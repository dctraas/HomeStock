package com.dtraas.boodschp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dtraas.boodschp.data.local.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

/** An activity log entry joined with the product's current name, for display. */
data class ActivityLogWithProduct(
    val id: Long,
    val barcode: String,
    val productName: String,
    val type: String,
    val detail: String,
    val timestamp: Long,
)

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(entry: ActivityLogEntity)

    @Query(
        """
        SELECT a.id AS id, a.barcode AS barcode, p.name AS productName, a.type AS type, a.detail AS detail, a.timestamp AS timestamp
        FROM activity_log a
        INNER JOIN products p ON p.barcode = a.barcode
        ORDER BY a.timestamp DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<ActivityLogWithProduct>>
}
