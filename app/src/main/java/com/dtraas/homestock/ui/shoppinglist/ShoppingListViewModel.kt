package com.dtraas.homestock.ui.shoppinglist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.ShoppingListItemEntity
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.data.repository.ShoppingListRepository
import com.dtraas.homestock.data.repository.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How items are ordered within each store's group. */
enum class ShoppingListSortMode(@StringRes val labelRes: Int) {
    /** The household's own drag-to-reorder order (see moveItem/ReorderableShoppingList). */
    MANUAL(R.string.shopping_list_sort_manual),
    /** Supermarket-aisle order (see Category.sortOrder) — same order Voorraad's default
     *  category grouping uses, so items line up the way they're laid out in a typical store. */
    AISLE(R.string.shopping_list_sort_aisle),
}

class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val storeRepository: StoreRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    val searchQueryState: StateFlow<String> = searchQuery

    private val sortMode = MutableStateFlow(ShoppingListSortMode.MANUAL)
    val sortModeState: StateFlow<ShoppingListSortMode> = sortMode

    val stores: StateFlow<List<StoreEntity>> =
        storeRepository.observeStores().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Grouped and ordered by each store's sortOrder (custom store list), with any store
    // name no longer in that list falling after the known ones and "no store" always last.
    val groupedByStore: StateFlow<Map<String, List<ShoppingListItemEntity>>> =
        combine(
            shoppingListRepository.observeShoppingList(),
            searchQuery,
            storeRepository.observeStores(),
            sortMode,
        ) { items, query, knownStores, mode ->
            val sortOrderByName = knownStores.associate { it.name to it.sortOrder }
            val grouped = items
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .groupBy { it.store }
            val ordered = if (mode == ShoppingListSortMode.AISLE) {
                // isChecked stays the primary key even here — an already-checked item
                // shouldn't jump back among the unchecked ones just because its category
                // happens to sort earlier than theirs.
                grouped.mapValues { (_, itemsInStore) ->
                    itemsInStore.sortedWith(
                        compareBy(
                            { it.isChecked },
                            { Category.fromStorageKey(it.category).sortOrder },
                            { it.name.lowercase() },
                        ),
                    )
                }
            } else {
                grouped
            }
            ordered.toSortedMap(
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

    fun onSortModeChange(mode: ShoppingListSortMode) {
        sortMode.value = mode
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

    fun setStore(id: String, store: String) {
        viewModelScope.launch { shoppingListRepository.setStore(id, store) }
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
