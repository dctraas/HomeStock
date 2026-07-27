package com.dtraas.boodschp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per user-visible inventory change (scan, quantity edit, removal,
 * added to shopping list), shown on the "Wijzigingen" screen.
 */
@Entity(
    tableName = "activity_log",
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
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val type: String,
    val detail: String,
    val timestamp: Long,
)
