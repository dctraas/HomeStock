package com.dtraas.homestock.data.local.entity

import com.google.firebase.firestore.DocumentSnapshot

/**
 * One named shopping list a household created alongside the default one — e.g. "IKEA" or
 * "Verjaardag zaterdag". Stored at `households/{householdId}/shoppingLists/{id}`; a
 * [ShoppingListItemEntity] belongs to one of these via its own `listId` field, matched by [id].
 *
 * The default (unnamed) list every household starts with is never a document here — it's
 * synthesized in the UI (see ShoppingListViewModel) as the entry with a `null` [id], matching
 * [ShoppingListItemEntity.listId]'s "null means default" convention (hence [id] being nullable
 * here, unlike a real Firestore document id ever is). That's what makes every item ever written
 * before this feature existed already "on" the default list with zero migration: it simply has
 * no `listId` field at all, which reads back as null here too.
 */
data class ShoppingListMeta(
    val id: String?,
    val name: String,
    val sortOrder: Double,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "sortOrder" to sortOrder,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): ShoppingListMeta? {
            val name = document.getString("name") ?: return null
            return ShoppingListMeta(
                id = document.id,
                name = name,
                sortOrder = document.getDouble("sortOrder") ?: 0.0,
            )
        }
    }
}
