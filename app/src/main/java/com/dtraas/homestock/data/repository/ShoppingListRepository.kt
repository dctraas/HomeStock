package com.dtraas.homestock.data.repository

import android.content.Context
import com.dtraas.homestock.data.local.entity.ShoppingListItemEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.data.remote.observeSnapshots
import com.dtraas.homestock.widget.updateShoppingListWidget
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
    private val productRepository: ProductRepository,
    private val deviceProfile: DeviceProfile,
) {
    private fun shoppingListCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("shoppingList")

    // Every mutation refreshes the home screen widget, which otherwise only shows a
    // one-shot snapshot from whenever Android last woke it up — without this it wouldn't
    // reflect changes made in-app until the system's next (infrequent) scheduled update.
    private suspend fun refreshWidget() = updateShoppingListWidget(context)

    fun observeShoppingList(): Flow<List<ShoppingListItemEntity>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                shoppingListCollection(householdId).observeSnapshots().map { snapshot ->
                    snapshot.documents
                        .mapNotNull { ShoppingListItemEntity.fromDocument(it) }
                        .sortedWith(compareBy<ShoppingListItemEntity> { it.isChecked }.thenBy { it.sortOrder })
                }
            }
        }

    /**
     * [observeShoppingList] filtered to one list — [listId] null means the default (unnamed)
     * list. Client-side filtering rather than a Firestore `.whereEqualTo("listId", listId)`
     * query deliberately: every item written before lists existed has no `listId` field at all
     * (rather than an explicit null), and Firestore's equality operator only matches an
     * explicit null, never a genuinely absent field — a query-level filter would silently
     * exclude every pre-existing item from the default list. Reusing the same underlying
     * listener this way also means switching lists in the UI is instant, no new Firestore
     * round-trip.
     */
    fun observeItemsForList(listId: String?): Flow<List<ShoppingListItemEntity>> =
        observeShoppingList().map { items -> items.filter { it.listId == listId } }

    /** One-shot (non-listening) fetch of unchecked items, for surfaces like the home screen widget that can't hold a live Firestore listener open. */
    suspend fun getUncheckedItemsOnce(): List<ShoppingListItemEntity> {
        val householdId = householdSession.householdId.value ?: return emptyList()
        val snapshot = shoppingListCollection(householdId).whereEqualTo("isChecked", false).get().await()
        return snapshot.documents
            .mapNotNull { ShoppingListItemEntity.fromDocument(it) }
            .sortedWith(compareBy({ it.store.isBlank() }, { it.store }, { it.sortOrder }))
    }

    suspend fun addItem(
        name: String,
        category: Category,
        store: String,
        quantity: Int,
        barcode: String? = null,
        imageUrl: String? = null,
        note: String? = null,
        unit: MeasurementUnit = MeasurementUnit.STUKS,
        price: Double? = null,
        // null = the default (unnamed) list — see [ShoppingListItemEntity.listId]. Every
        // existing caller (recipe/meal-planner/notification/widget quick-adds, ScanResultScreen,
        // ProductDetailScreen) omits this and keeps landing on the default list unchanged;
        // only ShoppingListScreen's own add-item flow ever passes a real one.
        listId: String? = null,
        // Returns the new document's id (or null if there's no active household) — most
        // callers ignore it, same as before this became non-Unit, but Recepten/Maaltijdplanner's
        // "Op lijst" bulk-adds need it back to support their undo snackbar (see
        // RecipesViewModel.addMissingIngredientsToShoppingList).
    ): String? {
        val householdId = householdSession.householdId.value ?: return null
        val entity = ShoppingListItemEntity(
            id = "",
            barcode = barcode,
            name = name.trim(),
            category = category.storageKey,
            store = store.trim(),
            imageUrl = imageUrl,
            quantity = quantity.coerceAtLeast(1),
            isChecked = false,
            addedAt = System.currentTimeMillis(),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            unit = unit.storageKey,
            price = price,
            listId = listId,
            addedByName = deviceProfile.displayName.value?.trim()?.takeIf { it.isNotEmpty() },
        )
        val ref = shoppingListCollection(householdId).add(entity.toMap()).await()
        refreshWidget()
        return ref.id
    }

    /** True if there's already an unchecked shopping list line for [barcode]. */
    suspend fun hasOpenItemForBarcode(barcode: String): Boolean {
        val householdId = householdSession.householdId.value ?: return false
        val matches = shoppingListCollection(householdId)
            .whereEqualTo("barcode", barcode)
            .whereEqualTo("isChecked", false)
            .get()
            .await()
        return !matches.isEmpty
    }

    suspend fun updateItem(item: ShoppingListItemEntity) {
        val householdId = householdSession.householdId.value ?: return
        val updated = item.copy(name = item.name.trim(), quantity = item.quantity.coerceAtLeast(1))
        shoppingListCollection(householdId).document(updated.id).set(updated.toMap()).await()
        refreshWidget()
    }

    /** Re-adds a previously removed item (as a new document) after an undo action. */
    suspend fun restoreItem(item: ShoppingListItemEntity) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).add(item.toMap()).await()
        refreshWidget()
    }

    /**
     * Checking an item off with both a [ShoppingListItemEntity.price] and a [ShoppingListItemEntity.barcode]
     * set records that price on the matching product (see [ProductRepository.addPricePoint]) —
     * the same price history a receipt scan feeds, so "typed it in myself" and "scanned the
     * receipt" build one continuous record either way. Requires the extra read below (this
     * previously updated the `isChecked` field alone, blind to the rest of the document) only
     * when actually checking something *on*; unchecking stays the same single write it always was.
     */
    suspend fun setChecked(id: String, checked: Boolean) {
        val householdId = householdSession.householdId.value ?: return
        val itemRef = shoppingListCollection(householdId).document(id)
        if (checked) {
            val item = ShoppingListItemEntity.fromDocument(itemRef.get().await())
            val price = item?.price
            val barcode = item?.barcode
            if (price != null && barcode != null) {
                productRepository.addPricePoint(barcode, price, item.store)
            }
        }
        itemRef.update("isChecked", checked).await()
        refreshWidget()
    }

    /** Sets (or clears, with `null`) this item's own per-unit price — see [ShoppingListItemEntity.price]. */
    suspend fun setPrice(id: String, price: Double?) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).update("price", price).await()
        refreshWidget()
    }

    suspend fun setQuantity(id: String, quantity: Int) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).update("quantity", quantity.coerceAtLeast(1)).await()
        refreshWidget()
    }

    /** Quick single-field store reassignment — see the per-row store picker in ShoppingListScreen. */
    suspend fun setStore(id: String, store: String) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).update("store", store.trim()).await()
        refreshWidget()
    }

    suspend fun removeItem(id: String) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).delete().await()
        refreshWidget()
    }

    /**
     * Moves [item] to sit between [previous] and [next] (either may be null at a list
     * boundary) by writing a single new sortOrder value — the midpoint of its new
     * neighbors, or one past whichever neighbor exists. Used by drag-to-reorder.
     */
    suspend fun moveItem(item: ShoppingListItemEntity, previous: ShoppingListItemEntity?, next: ShoppingListItemEntity?) {
        val householdId = householdSession.householdId.value ?: return
        val newSortOrder = when {
            previous != null && next != null -> (previous.sortOrder + next.sortOrder) / 2.0
            // See StoreRepository.moveStore's identical fix — these two were swapped, so
            // landing at either end of the list picked a sortOrder that put the item back
            // toward the middle instead of actually leaving it at that end.
            previous != null -> previous.sortOrder + 1.0
            next != null -> next.sortOrder - 1.0
            else -> return
        }
        if (newSortOrder == item.sortOrder) return
        shoppingListCollection(householdId).document(item.id).update("sortOrder", newSortOrder).await()
        refreshWidget()
    }

    /**
     * Reassigns every item currently on [storeName] to "Geen winkel" (empty string) — called
     * right before that store itself is deleted (see MoreScreen's Winkels sheet), so items don't
     * keep pointing at a store that no longer exists in the household's own list. A no-op if
     * nothing's currently on that store.
     */
    suspend fun clearStoreFromItems(storeName: String) {
        val householdId = householdSession.householdId.value ?: return
        val docs = shoppingListCollection(householdId).whereEqualTo("store", storeName).get().await()
        if (docs.isEmpty) return
        val batch = firestore.batch()
        docs.documents.forEach { batch.update(it.reference, "store", "") }
        batch.commit().await()
        refreshWidget()
    }

    /**
     * Repoints every item currently on [oldName] to [newName] — called right after a store
     * itself is renamed (see StoreRepository.renameStore/MoreScreen's Winkels sheet), since
     * items reference a store by this plain name string rather than its id (see
     * ShoppingListItemEntity.store's own doc) and would otherwise silently fall off that
     * store's section the moment its name changes. Same batch shape as [clearStoreFromItems],
     * which does the equivalent reassignment when a store is deleted outright instead of renamed.
     */
    suspend fun renameStoreOnItems(oldName: String, newName: String) {
        val householdId = householdSession.householdId.value ?: return
        val docs = shoppingListCollection(householdId).whereEqualTo("store", oldName).get().await()
        if (docs.isEmpty) return
        val batch = firestore.batch()
        docs.documents.forEach { batch.update(it.reference, "store", newName) }
        batch.commit().await()
        refreshWidget()
    }

    suspend fun checkAll() {
        val householdId = householdSession.householdId.value ?: return
        val uncheckedDocs = shoppingListCollection(householdId).whereEqualTo("isChecked", false).get().await()
        if (uncheckedDocs.isEmpty) return
        val batch = firestore.batch()
        uncheckedDocs.documents.forEach { batch.update(it.reference, "isChecked", true) }
        batch.commit().await()
        refreshWidget()
    }

    suspend fun clearChecked() {
        val householdId = householdSession.householdId.value ?: return
        val checkedDocs = shoppingListCollection(householdId).whereEqualTo("isChecked", true).get().await()
        if (checkedDocs.isEmpty) return
        val batch = firestore.batch()
        checkedDocs.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
        refreshWidget()
    }
}
