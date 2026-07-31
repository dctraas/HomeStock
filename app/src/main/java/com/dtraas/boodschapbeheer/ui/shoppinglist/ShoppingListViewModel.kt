package com.dtraas.boodschapbeheer.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.Store
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    val searchQueryState: StateFlow<String> = searchQuery

    val groupedByStore: StateFlow<Map<Store, List<ShoppingListItemEntity>>> =
        combine(
            shoppingListRepository.observeShoppingList(),
            searchQuery,
        ) { items, query ->
            items
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .groupBy { Store.fromStorageKey(it.store) }
                .toSortedMap(compareBy { it.sortOrder })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun addItem(name: String, category: Category, store: Store, quantity: Int, note: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch { shoppingListRepository.addItem(name, category, store, quantity, note = note) }
    }

    fun updateItem(item: ShoppingListItemEntity) {
        if (item.name.isBlank()) return
        viewModelScope.launch { shoppingListRepository.updateItem(item) }
    }

    fun setChecked(id: String, checked: Boolean) {
        viewModelScope.launch { shoppingListRepository.setChecked(id, checked) }
    }

    fun setQuantity(id: String, quantity: Int) {
        viewModelScope.launch { shoppingListRepository.setQuantity(id, quantity) }
    }

    /** Swaps [item] with its neighbor at [otherIndex] within [itemsInStore] to reorder the list. */
    fun moveItem(itemsInStore: List<ShoppingListItemEntity>, index: Int, otherIndex: Int) {
        if (otherIndex !in itemsInStore.indices) return
        val a = itemsInStore[index]
        val b = itemsInStore[otherIndex]
        viewModelScope.launch { shoppingListRepository.swapSortOrder(a, b) }
    }

    fun removeItem(id: String) {
        viewModelScope.launch { shoppingListRepository.removeItem(id) }
    }

    fun restoreItem(item: ShoppingListItemEntity) {
        viewModelScope.launch { shoppingListRepository.restoreItem(item) }
    }

    fun clearChecked() {
        viewModelScope.launch { shoppingListRepository.clearChecked() }
    }
}
