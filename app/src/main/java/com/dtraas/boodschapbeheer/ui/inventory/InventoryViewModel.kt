package com.dtraas.boodschapbeheer.ui.inventory

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.Store
import com.dtraas.boodschapbeheer.data.repository.ActivityLogRepository
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How items are ordered within each category group in the Voorraad overview. */
enum class InventorySortOption(@StringRes val labelRes: Int) {
    NAME(R.string.sort_option_name),
    QUANTITY(R.string.sort_option_quantity),
    RECENTLY_UPDATED(R.string.sort_option_recently_updated),
}

data class InventoryUiState(
    val searchQuery: String = "",
    val selectedCategory: Category? = null,
    val sortOption: InventorySortOption = InventorySortOption.NAME,
    val groupedInventory: Map<Category, List<InventoryItemWithProduct>> = emptyMap(),
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
        }
        InventoryUiState(
            searchQuery = query,
            selectedCategory = category,
            sortOption = sort,
            groupedInventory = sorted
                .groupBy { Category.fromStorageKey(it.category) }
                .toSortedMap(compareBy { it.sortOrder }),
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

    fun setQuantity(barcode: String, quantity: Int) {
        viewModelScope.launch { inventoryRepository.updateQuantity(barcode, quantity) }
    }

    fun removeFromInventory(barcode: String) {
        viewModelScope.launch { inventoryRepository.removeFromInventory(barcode) }
    }

    fun restoreItem(item: InventoryItemWithProduct) {
        viewModelScope.launch {
            inventoryRepository.restoreItem(item.barcode, item.quantity, item.expirationDate, item.minQuantity)
        }
    }

    fun addToShoppingList(item: InventoryItemWithProduct) {
        viewModelScope.launch {
            shoppingListRepository.addItem(
                name = item.name,
                category = Category.fromStorageKey(item.category),
                store = Store.GEEN,
                quantity = 1,
                barcode = item.barcode,
                imageUrl = item.imageUrl,
            )
            activityLogRepository.logAddedToShoppingList(item.barcode)
        }
    }
}
