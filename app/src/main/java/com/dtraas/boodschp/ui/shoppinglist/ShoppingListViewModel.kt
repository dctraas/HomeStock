package com.dtraas.boodschp.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.model.Store
import com.dtraas.boodschp.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
) : ViewModel() {

    val groupedByStore: StateFlow<Map<Store, List<ShoppingListItemEntity>>> =
        shoppingListRepository.observeShoppingList()
            .map { items ->
                items
                    .groupBy { Store.fromStorageKey(it.store) }
                    .toSortedMap(compareBy { it.sortOrder })
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addItem(name: String, category: Category, store: Store, quantity: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { shoppingListRepository.addItem(name, category, store, quantity) }
    }

    fun updateItem(item: ShoppingListItemEntity) {
        if (item.name.isBlank()) return
        viewModelScope.launch { shoppingListRepository.updateItem(item) }
    }

    fun setChecked(id: Long, checked: Boolean) {
        viewModelScope.launch { shoppingListRepository.setChecked(id, checked) }
    }

    fun removeItem(id: Long) {
        viewModelScope.launch { shoppingListRepository.removeItem(id) }
    }

    fun clearChecked() {
        viewModelScope.launch { shoppingListRepository.clearChecked() }
    }
}
