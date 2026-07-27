package com.dtraas.boodschp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A product currently held in stock. One row per barcode; deleting this row
 * removes the product from the inventory overview without touching the
 * cached [ProductEntity] catalog data or its scan history.
 */
@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["barcode"],
            childColumns = ["barcode"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class InventoryItemEntity(
    @PrimaryKey val barcode: String,
    val quantity: Int,
    val updatedAt: Long,
)
