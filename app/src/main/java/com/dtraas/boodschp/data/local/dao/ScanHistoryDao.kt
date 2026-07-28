package com.dtraas.boodschp.data.local.dao

/** A product ranked by how often it has been scanned, for the statistics screen. */
data class TopScannedProduct(
    val barcode: String,
    val name: String,
    val category: String,
    val imageUrl: String?,
    val scanCount: Int,
)
