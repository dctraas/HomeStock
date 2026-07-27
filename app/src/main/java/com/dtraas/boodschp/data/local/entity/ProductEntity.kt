package com.dtraas.boodschp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached catalog data for a scanned barcode, sourced from the Open Food
 * Facts API (or entered manually when a barcode is unknown).
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val category: String,
    val imageUrl: String?,
    val unit: String?,
    val lastFetchedAt: Long,
)
