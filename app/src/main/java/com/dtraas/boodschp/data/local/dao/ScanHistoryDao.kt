package com.dtraas.boodschp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dtraas.boodschp.data.local.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

/** A product ranked by how often it has been scanned, for the statistics screen. */
data class TopScannedProduct(
    val barcode: String,
    val name: String,
    val category: String,
    val imageUrl: String?,
    val scanCount: Int,
)

@Dao
interface ScanHistoryDao {
    @Insert
    suspend fun insert(entry: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history WHERE barcode = :barcode ORDER BY scannedAt DESC")
    fun observeHistoryForBarcode(barcode: String): Flow<List<ScanHistoryEntity>>

    @Query("SELECT COUNT(*) FROM scan_history WHERE barcode = :barcode")
    fun observeScanCount(barcode: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_history")
    fun observeTotalScanCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_history WHERE scannedAt >= :sinceMillis")
    fun observeScanCountSince(sinceMillis: Long): Flow<Int>

    @Query(
        """
        SELECT s.barcode AS barcode, p.name AS name, p.category AS category, p.imageUrl AS imageUrl, COUNT(s.id) AS scanCount
        FROM scan_history s
        INNER JOIN products p ON p.barcode = s.barcode
        GROUP BY s.barcode
        ORDER BY scanCount DESC
        LIMIT :limit
        """
    )
    fun observeTopScannedProducts(limit: Int): Flow<List<TopScannedProduct>>
}
