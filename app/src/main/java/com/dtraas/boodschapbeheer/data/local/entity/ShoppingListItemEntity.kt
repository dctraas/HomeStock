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
    val note: String? = null,
    // Ascending sort key for manual reordering. Defaults to -addedAt so freshly added
    // items sort first without a migration or extra read; moving an item just swaps
    // this value with a neighbor's, so reordering is an O(1) two-document write.
    val sortOrder: Double = -addedAt.toDouble(),
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
        "note" to note,
        "sortOrder" to sortOrder,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): ShoppingListItemEntity? {
            val name = document.getString("name") ?: return null
            val addedAt = document.getLong("addedAt") ?: 0L
            return ShoppingListItemEntity(
                id = document.id,
                barcode = document.getString("barcode"),
                name = name,
                category = document.getString("category") ?: "overig",
                store = document.getString("store") ?: "geen",
                imageUrl = document.getString("imageUrl"),
                quantity = (document.getLong("quantity") ?: 1L).toInt(),
                isChecked = document.getBoolean("isChecked") ?: false,
                addedAt = addedAt,
                note = document.getString("note")?.takeIf { it.isNotBlank() },
                sortOrder = document.getDouble("sortOrder") ?: -addedAt.toDouble(),
            )
        }
    }
}
