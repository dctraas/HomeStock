package com.dtraas.homestock.data.local.dao

/** A product ranked by how often it has been scanned, for the statistics screen. */
data class TopScannedProduct(
    val barcode: String,
    val name: String,
    val category: String,
    val imageUrl: String?,
    val scanCount: Int,
)

/** How many scans a household member is responsible for; null [actorName] means no name was set. */
data class ActorScanCount(
    val actorName: String?,
    val scanCount: Int,
)
