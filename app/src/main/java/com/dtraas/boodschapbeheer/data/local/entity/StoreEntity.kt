package com.dtraas.boodschapbeheer.data.local.entity

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
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "sortOrder" to sortOrder,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): StoreEntity? {
            val name = document.getString("name") ?: return null
            return StoreEntity(
                id = document.id,
                name = name,
                sortOrder = document.getDouble("sortOrder") ?: 0.0,
            )
        }
    }
}
