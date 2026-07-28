package com.dtraas.boodschapbeheer.data.local.entity

import com.google.firebase.firestore.DocumentSnapshot

/**
 * A single line on the shopping list. [barcode] is nullable because items
 * can be typed in by hand as well as added from a scanned/known product.
 * Stored at `households/{householdId}/shoppingList/{id}`; [id] is the
 * Firestore-generated document id.
 */
data class ShoppingListItemEntity(
    val id: String,
    val barcode: String?,
    val name: String,
    val category: String,
    val store: String,
    val imageUrl: String?,
    val quantity: Int,
    val isChecked: Boolean,
    val addedAt: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "barcode" to barcode,
        "name" to name,
        "category" to category,
        "store" to store,
        "imageUrl" to imageUrl,
        "quantity" to quantity,
        "isChecked" to isChecked,
        "addedAt" to addedAt,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): ShoppingListItemEntity? {
            val name = document.getString("name") ?: return null
            return ShoppingListItemEntity(
                id = document.id,
                barcode = document.getString("barcode"),
                name = name,
                category = document.getString("category") ?: "overig",
                store = document.getString("store") ?: "geen",
                imageUrl = document.getString("imageUrl"),
                quantity = (document.getLong("quantity") ?: 1L).toInt(),
                isChecked = document.getBoolean("isChecked") ?: false,
                addedAt = document.getLong("addedAt") ?: 0L,
            )
        }
    }
}
