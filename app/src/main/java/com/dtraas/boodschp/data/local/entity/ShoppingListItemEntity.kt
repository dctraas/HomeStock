package com.dtraas.boodschp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single line on the shopping list. [barcode] is nullable because items
 * can be typed in by hand as well as added from a scanned/known product.
 */
@Entity(tableName = "shopping_list_items")
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val category: String,
    val store: String,
    val imageUrl: String?,
    val quantity: Int,
    val isChecked: Boolean,
    val addedAt: Long,
)
