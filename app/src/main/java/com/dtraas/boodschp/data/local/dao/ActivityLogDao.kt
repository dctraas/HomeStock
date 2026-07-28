package com.dtraas.boodschp.data.local.dao

/** An activity log entry joined with the product's current name, for display. */
data class ActivityLogWithProduct(
    val id: String,
    val barcode: String,
    val productName: String,
    val type: String,
    val detail: String,
    val timestamp: Long,
)
