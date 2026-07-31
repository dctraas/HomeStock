package com.dtraas.boodschapbeheer.data.repository

import com.dtraas.boodschapbeheer.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.Store
import com.dtraas.boodschapbeheer.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    private fun shoppingListCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("shoppingList")

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

    suspend fun addItem(
        name: String,
        category: Category,
        store: Store,
        quantity: Int,
        barcode: String? = null,
        imageUrl: String? = null,
        note: String? = null,
    ) {
        val householdId = householdSession.householdId.value ?: return
        val entity = ShoppingListItemEntity(
            id = "",
            barcode = barcode,
            name = name.trim(),
            category = category.storageKey,
            store = store.storageKey,
            imageUrl = imageUrl,
            quantity = quantity.coerceAtLeast(1),
            isChecked = false,
            addedAt = System.currentTimeMillis(),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
        shoppingListCollection(householdId).add(entity.toMap()).await()
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
    }

    /** Re-adds a previously removed item (as a new document) after an undo action. */
    suspend fun restoreItem(item: ShoppingListItemEntity) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).add(item.toMap()).await()
    }

    suspend fun setChecked(id: String, checked: Boolean) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).update("isChecked", checked).await()
    }

    suspend fun setQuantity(id: String, quantity: Int) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).update("quantity", quantity.coerceAtLeast(1)).await()
    }

    suspend fun removeItem(id: String) {
        val householdId = householdSession.householdId.value ?: return
        shoppingListCollection(householdId).document(id).delete().await()
    }

    /** Swaps the manual sort position of two adjacent items (used by the up/down move buttons). */
    suspend fun swapSortOrder(a: ShoppingListItemEntity, b: ShoppingListItemEntity) {
        val householdId = householdSession.householdId.value ?: return
        val collection = shoppingListCollection(householdId)
        val batch = firestore.batch()
        batch.update(collection.document(a.id), "sortOrder", b.sortOrder)
        batch.update(collection.document(b.id), "sortOrder", a.sortOrder)
        batch.commit().await()
    }

    suspend fun clearChecked() {
        val householdId = householdSession.householdId.value ?: return
        val checkedDocs = shoppingListCollection(householdId).whereEqualTo("isChecked", true).get().await()
        if (checkedDocs.isEmpty) return
        val batch = firestore.batch()
        checkedDocs.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}
