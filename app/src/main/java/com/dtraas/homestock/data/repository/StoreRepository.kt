package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The household's custom list of stores — offered as suggestions when adding a shopping
 * list item, and manageable from Meer. Starts empty; the household adds its own stores.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    private fun storesCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("stores")

    fun observeStores(): Flow<List<StoreEntity>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                storesCollection(householdId).observeSnapshots().map { snapshot ->
                    snapshot.documents.mapNotNull { StoreEntity.fromDocument(it) }.sortedBy { it.sortOrder }
                }
            }
        }

    suspend fun addStore(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val householdId = householdSession.householdId.value ?: return
        val existing = storesCollection(householdId).get().await()
        val nextSortOrder = (existing.documents.maxOfOrNull { it.getDouble("sortOrder") ?: 0.0 } ?: -1.0) + 1
        storesCollection(householdId).add(StoreEntity(id = "", name = trimmed, sortOrder = nextSortOrder).toMap()).await()
    }

    suspend fun removeStore(id: String) {
        val householdId = householdSession.householdId.value ?: return
        storesCollection(householdId).document(id).delete().await()
    }

    /** Drag-to-reorder — same median-sortOrder swap as ShoppingListRepository.moveItem, just
     *  over the household's own store list instead of one list's items. Reorders the shopping
     *  list's store sections (see StoreHeader/groupedByStore), not anything about the stores
     *  screen's own presentation order alone. */
    suspend fun moveStore(store: StoreEntity, previous: StoreEntity?, next: StoreEntity?) {
        val householdId = householdSession.householdId.value ?: return
        val newSortOrder = when {
            previous != null && next != null -> (previous.sortOrder + next.sortOrder) / 2.0
            previous != null -> previous.sortOrder - 1.0
            next != null -> next.sortOrder + 1.0
            else -> return
        }
        if (newSortOrder == store.sortOrder) return
        storesCollection(householdId).document(store.id).update("sortOrder", newSortOrder).await()
    }

    /** Persists this store's own custom gangvolgorde — see [StoreEntity.aisleOrder]'s own doc
     *  for the fallback merge that happens once this is read back. */
    suspend fun setAisleOrder(store: StoreEntity, order: List<Category>) {
        val householdId = householdSession.householdId.value ?: return
        storesCollection(householdId).document(store.id).update("aisleOrder", order.map { it.storageKey }).await()
    }
}
