package com.dtraas.boodschapbeheer.data.repository

import android.content.Context
import com.dtraas.boodschapbeheer.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.MeasurementUnit
import com.dtraas.boodschapbeheer.data.remote.observeSnapshots
import com.dtraas.boodschapbeheer.widget.updateShoppingListWidget
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
    ) {
        val householdId = householdSession.householdId.value ?: return
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
        )
        shoppingListCollection(householdId).add(entity.toMap()).await()
        refreshWidget()
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

    suspend fun setChecked(id: String, checked: Boolean) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).update("isChecked", checked).await()
        refreshWidget()
    }

    suspend fun setQuantity(id: String, quantity: Int) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).update("quantity", quantity.coerceAtLeast(1)).await()
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
            previous != null -> previous.sortOrder - 1.0
            next != null -> next.sortOrder + 1.0
            else -> return
        }
        if (newSortOrder == item.sortOrder) return
        shoppingListCollection(householdId).document(item.id).update("sortOrder", newSortOrder).await()
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
