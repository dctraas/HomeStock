package com.dtraas.boodschapbeheer.data.local.entity

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Cached catalog data for a scanned barcode, sourced from the Open Food
 * Facts API (or entered manually when a barcode is unknown). Stored at
 * `households/{householdId}/products/{barcode}` — [barcode] is the document
 * id, not a stored field.
 */
data class ProductEntity(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: String,
    val imageUrl: String?,
    val unit: String?,
    val lastFetchedAt: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "brand" to brand,
        "category" to category,
        "imageUrl" to imageUrl,
        "unit" to unit,
        "lastFetchedAt" to lastFetchedAt,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): ProductEntity? {
            val name = document.getString("name") ?: return null
            return ProductEntity(
                barcode = document.id,
                name = name,
                brand = document.getString("brand"),
                category = document.getString("category") ?: "overig",
                imageUrl = document.getString("imageUrl"),
                unit = document.getString("unit"),
                lastFetchedAt = document.getLong("lastFetchedAt") ?: 0L,
            )
        }
    }
}
