package com.dtraas.boodschp.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
) : ViewModel() {

    val shoppingList: StateFlow<List<ShoppingListItemEntity>> =
        shoppingListRepository.observeShoppingList()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(name: String, category: Category, quantity: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { shoppingListRepository.addItem(name, category, quantity) }
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
