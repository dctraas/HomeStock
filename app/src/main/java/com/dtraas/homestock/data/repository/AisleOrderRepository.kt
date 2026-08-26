package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.remote.observeSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The household's own walking order through a store's aisles — what decides the "gang N" order
 * Winkelmodus (see `ShoppingModeScreen`) and the "Winkelindeling" sort mode (see
 * `ShoppingListViewModel`/`ShoppingListSortMode.AISLE`) group products in, instead of
 * [Category]'s own fixed [Category.sortOrder]. One order for the whole household, not per store —
 * a real per-store order (with its own settings screen per store, and a way to pick which store
 * you're editing) is a meaningfully bigger feature than this covers; a single custom order
 * already beats the fixed default for whichever store a household shops most, and is honest about
 * what it actually does rather than pretending to be per-store when it isn't.
 *
 * Stored as a single ordered array of [Category.storageKey]s on the household doc itself
 * (`households/{id}.aisleOrder`) — a household-wide preference, not its own subcollection, since
 * it's just one list. Missing entirely (never customized) or missing any category (e.g. a
 * category added to the app after a household already customized) both fall back to that
 * category's own [Category.sortOrder] position, appended after whatever the household did
 * explicitly order — so a fresh install and an old customization both always yield a complete,
 * valid order covering every [Category].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AisleOrderRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    private fun householdDoc(householdId: String) = firestore.collection("households").document(householdId)

    val defaultOrder: List<Category> = Category.entries.sortedBy { it.sortOrder }

    fun observeAisleOrder(): Flow<List<Category>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(defaultOrder)
            } else {
                householdDoc(householdId).observeSnapshot().map { snapshot ->
                    val storedKeys = (snapshot.get("aisleOrder") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    val custom = storedKeys.mapNotNull { key -> Category.entries.find { it.storageKey == key } }
                    custom + defaultOrder.filterNot { it in custom }
                }
            }
        }

    suspend fun setAisleOrder(order: List<Category>) {
        val householdId = householdSession.householdId.value ?: return
        householdDoc(householdId).set(mapOf("aisleOrder" to order.map { it.storageKey }), SetOptions.merge()).await()
    }
}
