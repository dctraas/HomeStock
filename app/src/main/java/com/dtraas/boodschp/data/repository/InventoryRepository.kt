package com.dtraas.boodschp.data.repository

import androidx.room.withTransaction
import com.dtraas.boodschp.data.local.AppDatabase
import com.dtraas.boodschp.data.local.dao.InventoryDao
import com.dtraas.boodschp.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschp.data.local.dao.ScanHistoryDao
import com.dtraas.boodschp.data.local.entity.InventoryItemEntity
import com.dtraas.boodschp.data.local.entity.ScanHistoryEntity
import com.dtraas.boodschp.data.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InventoryRepository(
    private val database: AppDatabase,
    private val inventoryDao: InventoryDao,
    private val scanHistoryDao: ScanHistoryDao,
) {
    /** Inventory items grouped by category, ordered for display (not alphabetically). */
    fun observeInventoryGroupedByCategory(): Flow<Map<Category, List<InventoryItemWithProduct>>> =
        inventoryDao.observeInventoryWithProduct().map { items ->
            items
                .groupBy { Category.fromStorageKey(it.category) }
                .toSortedMap(compareBy { it.sortOrder })
        }

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
    }

    suspend fun updateQuantity(barcode: String, quantity: Int) {
        inventoryDao.upsert(
            InventoryItemEntity(
                barcode = barcode,
                quantity = quantity.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeFromInventory(barcode: String) {
        inventoryDao.deleteByBarcode(barcode)
    }

    fun observeInventoryItem(barcode: String): Flow<InventoryItemEntity?> =
        inventoryDao.observeByBarcode(barcode)

    fun observeHistory(barcode: String): Flow<List<ScanHistoryEntity>> =
        scanHistoryDao.observeHistoryForBarcode(barcode)

    fun observeScanCount(barcode: String): Flow<Int> = scanHistoryDao.observeScanCount(barcode)
}
