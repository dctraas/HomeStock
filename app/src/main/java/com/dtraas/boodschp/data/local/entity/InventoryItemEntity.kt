package com.dtraas.boodschp.data.local.entity

import com.google.firebase.firestore.DocumentSnapshot

/**
 * A product currently held in stock. Stored at
 * `households/{householdId}/inventory/{barcode}` — [barcode] is the
 * document id, not a stored field. Deleting this document removes the
 * product from the inventory overview without touching the cached
 * [ProductEntity] catalog data or its scan history.
 */
data class InventoryItemEntity(
    val barcode: String,
    val quantity: Int,
    val updatedAt: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "quantity" to quantity,
        "updatedAt" to updatedAt,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): InventoryItemEntity? {
            val quantity = document.getLong("quantity") ?: return null
            return InventoryItemEntity(
                barcode = document.id,
                quantity = quantity.toInt(),
                updatedAt = document.getLong("updatedAt") ?: 0L,
            )
        }
    }
}
