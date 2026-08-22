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

/**
 * A product ranked by how many times it's been removed as food waste, for the statistics
 * screen. [wastedValue] is [wastedCount] × the product's current [ProductEntity.lastPrice] (0
 * for a product never priced) — an approximation, not a true per-event historical price, since
 * an activityLog "wasted" entry doesn't itself record what the product cost at that moment.
 */
data class TopWastedProduct(
    val barcode: String,
    val name: String,
    val category: String,
    val imageUrl: String?,
    val wastedCount: Int,
    val wastedValue: Double = 0.0,
)
