package com.dtraas.homestock.ui.inventory

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.InventoryStockStatus
import com.dtraas.homestock.data.model.Location
import com.dtraas.homestock.data.repository.ActivityLogRepository
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ShoppingListRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How items are ordered within each category group in the Voorraad overview. */
enum class InventorySortOption(@StringRes val labelRes: Int) {
    NAME(R.string.sort_option_name),
    QUANTITY(R.string.sort_option_quantity),
    RECENTLY_UPDATED(R.string.sort_option_recently_updated),
    EXPIRATION(R.string.sort_option_expiration),
}

/** Which field the Voorraad overview's section headers group by. */
enum class InventoryGroupBy(@StringRes val labelRes: Int) {
    CATEGORY(R.string.group_by_category),
    LOCATION(R.string.group_by_location),
}

data class InventoryUiState(
    val searchQuery: String = "",
    val selectedCategory: Category? = null,
    val sortOption: InventorySortOption = InventorySortOption.NAME,
    val favoritesOnly: Boolean = false,
    // Independent of each other and of favoritesOnly/selectedCategory — all narrow the same
    // list together, shown as chips/cards (see InventoryFilterSheet) rather than folded into a
    // menu, since these are meant to be glanceable/one-tap.
    val lowStockOnly: Boolean = false,
    // Split from what used to be one combined "expiringSoonOnly" (soon-or-already-past) into two
    // non-overlapping quick filters, matching the filter sheet's "Verlopen"/"Bijna over datum"
    // cards — see InventoryStockStatus.isExpired's doc for why. Already-expired items no longer
    // belong under "bijna over datum" at all (an expired item isn't "soon" to expire, it already
    // has) — the "Eerst opmaken" hint card and its "Alles →" only ever deal in
    // expiringSoonNotExpiredOnly now; expiredOnly is its own separate quick filter, surfaced only
    // via the filter sheet's "Verlopen" chip.
    val expiredOnly: Boolean = false,
    val expiringSoonNotExpiredOnly: Boolean = false,
    val noExpirationDateOnly: Boolean = false,
    val groupedInventory: Map<Category, List<InventoryItemWithProduct>> = emptyMap(),
    // Same items as groupedInventory, but as one globally ordered list rather than grouped
    // by category — grouping loses the overall order between categories (each category's
    // header still shows in category order, not by whichever item within it sorts first),
    // so sort options where that global order is the point (EXPIRATION) render from this
    // instead of groupedInventory. See InventoryScreen.
    val flatInventory: List<InventoryItemWithProduct> = emptyList(),
    // Shown as the top-bar title in place of the generic "Voorraad" label; null for
    // households created before this field existed, or while it's still loading.
    val householdName: String? = null,
    val groupBy: InventoryGroupBy = InventoryGroupBy.CATEGORY,
    // Independent of groupBy — filtering to one location works the same whether headers are
    // currently grouped by category or by location. Null key holds items with no location set.
    val selectedLocation: String? = null,
    // Distinct from selectedLocation == null ("no location filter applied, show everything") —
    // this means "show only items with no location set", the filter sheet's "Zonder" chip.
    // Mutually exclusive with selectedLocation in the UI (see InventoryViewModel.onLocationFilterChange/
    // onNoLocationFilterChange): picking one clears the other.
    val noLocationOnly: Boolean = false,
    val groupedByLocation: Map<String?, List<InventoryItemWithProduct>> = emptyMap(),
    // Every distinct location currently in use, for the filter sheet — derived from the full,
    // unfiltered inventory (not `filtered`) so picking one location doesn't make the others
    // disappear from the list of choices. Ordered by the fixed Location enum first (Koelkast/
    // Vriezer/Voorraadkast/Kelder, in that declared order), then any custom/free-text location
    // string a household typed that doesn't match one of those, alphabetically — matches the
    // order a household would expect their most common storage spots to appear in.
    val availableLocations: List<String> = emptyList(),
    // "Keuken" header stats — derived from the *unfiltered* household inventory (not
    // `filtered`/`flatInventory` above), same reasoning as availableLocations: this gives an
    // at-a-glance picture of the whole voorraad, which shouldn't shrink just because a search/
    // category filter happens to be active. Same reasoning extends to every other *Count/*Counts
    // field below — all real, all off the unfiltered list, never estimated.
    val totalCount: Int = 0,
    val lowStockCount: Int = 0,
    // Expiring soon but NOT already expired — the header stat line. Equal to
    // expiringSoonNotExpiredCount below (both mean the same thing now); kept as a separate field
    // because it serves a different piece of UI (the "Keuken" stats row vs. the filter sheet's
    // "Bijna over datum" chip label).
    val expiringSoonCount: Int = 0,
    val expiredCount: Int = 0,
    val expiringSoonNotExpiredCount: Int = 0,
    val noExpirationDateCount: Int = 0,
    val favoritesCount: Int = 0,
    val noLocationCount: Int = 0,
    // The filter sheet's CATEGORIE/LOCATIE chip labels ("Zuivel · 9", "Koelkast · 34").
    val categoryCounts: Map<Category, Int> = emptyMap(),
    val locationCounts: Map<String, Int> = emptyMap(),
    // The 3 soonest-expiring items that aren't already expired, unfiltered, soonest first — the
    // "Eerst opmaken" header card's chips. A subset of what expiringSoonCount counts, not
    // everything it counts — the card only ever has room for a handful, "Alles" is where the
    // rest shows up. Already-expired items never appear here — see expiredOnly's doc.
    val expiringSoonItems: List<InventoryItemWithProduct> = emptyList(),
)

class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val householdRepository: HouseholdRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<Category?>(null)
    private val sortOption = MutableStateFlow(InventorySortOption.NAME)
    private val favoritesOnly = MutableStateFlow(false)
    private val lowStockOnly = MutableStateFlow(false)
    // See InventoryUiState.expiredOnly/expiringSoonNotExpiredOnly's doc — these replace what
    // used to be one combined "expiringSoonOnly" flag.
    private val expiredOnly = MutableStateFlow(false)
    private val expiringSoonNotExpiredOnly = MutableStateFlow(false)
    private val noExpirationDateOnly = MutableStateFlow(false)
    private val groupBy = MutableStateFlow(InventoryGroupBy.CATEGORY)
    private val selectedLocation = MutableStateFlow<String?>(null)
    private val noLocationOnly = MutableStateFlow(false)

    // combine() only has direct overloads up to 5 flows, and there are considerably more than
    // that many inputs here — the filter/sort controls are combined into one flow first (itself
    // built from several nested combines, for the same reason) so the outer combine stays within
    // that limit.
    private data class QuickFilters(
        val lowStockOnly: Boolean,
        val expiredOnly: Boolean,
        val expiringSoonNotExpiredOnly: Boolean,
        val noExpirationDateOnly: Boolean,
    )

    private data class LocationFilters(
        val groupBy: InventoryGroupBy,
        val selectedLocation: String?,
        val noLocationOnly: Boolean,
    )

    private data class Extras(
        val householdName: String?,
        val quickFilters: QuickFilters,
        val locationFilters: LocationFilters,
    )

    private data class FilterState(
        val query: String,
        val category: Category?,
        val sort: InventorySortOption,
        val favoritesOnly: Boolean,
        val extras: Extras,
    )

    private val quickFilters = combine(
        lowStockOnly, expiredOnly, expiringSoonNotExpiredOnly, noExpirationDateOnly,
    ) { lowStock, expired, expiringSoonNotExpired, noExpirationDate ->
        QuickFilters(lowStock, expired, expiringSoonNotExpired, noExpirationDate)
    }

    private val locationFilters = combine(
        groupBy, selectedLocation, noLocationOnly,
    ) { groupByOption, location, noLocation -> LocationFilters(groupByOption, location, noLocation) }

    val uiState: StateFlow<InventoryUiState> = combine(
        inventoryRepository.observeInventoryWithProduct(),
        combine(
            searchQuery,
            selectedCategory,
            sortOption,
            favoritesOnly,
            combine(
                householdRepository.observeHouseholdName(),
                quickFilters,
                locationFilters,
            ) { householdName, qf, lf -> Extras(householdName, qf, lf) },
        ) { query, category, sort, favOnly, extras ->
            FilterState(query, category, sort, favOnly, extras)
        },
    ) { items, filters ->
        val extras = filters.extras
        fun String?.normalizedLocation(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
        val filtered = items.filter { item ->
            val matchesCategory = filters.category == null || Category.fromStorageKey(item.category) == filters.category
            val matchesQuery = filters.query.isBlank() ||
                item.name.contains(filters.query, ignoreCase = true) ||
                item.brand?.contains(filters.query, ignoreCase = true) == true
            val matchesFavorite = !filters.favoritesOnly || item.isFavorite
            // Each independently checked against the item's raw fields (not the single,
            // priority-ordered InventoryStockStatus.of() the status dot uses) — an item that's
            // both below its minimum *and* expiring soon should still show up under either
            // chip, not just whichever one of the two of() would have picked.
            val matchesLowStock = !extras.quickFilters.lowStockOnly ||
                InventoryStockStatus.isLowStock(item.quantity, item.minQuantity)
            val matchesExpired = !extras.quickFilters.expiredOnly || InventoryStockStatus.isExpired(item.expirationDate)
            val matchesExpiringSoonNotExpired = !extras.quickFilters.expiringSoonNotExpiredOnly ||
                (InventoryStockStatus.isExpiringSoon(item.expirationDate) && !InventoryStockStatus.isExpired(item.expirationDate))
            val matchesNoExpirationDate = !extras.quickFilters.noExpirationDateOnly || item.expirationDate == null
            val matchesLocation = when {
                extras.locationFilters.noLocationOnly -> item.location.normalizedLocation() == null
                extras.locationFilters.selectedLocation != null ->
                    item.location.normalizedLocation()?.equals(extras.locationFilters.selectedLocation, ignoreCase = true) == true
                else -> true
            }
            matchesCategory && matchesQuery && matchesFavorite && matchesLowStock &&
                matchesExpired && matchesExpiringSoonNotExpired && matchesNoExpirationDate && matchesLocation
        }
        val sorted = when (filters.sort) {
            InventorySortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            InventorySortOption.QUANTITY -> filtered.sortedByDescending { it.quantity }
            InventorySortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.updatedAt }
            // Items with a set expiration date first (soonest first); items without one sink to the bottom.
            InventorySortOption.EXPIRATION -> filtered.sortedWith(compareBy(nullsLast()) { it.expirationDate })
        }
        // Expiring-soon-but-not-yet-expired: shared by the header stat, the "Eerst opmaken" hint
        // card, and the filter sheet's "Bijna over datum" chip — all three now mean exactly this
        // and nothing broader, so this is computed once instead of three times.
        val expiringSoonNotExpired = items.filter {
            InventoryStockStatus.isExpiringSoon(it.expirationDate) && !InventoryStockStatus.isExpired(it.expirationDate)
        }
        InventoryUiState(
            searchQuery = filters.query,
            selectedCategory = filters.category,
            sortOption = filters.sort,
            favoritesOnly = filters.favoritesOnly,
            lowStockOnly = extras.quickFilters.lowStockOnly,
            expiredOnly = extras.quickFilters.expiredOnly,
            expiringSoonNotExpiredOnly = extras.quickFilters.expiringSoonNotExpiredOnly,
            noExpirationDateOnly = extras.quickFilters.noExpirationDateOnly,
            groupedInventory = sorted
                .groupBy { Category.fromStorageKey(it.category) }
                .toSortedMap(compareBy { it.sortOrder }),
            flatInventory = sorted,
            householdName = extras.householdName,
            groupBy = extras.locationFilters.groupBy,
            selectedLocation = extras.locationFilters.selectedLocation,
            noLocationOnly = extras.locationFilters.noLocationOnly,
            // Null-location bucket sorts last, everything else alphabetically — a
            // List<Pair>.toMap() preserves that order (LinkedHashMap), unlike a raw groupBy().
            groupedByLocation = sorted
                .groupBy { it.location.normalizedLocation() }
                .toList()
                .sortedWith(compareBy({ it.first == null }, { it.first?.lowercase() ?: "" }))
                .toMap(),
            availableLocations = items
                .mapNotNull { it.location.normalizedLocation() }
                .distinct()
                .sortedWith(compareBy({ Location.fromStorageKey(it)?.ordinal ?: Int.MAX_VALUE }, { it.lowercase() })),
            totalCount = items.size,
            lowStockCount = items.count { InventoryStockStatus.isLowStock(it.quantity, it.minQuantity) },
            expiringSoonCount = expiringSoonNotExpired.size,
            expiredCount = items.count { InventoryStockStatus.isExpired(it.expirationDate) },
            expiringSoonNotExpiredCount = expiringSoonNotExpired.size,
            noExpirationDateCount = items.count { it.expirationDate == null },
            favoritesCount = items.count { it.isFavorite },
            noLocationCount = items.count { it.location.normalizedLocation() == null },
            categoryCounts = items.groupingBy { Category.fromStorageKey(it.category) }.eachCount(),
            locationCounts = items.mapNotNull { it.location.normalizedLocation() }.groupingBy { it }.eachCount(),
            expiringSoonItems = expiringSoonNotExpired
                .sortedBy { it.expirationDate }
                .take(3),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onLowStockFilterChange(enabled: Boolean) {
        lowStockOnly.value = enabled
    }

    fun onExpiredFilterChange(enabled: Boolean) {
        expiredOnly.value = enabled
    }

    fun onExpiringSoonNotExpiredFilterChange(enabled: Boolean) {
        expiringSoonNotExpiredOnly.value = enabled
    }

    fun onNoExpirationDateFilterChange(enabled: Boolean) {
        noExpirationDateOnly.value = enabled
    }

    /** "Alles →" on the "Eerst opmaken" hint card. The card itself no longer shows already-
     *  expired items (see expiredOnly's doc), so jumping from it only ever means "show me
     *  everything that card counts" — just expiringSoonNotExpiredOnly. An already-expired item
     *  would be a non-sequitur in that view. */
    fun showExpiringSoonNotExpiredOnly() {
        expiringSoonNotExpiredOnly.value = true
    }

    /** The "bijna over datum" push notification's deep link ([ExpiryCheckWorker] groups both
     *  soon-to-expire AND already-expired items into that one notification, under separate
     *  "Vandaag"/"Verlopen"-style day headers) — unlike the hint card above, tapping it should
     *  land on everything the notification just listed, so this still sets both flags. */
    fun showExpiringOrExpiredOnly() {
        expiredOnly.value = true
        expiringSoonNotExpiredOnly.value = true
    }

    fun onCategoryFilterChange(category: Category?) {
        selectedCategory.value = category
    }

    fun onSortOptionChange(option: InventorySortOption) {
        sortOption.value = option
    }

    fun onFavoritesFilterChange(favoritesOnly: Boolean) {
        this.favoritesOnly.value = favoritesOnly
    }

    fun onGroupByChange(option: InventoryGroupBy) {
        groupBy.value = option
    }

    fun onLocationFilterChange(location: String?) {
        selectedLocation.value = location
        // Mutually exclusive with noLocationOnly in the UI (the filter sheet's "Zonder" chip) —
        // picking a named location clears it.
        if (location != null) noLocationOnly.value = false
    }

    /** The filter sheet's "Zonder" location chip — items with no location set at all. */
    fun onNoLocationFilterChange(enabled: Boolean) {
        noLocationOnly.value = enabled
        if (enabled) selectedLocation.value = null
    }

    /** "Alles wissen" in the filter sheet — resets every filter dimension it controls. Leaves
     *  [searchQuery] (not a sheet control) and [sortOption]/[groupBy] (display preferences, not
     *  filters — see the sheet's own "ZO TOON JE HET" section) untouched. */
    fun clearAllFilters() {
        selectedCategory.value = null
        favoritesOnly.value = false
        lowStockOnly.value = false
        expiredOnly.value = false
        expiringSoonNotExpiredOnly.value = false
        noExpirationDateOnly.value = false
        selectedLocation.value = null
        noLocationOnly.value = false
    }

    fun toggleFavorite(item: InventoryItemWithProduct) {
        viewModelScope.launch { inventoryRepository.setFavorite(item.barcode, !item.isFavorite) }
    }

    private val _restockEvents = Channel<String>(Channel.BUFFERED)

    /** Emits a product name whenever a quantity change here triggers an auto-restock. */
    val restockEvents: Flow<String> = _restockEvents.receiveAsFlow()

    fun setQuantity(barcode: String, quantity: Int) {
        viewModelScope.launch {
            val restockedProductName = inventoryRepository.updateQuantity(barcode, quantity)
            if (restockedProductName != null) _restockEvents.send(restockedProductName)
        }
    }

    fun removeFromInventory(barcode: String, wasted: Boolean = false) {
        viewModelScope.launch { inventoryRepository.removeFromInventory(barcode, wasted) }
    }

    /** See [InventoryRepository.removeQuantityFromInventory] — used instead of
     *  [removeFromInventory] whenever the item's own quantity is more than 1 and the household
     *  said how many were actually used up/wasted (the swipe-to-delete/Opgebruikt-of-weggegooid
     *  flow), rather than assuming it was all of them. */
    fun removeQuantityFromInventory(barcode: String, amount: Int, wasted: Boolean) {
        viewModelScope.launch { inventoryRepository.removeQuantityFromInventory(barcode, amount, wasted) }
    }

    fun restoreItem(item: InventoryItemWithProduct) {
        viewModelScope.launch {
            inventoryRepository.restoreItem(
                item.barcode, item.quantity, item.expirationDate, item.minQuantity, item.note, item.isFavorite,
            )
        }
    }

    fun addToShoppingList(item: InventoryItemWithProduct) {
        viewModelScope.launch {
            shoppingListRepository.addItem(
                name = item.name,
                category = Category.fromStorageKey(item.category),
                store = "",
                quantity = 1,
                barcode = item.barcode,
                imageUrl = item.imageUrl,
            )
            activityLogRepository.logAddedToShoppingList(item.barcode)
        }
    }
}
