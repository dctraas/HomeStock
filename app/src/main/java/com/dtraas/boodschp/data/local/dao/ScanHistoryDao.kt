package com.dtraas.boodschp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dtraas.boodschp.data.local.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert
    suspend fun insert(entry: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history WHERE barcode = :barcode ORDER BY scannedAt DESC")
    fun observeHistoryForBarcode(barcode: String): Flow<List<ScanHistoryEntity>>

    @Query("SELECT COUNT(*) FROM scan_history WHERE barcode = :barcode")
    fun observeScanCount(barcode: String): Flow<Int>
}
