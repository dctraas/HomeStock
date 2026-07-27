package com.dtraas.boodschp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per barcode scan, kept even after a product is removed from the
 * inventory, so "hoe vaak en wanneer gescand" history is never lost.
 */
@Entity(
    tableName = "scan_history",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["barcode"],
            childColumns = ["barcode"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("barcode")],
)
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val scannedAt: Long,
    val quantityDelta: Int,
)
