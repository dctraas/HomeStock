package com.dtraas.boodschapbeheer.data.local.entity

import com.google.firebase.firestore.DocumentSnapshot

/**
 * One record per barcode scan, kept even after a product is removed from
 * the inventory, so "hoe vaak en wanneer gescand" history is never lost.
 * Stored at `households/{householdId}/scanHistory/{id}`; [id] is the
 * Firestore-generated document id.
 */
data class ScanHistoryEntity(
    val id: String,
    val barcode: String,
    val scannedAt: Long,
    val quantityDelta: Int,
) {
    companion object {
        fun fromDocument(document: DocumentSnapshot): ScanHistoryEntity? {
            val barcode = document.getString("barcode") ?: return null
            val scannedAt = document.getLong("scannedAt") ?: return null
            val quantityDelta = document.getLong("quantityDelta") ?: return null
            return ScanHistoryEntity(
                id = document.id,
                barcode = barcode,
                scannedAt = scannedAt,
                quantityDelta = quantityDelta.toInt(),
            )
        }
    }
}
