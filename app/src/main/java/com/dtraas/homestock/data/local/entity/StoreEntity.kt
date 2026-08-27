package com.dtraas.homestock.data.local.entity

import com.dtraas.homestock.data.model.Category
import com.google.firebase.firestore.DocumentSnapshot

/**
 * A custom, household-defined store offered when adding a shopping list item. This is
 * only a suggestion list — a shopping list item's own `store` field holds the store's
 * name directly (see [ShoppingListItemEntity]), not this document's id, so removing a
 * store here never orphans anything already on the list.
 */
data class StoreEntity(
    val id: String,
    val name: String,
    val sortOrder: Double,
    // The household's own walking order through THIS store's aisles ("gangvolgorde") — what
    // decides the "gang N" order Winkelmodus (see ShoppingModeScreen) and the "Winkelindeling"
    // sort mode (ShoppingListViewModel/ShoppingListSortMode.AISLE) group this store's products
    // in, instead of Category's own fixed sortOrder. Each element is one *path* (one physical
    // aisle) — a single Category storage key ("zuivel"), or several joined with "," when more
    // than one category lives in the same aisle ("zuivel,kaas"). See [aislePaths] for the
    // parsed form; edited via MoreScreen's Winkels dialog. Empty (never customized, or a
    // category the household added after customizing) means "fall back to Category's own
    // sortOrder for that category" — see [aislePaths] and ShoppingListViewModel.groupedByStore's
    // own AISLE-mode rank map for exactly how that fallback merge happens. Deliberately
    // per-store, not one household-wide order: two real supermarkets rarely lay their aisles
    // out the same way.
    val aisleOrder: List<String> = emptyList(),
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "sortOrder" to sortOrder,
        "aisleOrder" to aisleOrder,
    )

    /**
     * [aisleOrder] parsed into paths (each a non-empty [Category] list, comma-joined keys split
     * back apart) plus every category the household hasn't placed yet, each as its own
     * trailing single-category path in [Category]'s own fixed [Category.sortOrder] order — so
     * the result always covers every category exactly once, even for a store that's never been
     * customized at all (then it's just 11 one-category paths in the default order).
     */
    fun aislePaths(): List<List<Category>> {
        val paths = aisleOrder.mapNotNull { entry ->
            val categories = entry.split(",").mapNotNull { key -> Category.entries.find { it.storageKey == key } }
            categories.takeIf { it.isNotEmpty() }
        }
        val placed = paths.flatten().toSet()
        val remaining = Category.entries.sortedBy { it.sortOrder }.filterNot { it in placed }
        return paths + remaining.map { listOf(it) }
    }

    companion object {
        fun fromDocument(document: DocumentSnapshot): StoreEntity? {
            val name = document.getString("name") ?: return null
            return StoreEntity(
                id = document.id,
                name = name,
                sortOrder = document.getDouble("sortOrder") ?: 0.0,
                aisleOrder = (document.get("aisleOrder") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            )
        }
    }
}

/** Serializes [paths] (see [StoreEntity.aisleOrder]'s doc) back into the comma-joined storage
 *  form — the inverse of [StoreEntity.aislePaths]. */
fun List<List<Category>>.toAisleOrderStorage(): List<String> =
    map { path -> path.joinToString(",") { it.storageKey } }
