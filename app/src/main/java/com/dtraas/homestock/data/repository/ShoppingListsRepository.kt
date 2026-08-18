package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.entity.ShoppingListMeta
import com.dtraas.homestock.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The household's named shopping lists alongside its one default list — see
 * [ShoppingListMeta]'s doc for why the default list itself never has a document here. Deleting
 * a list also deletes every item on it (see [deleteList]); the default list can't be deleted at
 * all, since it isn't a document this repository manages in the first place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListsRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    private fun listsCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("shoppingLists")

    private fun shoppingListCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("shoppingList")

    fun observeLists(): Flow<List<ShoppingListMeta>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                listsCollection(householdId).observeSnapshots().map { snapshot ->
                    snapshot.documents.mapNotNull { ShoppingListMeta.fromDocument(it) }.sortedBy { it.sortOrder }
                }
            }
        }

    /** Creates a new named list and returns its id, for the caller to switch to right away. */
    suspend fun createList(name: String): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("name is blank"))
        val householdId = householdSession.householdId.value
            ?: return Result.failure(IllegalStateException("no household"))
        return try {
            val existing = listsCollection(householdId).get().await()
            val nextSortOrder = (existing.documents.maxOfOrNull { it.getDouble("sortOrder") ?: 0.0 } ?: -1.0) + 1
            val ref = listsCollection(householdId)
                .add(ShoppingListMeta(id = "", name = trimmed, sortOrder = nextSortOrder).toMap())
                .await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameList(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val householdId = householdSession.householdId.value ?: return
        listsCollection(householdId).document(id).update("name", trimmed).await()
    }

    /**
     * Deletes [id] itself plus every item on it — a named list has no "move items back to the
     * default list" fallback, since silently relocating a household's items without them
     * choosing to is more surprising than just being clear up front (see the confirmation
     * dialog this is called from) that deleting a list deletes what's on it. The Firestore
     * client SDK has no server-side recursive/queried batch delete, so items are paged through
     * the same way HouseholdRepository.deleteCollection already does for a whole household.
     */
    suspend fun deleteList(id: String) {
        val householdId = householdSession.householdId.value ?: return
        val itemsCollection = shoppingListCollection(householdId)
        while (true) {
            val batchDocs = itemsCollection.whereEqualTo("listId", id).limit(DELETE_BATCH_SIZE.toLong()).get().await()
            if (batchDocs.isEmpty) break
            val batch = firestore.batch()
            batchDocs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (batchDocs.size() < DELETE_BATCH_SIZE) break
        }
        listsCollection(householdId).document(id).delete().await()
    }

    private companion object {
        const val DELETE_BATCH_SIZE = 400
    }
}
