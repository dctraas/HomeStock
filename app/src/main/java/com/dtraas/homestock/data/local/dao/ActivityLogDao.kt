package com.dtraas.homestock.data.local.dao

/** An activity log entry joined with the product's current name, for display. */
data class ActivityLogWithProduct(
    val id: String,
    val barcode: String,
    val productName: String,
    val type: String,
    val detail: String,
    val timestamp: Long,
    val actorName: String? = null,
)

/** A product ranked by how many times it's been removed as food waste, for the statistics screen. */
data class TopWastedProduct(
    val barcode: String,
    val name: String,
    val category: String,
    val imageUrl: String?,
    val wastedCount: Int,
)
