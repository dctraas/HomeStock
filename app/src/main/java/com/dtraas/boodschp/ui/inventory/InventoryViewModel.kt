package com.dtraas.boodschp.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschp.data.model.ActivityType
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.model.Store
import com.dtraas.boodschp.data.repository.ActivityLogRepository
import com.dtraas.boodschp.data.repository.InventoryRepository
import com.dtraas.boodschp.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InventoryUiState(
    val searchQuery: String = "",
    val selectedCategory: Category? = null,
    val groupedInventory: Map<Category, List<InventoryItemWithProduct>> = emptyMap(),
)

class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val activityLogRepository: ActivityLogRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<Category?>(null)

    val uiState: StateFlow<InventoryUiState> = combine(
        inventoryRepository.observeInventoryWithProduct(),
        searchQuery,
        selectedCategory,
    ) { items, query, category ->
        val filtered = items.filter { item ->
            val matchesCategory = category == null || Category.fromStorageKey(item.category) == category
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.brand?.contains(query, ignoreCase = true) == true
            matchesCategory && matchesQuery
        }
        InventoryUiState(
            searchQuery = query,
            selectedCategory = category,
            groupedInventory = filtered
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

    fun setQuantity(barcode: String, quantity: Int) {
        viewModelScope.launch { inventoryRepository.updateQuantity(barcode, quantity) }
    }

    fun removeFromInventory(barcode: String) {
        viewModelScope.launch { inventoryRepository.removeFromInventory(barcode) }
    }

    fun restoreItem(item: InventoryItemWithProduct) {
        viewModelScope.launch { inventoryRepository.restoreItem(item.barcode, item.quantity) }
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
            activityLogRepository.log(
                item.barcode,
                ActivityType.ADDED_TO_SHOPPING_LIST,
                "Toegevoegd aan boodschappenlijst",
            )
        }
    }
}
