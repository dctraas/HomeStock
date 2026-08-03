package com.dtraas.boodschapbeheer.ui.inventory

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.repository.ActivityLogRepository
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository
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

data class InventoryUiState(
    val searchQuery: String = "",
    val selectedCategory: Category? = null,
    val sortOption: InventorySortOption = InventorySortOption.NAME,
    val groupedInventory: Map<Category, List<InventoryItemWithProduct>> = emptyMap(),
    // Same items as groupedInventory, but as one globally ordered list rather than grouped
    // by category — grouping loses the overall order between categories (each category's
    // header still shows in category order, not by whichever item within it sorts first),
    // so sort options where that global order is the point (EXPIRATION) render from this
    // instead of groupedInventory. See InventoryScreen.
    val flatInventory: List<InventoryItemWithProduct> = emptyList(),
)

class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val activityLogRepository: ActivityLogRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<Category?>(null)
    private val sortOption = MutableStateFlow(InventorySortOption.NAME)

    val uiState: StateFlow<InventoryUiState> = combine(
        inventoryRepository.observeInventoryWithProduct(),
        searchQuery,
        selectedCategory,
        sortOption,
    ) { items, query, category, sort ->
        val filtered = items.filter { item ->
            val matchesCategory = category == null || Category.fromStorageKey(item.category) == category
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.brand?.contains(query, ignoreCase = true) == true
            matchesCategory && matchesQuery
        }
        val sorted = when (sort) {
            InventorySortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            InventorySortOption.QUANTITY -> filtered.sortedByDescending { it.quantity }
            InventorySortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.updatedAt }
            // Items with a set expiration date first (soonest first); items without one sink to the bottom.
            InventorySortOption.EXPIRATION -> filtered.sortedWith(compareBy(nullsLast()) { it.expirationDate })
        }
        InventoryUiState(
            searchQuery = query,
            selectedCategory = category,
            sortOption = sort,
            groupedInventory = sorted
                .groupBy { Category.fromStorageKey(it.category) }
                .toSortedMap(compareBy { it.sortOrder }),
            flatInventory = sorted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onCategoryFilterChange(category: Category?) {
        selectedCategory.value = category
    }

    fun onSortOptionChange(option: InventorySortOption) {
        sortOption.value = option
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
                item.barcode, item.quantity, item.expirationDate, item.minQuantity, item.note,
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
