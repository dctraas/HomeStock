package com.dtraas.homestock.data.local.entity

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
    // The household's own walking order through THIS store's aisles, as an ordered list of
    // Category storage keys — what decides the "gang N" order Winkelmodus (see
    // ShoppingModeScreen) and the "Winkelindeling" sort mode (ShoppingListViewModel/
    // ShoppingListSortMode.AISLE) group this store's products in, instead of Category's own
    // fixed sortOrder. Empty (never customized, or a category the household added after
    // customizing) means "fall back to Category's own sortOrder for that category" — see
    // ShoppingListViewModel.groupedByStore's own AISLE-mode rank map for exactly how that
    // fallback merge happens. Deliberately per-store, not one household-wide order: two real
    // supermarkets rarely lay their aisles out the same way.
    val aisleOrder: List<String> = emptyList(),
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "sortOrder" to sortOrder,
        "aisleOrder" to aisleOrder,
    )

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
