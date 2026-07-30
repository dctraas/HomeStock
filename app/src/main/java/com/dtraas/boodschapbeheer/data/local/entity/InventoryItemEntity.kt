package com.dtraas.boodschapbeheer.data.local.entity

import com.google.firebase.firestore.DocumentSnapshot

/**
 * A product currently held in stock. Stored at
 * `households/{householdId}/inventory/{barcode}` — [barcode] is the
 * document id, not a stored field. Deleting this document removes the
 * product from the inventory overview without touching the cached
 * [ProductEntity] catalog data or its scan history.
 *
 * [expirationDate] is a calendar date (epoch millis at UTC midnight, as
 * produced by Compose's Material3 date picker) rather than a precise
 * instant. [minQuantity], when set, is the threshold below which the
 * product is automatically re-added to the shopping list.
 */
data class InventoryItemEntity(
    val barcode: String,
    val quantity: Int,
    val updatedAt: Long,
    val expirationDate: Long? = null,
    val minQuantity: Int? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "quantity" to quantity,
        "updatedAt" to updatedAt,
        "expirationDate" to expirationDate,
        "minQuantity" to minQuantity,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): InventoryItemEntity? {
            val quantity = document.getLong("quantity") ?: return null
            return InventoryItemEntity(
                barcode = document.id,
                quantity = quantity.toInt(),
                updatedAt = document.getLong("updatedAt") ?: 0L,
                expirationDate = document.getLong("expirationDate"),
                minQuantity = document.getLong("minQuantity")?.toInt(),
            )
        }
    }
}
