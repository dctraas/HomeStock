package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.dao.ActorScanCount
import com.dtraas.homestock.data.local.dao.CategoryCount
import com.dtraas.homestock.data.local.dao.TopScannedProduct
import com.dtraas.homestock.data.local.dao.TopWastedProduct
import com.dtraas.homestock.data.local.entity.InventoryItemEntity
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.local.entity.ScanHistoryEntity
import com.dtraas.homestock.data.model.ActivityType
import com.dtraas.homestock.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** One month's total approximate waste value — see [StatisticsRepository.observeMonthlyWasteValue]. */
data class MonthlyWaste(val month: YearMonth, val totalValue: Double)

/** One calendar year's total approximate waste value — the "Jaar" period on Inzicht &
 *  Verspilling's hero chart, see [StatisticsRepository.observeYearlyWasteValue]. */
data class YearlyWaste(val year: Int, val totalValue: Double)

/** How many items were logged as waste, and their approximate combined value, in some window —
 *  see [StatisticsRepository.observeWasteSince]. */
data class WasteSummary(val count: Int, val totalValue: Double)

/** How many activityLog entries (of any type — scans, removals, waste, ...) a household member
 *  is responsible for in some window; null [actorName] means no name was set. Distinct from
 *  [com.dtraas.homestock.data.local.dao.ActorScanCount], which only counts scans — see
 *  [StatisticsRepository.observeActivityShareThisMonth]. */
data class ActorActivityCount(val actorName: String?, val count: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    private fun collection(householdId: String, name: String) =
        firestore.collection("households").document(householdId).collection(name)

    private fun products(householdId: String) =
        collection(householdId, "products").observeSnapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { ProductEntity.fromDocument(it) }.associateBy { it.barcode } }

    private fun inventoryWithProducts(): Flow<List<Pair<InventoryItemEntity, ProductEntity>>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    collection(householdId, "inventory").observeSnapshots()
                        .map { snapshot -> snapshot.documents.mapNotNull { InventoryItemEntity.fromDocument(it) } },
                    products(householdId),
                ) { inventory, products ->
                    inventory.mapNotNull { item -> products[item.barcode]?.let { item to it } }
                }
            }
        }

    private fun scanHistory(): Flow<List<ScanHistoryEntity>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                collection(householdId, "scanHistory").observeSnapshots()
                    .map { snapshot -> snapshot.documents.mapNotNull { ScanHistoryEntity.fromDocument(it) } }
            }
        }

    fun observeInventoryCount(): Flow<Int> = inventoryWithProducts().map { it.size }

    fun observeFavoritesCount(): Flow<Int> = inventoryWithProducts().map { items -> items.count { (item, _) -> item.isFavorite } }

    /** Items whose expiration date has already passed, or falls within [withinDays] from now. */
    fun observeExpiringSoonCount(withinDays: Long = 3): Flow<Int> =
        inventoryWithProducts().map { items ->
            val cutoff = LocalDate.now().plusDays(withinDays).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            items.count { (item, _) -> item.expirationDate != null && item.expirationDate <= cutoff }
        }

    /** Items at or below the minimum quantity threshold the household set for them. */
    fun observeLowStockCount(): Flow<Int> =
        inventoryWithProducts().map { items ->
            items.count { (item, _) -> item.minQuantity != null && item.quantity <= item.minQuantity }
        }

    /**
     * How many [type] entries were logged since [sinceMillis] — the same generic activity
     * count backing "scans in range" above, reused for e.g. "removed in range" or "added to
     * shopping list in range" to show more than just scan activity for the selected period.
     */
    fun observeActivityCountByType(type: ActivityType, sinceMillis: Long): Flow<Int> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(0)
            } else {
                collection(householdId, "activityLog").observeSnapshots().map { snapshot ->
                    snapshot.documents.count {
                        it.getString("type") == type.storageKey && (it.getLong("timestamp") ?: 0L) >= sinceMillis
                    }
                }
            }
        }

    /** Which day of the week sees the most scans, all-time — null once there's no scan history yet. */
    fun observeBusiestWeekday(): Flow<DayOfWeek?> =
        scanHistory().map { history ->
            history
                .groupingBy { Instant.ofEpochMilli(it.scannedAt).atZone(ZoneId.systemDefault()).dayOfWeek }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        }

    fun observeCategoryDistribution(): Flow<List<CategoryCount>> =
        inventoryWithProducts().map { items ->
            items.groupingBy { (_, product) -> product.category }
                .eachCount()
                .map { (category, count) -> CategoryCount(category, count) }
        }

    fun observeTopScannedProducts(limit: Int = 5): Flow<List<TopScannedProduct>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    collection(householdId, "scanHistory").observeSnapshots()
                        .map { snapshot -> snapshot.documents.mapNotNull { ScanHistoryEntity.fromDocument(it) } },
                    products(householdId),
                ) { history, products ->
                    history
                        .groupingBy { it.barcode }
                        .eachCount()
                        .mapNotNull { (barcode, count) ->
                            products[barcode]?.let { product ->
                                TopScannedProduct(
                                    barcode = barcode,
                                    name = product.name,
                                    category = product.category,
                                    imageUrl = product.imageUrl,
                                    scanCount = count,
                                )
                            }
                        }
                        .sortedByDescending { it.scanCount }
                        .take(limit)
                }
            }
        }

    /** Products most often removed as food waste (rather than used up), all-time. */
    fun observeTopWastedProducts(limit: Int = 5): Flow<List<TopWastedProduct>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    collection(householdId, "activityLog").observeSnapshots(),
                    products(householdId),
                ) { snapshot, products ->
                    snapshot.documents
                        .filter { it.getString("type") == ActivityType.WASTED.storageKey }
                        .mapNotNull { it.getString("barcode") }
                        .groupingBy { it }
                        .eachCount()
                        .mapNotNull { (barcode, count) ->
                            products[barcode]?.let { product ->
                                TopWastedProduct(
                                    barcode = barcode,
                                    name = product.name,
                                    category = product.category,
                                    imageUrl = product.imageUrl,
                                    wastedCount = count,
                                    wastedValue = count * (product.lastPrice ?: 0.0),
                                )
                            }
                        }
                        .sortedByDescending { it.wastedCount }
                        .take(limit)
                }
            }
        }

    /**
     * Total waste value ([TopWastedProduct.wastedValue]'s same count × current-price
     * approximation, summed per month) for [monthsBack] months up to and including the current
     * one, oldest first — feeds the hero metric's month-over-month delta and its bar chart. A
     * month with zero "wasted" activity entries (or none priced) still gets an entry at €0
     * rather than being missing, so the chart always has exactly [monthsBack] bars.
     */
    fun observeMonthlyWasteValue(monthsBack: Int = 6): Flow<List<MonthlyWaste>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    collection(householdId, "activityLog").observeSnapshots(),
                    products(householdId),
                ) { snapshot, products ->
                    val currentMonth = YearMonth.now()
                    val months = (monthsBack - 1 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
                    val totals = months.associateWith { 0.0 }.toMutableMap()
                    snapshot.documents
                        .filter { it.getString("type") == ActivityType.WASTED.storageKey }
                        .forEach { doc ->
                            val timestamp = doc.getLong("timestamp") ?: return@forEach
                            val month = YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
                            if (month !in totals) return@forEach
                            val barcode = doc.getString("barcode") ?: return@forEach
                            val price = products[barcode]?.lastPrice ?: 0.0
                            totals[month] = (totals[month] ?: 0.0) + price
                        }
                    months.map { MonthlyWaste(it, totals[it] ?: 0.0) }
                }
            }
        }

    /**
     * Same idea as [observeMonthlyWasteValue] but bucketed by calendar year rather than month,
     * for Inzicht & Verspilling's "Jaar" period toggle — [yearsBack] years up to and including
     * the current one, oldest first, always exactly [yearsBack] entries.
     */
    fun observeYearlyWasteValue(yearsBack: Int = 6): Flow<List<YearlyWaste>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    collection(householdId, "activityLog").observeSnapshots(),
                    products(householdId),
                ) { snapshot, products ->
                    val currentYear = YearMonth.now().year
                    val years = (yearsBack - 1 downTo 0).map { currentYear - it }
                    val totals = years.associateWith { 0.0 }.toMutableMap()
                    snapshot.documents
                        .filter { it.getString("type") == ActivityType.WASTED.storageKey }
                        .forEach { doc ->
                            val timestamp = doc.getLong("timestamp") ?: return@forEach
                            val year = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).year
                            if (year !in totals) return@forEach
                            val barcode = doc.getString("barcode") ?: return@forEach
                            val price = products[barcode]?.lastPrice ?: 0.0
                            totals[year] = (totals[year] ?: 0.0) + price
                        }
                    years.map { YearlyWaste(it, totals[it] ?: 0.0) }
                }
            }
        }

    /** How many distinct products have at least one "wasted" entry so far this calendar month —
     *  Inzicht & Verspilling's hero subtitle ("9 producten"). */
    fun observeWasteProductCountThisMonth(): Flow<Int> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(0)
            } else {
                collection(householdId, "activityLog").observeSnapshots().map { snapshot ->
                    val monthStart = YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    snapshot.documents
                        .filter { it.getString("type") == ActivityType.WASTED.storageKey && (it.getLong("timestamp") ?: 0L) >= monthStart }
                        .mapNotNull { it.getString("barcode") }
                        .distinct()
                        .size
                }
            }
        }

    /**
     * Approximate value of everything logged as [ActivityType.REMOVED] (used up, not thrown
     * away) so far this month — Inzicht & Verspilling's "Bespaard" tile: food that made it to
     * the plate instead of the bin, priced the same way [observeMonthlyWasteValue] prices waste.
     */
    fun observeSavedValueThisMonth(): Flow<Double> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(0.0)
            } else {
                combine(
                    collection(householdId, "activityLog").observeSnapshots(),
                    products(householdId),
                ) { snapshot, products ->
                    val monthStart = YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    snapshot.documents
                        .filter { it.getString("type") == ActivityType.REMOVED.storageKey && (it.getLong("timestamp") ?: 0L) >= monthStart }
                        .sumOf { doc ->
                            val barcode = doc.getString("barcode") ?: return@sumOf 0.0
                            products[barcode]?.lastPrice ?: 0.0
                        }
                }
            }
        }

    /** Share of this month's household activity (every logged type, not just scans) per member —
     *  Inzicht & Verspilling's "Dennis 62% · Marieke 38%" row. */
    fun observeActivityShareThisMonth(): Flow<List<ActorActivityCount>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                collection(householdId, "activityLog").observeSnapshots().map { snapshot ->
                    val monthStart = YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    snapshot.documents
                        .filter { (it.getLong("timestamp") ?: 0L) >= monthStart }
                        .groupingBy { it.getString("actorName") }
                        .eachCount()
                        .map { (actorName, count) -> ActorActivityCount(actorName, count) }
                        .sortedByDescending { it.count }
                }
            }
        }

    /**
     * Waste logged since [sinceMillis] — used by [com.dtraas.homestock.work.WasteSummaryWorker]'s
     * periodic "voedselverspilling"-melding, a narrower rolling window than
     * [observeMonthlyWasteValue]'s fixed calendar-month buckets.
     */
    fun observeWasteSince(sinceMillis: Long): Flow<WasteSummary> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(WasteSummary(0, 0.0))
            } else {
                combine(
                    collection(householdId, "activityLog").observeSnapshots(),
                    products(householdId),
                ) { snapshot, products ->
                    val wastedSince = snapshot.documents.filter {
                        it.getString("type") == ActivityType.WASTED.storageKey && (it.getLong("timestamp") ?: 0L) >= sinceMillis
                    }
                    val totalValue = wastedSince.sumOf { doc ->
                        val barcode = doc.getString("barcode") ?: return@sumOf 0.0
                        products[barcode]?.lastPrice ?: 0.0
                    }
                    WasteSummary(count = wastedSince.size, totalValue = totalValue)
                }
            }
        }

    /**
     * Scan counts grouped by who performed them. Reads `activityLog` rather than
     * `scanHistory` — [ActivityLogRepository] is the only place that stamps a scan
     * with the acting device's name, `scanHistory` entries don't carry one.
     */
    fun observeScansByActor(): Flow<List<ActorScanCount>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                collection(householdId, "activityLog").observeSnapshots().map { snapshot ->
                    snapshot.documents
                        .filter { it.getString("type") == ActivityType.SCANNED.storageKey }
                        .groupingBy { it.getString("actorName") }
                        .eachCount()
                        .map { (actorName, count) -> ActorScanCount(actorName, count) }
                        .sortedByDescending { it.scanCount }
                }
            }
        }
}
