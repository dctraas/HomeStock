package com.dtraas.homestock.ui.inventory

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.InventoryStockStatus
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
    // list together, shown as chips (see InventoryScreen) rather than folded into the filter
    // dropdown like favoritesOnly, since these two are meant to be glanceable/one-tap rather
    // than a couple of taps deep in a menu.
    val lowStockOnly: Boolean = false,
    val expiringSoonOnly: Boolean = false,
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
    val groupedByLocation: Map<String?, List<InventoryItemWithProduct>> = emptyMap(),
    // Every distinct location currently in use, for the filter menu — derived from the full,
    // unfiltered inventory (not `filtered`) so picking one location doesn't make the others
    // disappear from the list of choices.
    val availableLocations: List<String> = emptyList(),
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
    private val expiringSoonOnly = MutableStateFlow(false)
    private val groupBy = MutableStateFlow(InventoryGroupBy.CATEGORY)
    private val selectedLocation = MutableStateFlow<String?>(null)

    // combine() only has direct overloads up to 5 flows, and there are 9 inputs here — the
    // filter/sort controls are combined into one flow first (itself built from two nested
    // combines, for the same reason) so the outer combine stays within that limit.
    private data class QuickFilters(val lowStockOnly: Boolean, val expiringSoonOnly: Boolean)

    private data class Extras(
        val householdName: String?,
        val quickFilters: QuickFilters,
        val groupBy: InventoryGroupBy,
        val selectedLocation: String?,
    )

    private data class FilterState(
        val query: String,
        val category: Category?,
        val sort: InventorySortOption,
        val favoritesOnly: Boolean,
        val extras: Extras,
    )

    val uiState: StateFlow<InventoryUiState> = combine(
        inventoryRepository.observeInventoryWithProduct(),
        combine(
            searchQuery,
            selectedCategory,
            sortOption,
            favoritesOnly,
            combine(
                householdRepository.observeHouseholdName(),
                lowStockOnly,
                expiringSoonOnly,
                groupBy,
                selectedLocation,
            ) { householdName, lowStock, expiringSoon, groupByOption, location ->
                Extras(householdName, QuickFilters(lowStock, expiringSoon), groupByOption, location)
            },
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
            val matchesExpiringSoon = !extras.quickFilters.expiringSoonOnly ||
                InventoryStockStatus.isExpiringSoon(item.expirationDate)
            val matchesLocation = extras.selectedLocation == null ||
                item.location.normalizedLocation()?.equals(extras.selectedLocation, ignoreCase = true) == true
            matchesCategory && matchesQuery && matchesFavorite && matchesLowStock && matchesExpiringSoon && matchesLocation
        }
        val sorted = when (filters.sort) {
            InventorySortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            InventorySortOption.QUANTITY -> filtered.sortedByDescending { it.quantity }
            InventorySortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.updatedAt }
            // Items with a set expiration date first (soonest first); items without one sink to the bottom.
            InventorySortOption.EXPIRATION -> filtered.sortedWith(compareBy(nullsLast()) { it.expirationDate })
        }
        InventoryUiState(
            searchQuery = filters.query,
            selectedCategory = filters.category,
            sortOption = filters.sort,
            favoritesOnly = filters.favoritesOnly,
            lowStockOnly = extras.quickFilters.lowStockOnly,
            expiringSoonOnly = extras.quickFilters.expiringSoonOnly,
            groupedInventory = sorted
                .groupBy { Category.fromStorageKey(it.category) }
                .toSortedMap(compareBy { it.sortOrder }),
            flatInventory = sorted,
            householdName = extras.householdName,
            groupBy = extras.groupBy,
            selectedLocation = extras.selectedLocation,
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
                .sortedBy { it.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onLowStockFilterChange(enabled: Boolean) {
        lowStockOnly.value = enabled
    }

    fun onExpiringSoonFilterChange(enabled: Boolean) {
        expiringSoonOnly.value = enabled
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

    fun removeFromInventory(barcode: String) {
        viewModelScope.launch { inventoryRepository.removeFromInventory(barcode) }
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
