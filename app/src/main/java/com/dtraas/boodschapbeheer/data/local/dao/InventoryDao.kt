package com.dtraas.boodschapbeheer.data.local.dao

/**
 * Flattened row combining an inventory entry with its cached product fields,
 * produced by joining the `inventory` and `products` Firestore collections
 * in [com.dtraas.boodschapbeheer.data.repository.InventoryRepository] so the UI
 * never has to stitch the two together.
 */
data class InventoryItemWithProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: String,
    val imageUrl: String?,
    val unit: String?,
    val quantity: Int,
    val updatedAt: Long,
)

/** Number of distinct inventory items per category, for the statistics screen. */
data class CategoryCount(
    val category: String,
    val count: Int,
)
