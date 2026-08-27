package com.dtraas.homestock.ui.shoppinglist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.ShoppingListItemEntity
import com.dtraas.homestock.data.local.entity.ShoppingListMeta
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.InventoryStockStatus
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.data.repository.ActivityLogRepository
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ShoppingListRepository
import com.dtraas.homestock.data.repository.ShoppingListsRepository
import com.dtraas.homestock.data.repository.StoreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One low-stock/out-of-stock inventory item offered as a suggestion chip on the shopping
 *  list's bottom bar — see [ShoppingListViewModel.lowStockSuggestions]. */
data class LowStockSuggestion(val barcode: String, val name: String, val category: Category)

/** [store]'s own gangvolgorde (see [StoreEntity.aislePaths]) turned into a rank lookup covering
 *  every [Category] — every category within the same path shares that path's rank, so they sort
 *  together as one aisle. Null [store] (a "no store" bucket, which has no [StoreEntity] to look
 *  up at all) falls back to Category's own fixed [Category.sortOrder], same as an uncustomized
 *  store's own [StoreEntity.aislePaths] already does. Shared by
 *  [ShoppingListViewModel.groupedByStore]'s AISLE sort mode and, since it's not file-private,
 *  ShoppingModeScreen's own aisle-number display (same package). */
fun categoryRankFor(store: StoreEntity?): Map<Category, Int> {
    val paths = store?.aislePaths() ?: Category.entries.sortedBy { it.sortOrder }.map { listOf(it) }
    return paths.withIndex().flatMap { (rank, path) -> path.map { category -> category to rank } }.toMap()
}

/** How items are ordered within each store's group. */
enum class ShoppingListSortMode(@StringRes val labelRes: Int) {
    /** The household's own drag-to-reorder order (see moveItem/ReorderableShoppingList). */
    MANUAL(R.string.shopping_list_sort_manual),
    /** Supermarket-aisle order (see Category.sortOrder) — same order Voorraad's default
     *  category grouping uses, so items line up the way they're laid out in a typical store. */
    AISLE(R.string.shopping_list_sort_aisle),
}

/**
 * [defaultListName] is resolved once from a string resource by the caller (ShoppingListScreen) —
 * a ViewModel can't call `stringResource` itself — and used as the synthesized default list's
 * display name (see [ShoppingListMeta]'s doc for why the default list is never a real document).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val storeRepository: StoreRepository,
    private val shoppingListsRepository: ShoppingListsRepository,
    activityLogRepository: ActivityLogRepository,
    inventoryRepository: InventoryRepository,
    defaultListName: String,
) : ViewModel() {

    private val sortMode = MutableStateFlow(ShoppingListSortMode.MANUAL)
    val sortModeState: StateFlow<ShoppingListSortMode> = sortMode

    val stores: StateFlow<List<StoreEntity>> =
        storeRepository.observeStores().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val defaultListMeta = ShoppingListMeta(id = null, name = defaultListName, sortOrder = -1.0)

    /** The default list first, always — even in a brand-new household with no custom lists
     *  yet — followed by every named list the household created (see [ShoppingListsRepository]). */
    val lists: StateFlow<List<ShoppingListMeta>> =
        shoppingListsRepository.observeLists()
            .map { custom -> listOf(defaultListMeta) + custom }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(defaultListMeta))

    // In-memory only (not persisted) — resets to the default list on a cold start, same as most
    // list apps opening back to their primary list. Deliberately device-local: which list a
    // household member happens to be looking at right now isn't shared state the way the lists
    // and their items are.
    private val activeListId = MutableStateFlow<String?>(null)

    val activeList: StateFlow<ShoppingListMeta> =
        combine(lists, activeListId) { allLists, activeId ->
            allLists.firstOrNull { it.id == activeId } ?: defaultListMeta
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultListMeta)

    /** Every list's own item count (checked + unchecked), keyed by [ShoppingListMeta.id] — null
     *  is the default list's key, same convention [ShoppingListItemEntity.listId] already uses.
     *  Lets the list-switcher sheet show "3 items" per list instead of just names. */
    val itemCountByListId: StateFlow<Map<String?, Int>> =
        shoppingListRepository.observeShoppingList()
            .map { items -> items.groupingBy { it.listId }.eachCount() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Grouped and ordered by each store's sortOrder (custom store list), with any store
    // name no longer in that list falling after the known ones and "no store" always last.
    val groupedByStore: StateFlow<Map<String, List<ShoppingListItemEntity>>> =
        combine(
            activeListId.flatMapLatest { listId -> shoppingListRepository.observeItemsForList(listId) },
            storeRepository.observeStores(),
            sortMode,
        ) { items, knownStores, mode ->
            val sortOrderByName = knownStores.associate { it.name to it.sortOrder }
            val grouped = items.groupBy { it.store }
            val ordered = if (mode == ShoppingListSortMode.AISLE) {
                val storeByName = knownStores.associateBy { it.name }
                // isChecked stays the primary key even here — an already-checked item
                // shouldn't jump back among the unchecked ones just because its category
                // happens to sort earlier than theirs.
                grouped.mapValues { (storeName, itemsInStore) ->
                    // Each store's own custom gangvolgorde (see StoreEntity.aisleOrder) rather
                    // than Category's fixed sortOrder — computed per store since two real
                    // supermarkets rarely lay their aisles out the same way. Falls back to that
                    // fixed order automatically for a store that's never been customized (or for
                    // "no store" items, which have no StoreEntity to look up at all).
                    val rankByCategory = categoryRankFor(storeByName[storeName])
                    itemsInStore.sortedWith(
                        compareBy(
                            { it.isChecked },
                            { rankByCategory[Category.fromStorageKey(it.category)] ?: Int.MAX_VALUE },
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

    /** Sum of price × quantity across every item on the active list that has a price set
     *  (checked or not) — null (rather than 0.0) when nothing on the list has a price yet, so
     *  ShoppingListScreen can hide the total entirely instead of showing a misleading €0,00. */
    val totalPrice: StateFlow<Double?> =
        groupedByStore.map { grouped ->
            val priced = grouped.values.flatten().mapNotNull { item -> item.price?.let { it * item.quantity } }
            priced.takeIf { it.isNotEmpty() }?.sum()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Product names the household recently scanned/adjusted (see ActivityLogRepository),
     *  most-recent-first and deduplicated, offered as quick-add suggestion chips — reusing
     *  the household's own activity log rather than a dedicated "shopping history" store that
     *  doesn't otherwise exist (removed items aren't kept anywhere once deleted). Names
     *  already on the active list are filtered out so a chip never duplicates a real row. */
    val historySuggestions: StateFlow<List<String>> =
        combine(activityLogRepository.observeRecent(), groupedByStore) { recent, grouped ->
            val onListAlready = grouped.values.flatten().map { it.name.lowercase() }.toSet()
            recent.map { it.productName }
                .distinct()
                .filterNot { it.lowercase() in onListAlready }
                .take(8)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Inventory items running low or out entirely — the same [InventoryStockStatus] Voorraad
     *  itself flags — offered as suggestion chips so restocking doesn't require a trip to
     *  Voorraad first. Items already on the active list are excluded the same way as
     *  [historySuggestions]. */
    val lowStockSuggestions: StateFlow<List<LowStockSuggestion>> =
        combine(inventoryRepository.observeInventoryWithProduct(), groupedByStore) { inventory, grouped ->
            val onListAlready = grouped.values.flatten().map { it.name.lowercase() }.toSet()
            inventory
                .filter { item ->
                    val status = InventoryStockStatus.of(item.quantity, item.minQuantity, item.expirationDate)
                    (status == InventoryStockStatus.LOW_STOCK || status == InventoryStockStatus.OUT_OF_STOCK) &&
                        item.name.lowercase() !in onListAlready
                }
                .map { LowStockSuggestion(it.barcode, it.name, Category.fromStorageKey(it.category)) }
                .take(8)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSortModeChange(mode: ShoppingListSortMode) {
        sortMode.value = mode
    }

    fun selectList(listId: String?) {
        activeListId.value = listId
    }

    /** [onCreated] switches straight to the new list — called from ShoppingListScreen right
     *  after the "nieuwe lijst" dialog is confirmed, so creating a list also opens it. */
    fun createList(name: String, onCreated: () -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            shoppingListsRepository.createList(name).onSuccess { id ->
                activeListId.value = id
                onCreated()
            }
        }
    }

    fun renameList(id: String, name: String) {
        viewModelScope.launch { shoppingListsRepository.renameList(id, name) }
    }

    /** Falls back to the default list if the deleted one was active — there's nothing left to show otherwise. */
    fun deleteList(id: String) {
        viewModelScope.launch {
            shoppingListsRepository.deleteList(id)
            if (activeListId.value == id) activeListId.value = null
        }
    }

    fun addItem(
        name: String,
        category: Category,
        store: String,
        quantity: Int,
        note: String? = null,
        unit: MeasurementUnit = MeasurementUnit.STUKS,
        price: Double? = null,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            shoppingListRepository.addItem(
                name, category, store, quantity,
                note = note, unit = unit, price = price, listId = activeListId.value,
            )
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

    fun setPrice(id: String, price: Double?) {
        viewModelScope.launch { shoppingListRepository.setPrice(id, price) }
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

    // Household inventory, keyed by lowercase name — the source [guessFor] matches a freshly
    // typed item name against. Real, if simple: the household's own inventory is the one durable
    // per-product record this app has (a shopping list item is deleted once bought, so there's no
    // separate "history" to match against instead — see [historySuggestions]'s doc).
    private val inventoryCategoryByName: StateFlow<Map<String, Category>> =
        inventoryRepository.observeInventoryWithProduct()
            .map { items -> items.associate { it.name.trim().lowercase() to Category.fromStorageKey(it.category) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * "Herkend als …" guess for [ItemFormDialog]'s name field (see the 2026-08 dialog review) —
     * an exact (case-insensitive) match against [inventoryCategoryByName], paired with
     * [defaultUnitFor]'s per-category default unit. Inventory items don't carry a
     * [MeasurementUnit] of their own to match against ([com.dtraas.homestock.data.local.entity.ProductEntity.unit]
     * is a free-text packaging string like "500g", not this enum), so the unit half is a
     * sensible default rather than a second lookup. Null — no guess line shown — when nothing in
     * inventory has this exact name yet.
     */
    fun guessFor(name: String): Pair<Category, MeasurementUnit>? {
        val category = inventoryCategoryByName.value[name.trim().lowercase()] ?: return null
        return category to defaultUnitFor(category)
    }

    companion object {
        /** A reasonable per-category default — weighed/measured categories default to the unit
         *  they're actually sold in, everything else defaults to a piece count. */
        private fun defaultUnitFor(category: Category): MeasurementUnit = when (category) {
            Category.VLEES_VIS -> MeasurementUnit.GRAM
            Category.DRANKEN -> MeasurementUnit.LITER
            else -> MeasurementUnit.STUKS
        }
    }
}
