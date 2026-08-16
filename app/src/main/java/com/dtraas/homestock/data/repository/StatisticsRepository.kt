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
import java.time.ZoneId
import java.time.ZoneOffset

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
                                )
                            }
                        }
                        .sortedByDescending { it.wastedCount }
                        .take(limit)
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
