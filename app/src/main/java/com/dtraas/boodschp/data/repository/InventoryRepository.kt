package com.dtraas.boodschp.data.repository

import androidx.room.withTransaction
import com.dtraas.boodschp.data.local.AppDatabase
import com.dtraas.boodschp.data.local.dao.InventoryDao
import com.dtraas.boodschp.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschp.data.local.dao.ScanHistoryDao
import com.dtraas.boodschp.data.local.entity.InventoryItemEntity
import com.dtraas.boodschp.data.local.entity.ScanHistoryEntity
import com.dtraas.boodschp.data.model.ActivityType
import kotlinx.coroutines.flow.Flow

class InventoryRepository(
    private val database: AppDatabase,
    private val inventoryDao: InventoryDao,
    private val scanHistoryDao: ScanHistoryDao,
    private val activityLogRepository: ActivityLogRepository,
) {
    /** Flat inventory list joined with product data, unsorted into categories. */
    fun observeInventoryWithProduct(): Flow<List<InventoryItemWithProduct>> =
        inventoryDao.observeInventoryWithProduct()

    /**
     * Registers a barcode scan: bumps (or creates) the inventory quantity by
     * [quantityDelta] and appends a scan-history entry, atomically.
     */
    suspend fun recordScan(barcode: String, quantityDelta: Int = 1) {
        database.withTransaction {
            val existing = inventoryDao.findByBarcode(barcode)
            val newQuantity = (existing?.quantity ?: 0) + quantityDelta
            inventoryDao.upsert(
                InventoryItemEntity(
                    barcode = barcode,
                    quantity = newQuantity.coerceAtLeast(0),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            scanHistoryDao.insert(
                ScanHistoryEntity(
                    barcode = barcode,
                    scannedAt = System.currentTimeMillis(),
                    quantityDelta = quantityDelta,
                )
            )
        }
        val sign = if (quantityDelta >= 0) "+" else ""
        activityLogRepository.log(barcode, ActivityType.SCANNED, "Gescand ($sign$quantityDelta)")
    }

    suspend fun updateQuantity(barcode: String, quantity: Int) {
        val clamped = quantity.coerceAtLeast(0)
        val previousQuantity = inventoryDao.findByBarcode(barcode)?.quantity ?: 0
        inventoryDao.upsert(
            InventoryItemEntity(
                barcode = barcode,
                quantity = clamped,
                updatedAt = System.currentTimeMillis(),
            )
        )
        if (previousQuantity != clamped) {
            activityLogRepository.log(
                barcode,
                ActivityType.QUANTITY_CHANGED,
                "Aantal aangepast van $previousQuantity naar $clamped",
            )
        }
    }

    suspend fun removeFromInventory(barcode: String) {
        val existing = inventoryDao.findByBarcode(barcode)
        inventoryDao.deleteByBarcode(barcode)
        if (existing != null) {
            activityLogRepository.log(
                barcode,
                ActivityType.REMOVED,
                "Verwijderd uit voorraad (${existing.quantity}x)",
            )
        }
    }

    fun observeInventoryItem(barcode: String): Flow<InventoryItemEntity?> =
        inventoryDao.observeByBarcode(barcode)

    fun observeHistory(barcode: String): Flow<List<ScanHistoryEntity>> =
        scanHistoryDao.observeHistoryForBarcode(barcode)

    fun observeScanCount(barcode: String): Flow<Int> = scanHistoryDao.observeScanCount(barcode)
}
