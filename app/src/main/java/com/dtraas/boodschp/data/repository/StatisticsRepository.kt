package com.dtraas.boodschp.data.repository

import com.dtraas.boodschp.data.local.dao.CategoryCount
import com.dtraas.boodschp.data.local.dao.InventoryDao
import com.dtraas.boodschp.data.local.dao.ScanHistoryDao
import com.dtraas.boodschp.data.local.dao.TopScannedProduct
import kotlinx.coroutines.flow.Flow

class StatisticsRepository(
    private val scanHistoryDao: ScanHistoryDao,
    private val inventoryDao: InventoryDao,
) {
    fun observeInventoryCount(): Flow<Int> = inventoryDao.observeInventoryCount()

    fun observeCategoryDistribution(): Flow<List<CategoryCount>> = inventoryDao.observeCategoryDistribution()

    fun observeTotalScanCount(): Flow<Int> = scanHistoryDao.observeTotalScanCount()

    fun observeScanCountSince(sinceMillis: Long): Flow<Int> = scanHistoryDao.observeScanCountSince(sinceMillis)

    fun observeTopScannedProducts(limit: Int = 5): Flow<List<TopScannedProduct>> =
        scanHistoryDao.observeTopScannedProducts(limit)
}
