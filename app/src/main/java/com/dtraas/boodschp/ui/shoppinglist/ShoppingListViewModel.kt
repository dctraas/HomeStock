package com.dtraas.boodschp.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.model.Store
import com.dtraas.boodschp.data.repository.ShoppingListRepository
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

    fun addItem(name: String, category: Category, store: Store, quantity: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { shoppingListRepository.addItem(name, category, store, quantity) }
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
