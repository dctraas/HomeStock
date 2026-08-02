package com.dtraas.boodschapbeheer.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschapbeheer.data.local.entity.StoreEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.MeasurementUnit
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository
import com.dtraas.boodschapbeheer.data.repository.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val storeRepository: StoreRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    val searchQueryState: StateFlow<String> = searchQuery

    val stores: StateFlow<List<StoreEntity>> =
        storeRepository.observeStores().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Grouped and ordered by each store's sortOrder (custom store list), with any store
    // name no longer in that list falling after the known ones and "no store" always last.
    val groupedByStore: StateFlow<Map<String, List<ShoppingListItemEntity>>> =
        combine(
            shoppingListRepository.observeShoppingList(),
            searchQuery,
            storeRepository.observeStores(),
        ) { items, query, knownStores ->
            val sortOrderByName = knownStores.associate { it.name to it.sortOrder }
            items
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .groupBy { it.store }
                .toSortedMap(
                    compareBy(
                        { it.isBlank() },
                        { sortOrderByName[it] ?: Double.MAX_VALUE / 2 },
                        { it },
                    ),
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun addItem(
        name: String,
        category: Category,
        store: String,
        quantity: Int,
        note: String? = null,
        unit: MeasurementUnit = MeasurementUnit.STUKS,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            shoppingListRepository.addItem(name, category, store, quantity, note = note, unit = unit)
        }
    }

    fun addStore(name: String) {
        viewModelScope.launch { storeRepository.addStore(name) }
    }

    fun removeStore(id: String) {
        viewModelScope.launch { storeRepository.removeStore(id) }
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

    fun moveItem(item: ShoppingListItemEntity, previous: ShoppingListItemEntity?, next: ShoppingListItemEntity?) {
        viewModelScope.launch { shoppingListRepository.moveItem(item, previous, next) }
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

    fun checkAll() {
        viewModelScope.launch { shoppingListRepository.checkAll() }
    }
}
