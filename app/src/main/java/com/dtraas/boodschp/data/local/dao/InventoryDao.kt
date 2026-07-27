package com.dtraas.boodschp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dtraas.boodschp.data.local.entity.InventoryItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Flattened row combining an [InventoryItemEntity] with its cached
 * [com.dtraas.boodschp.data.local.entity.ProductEntity] fields, produced by
 * a manual join so the UI never has to stitch the two tables together.
 */
data class InventoryItemWithProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: String,
    val imageUrl: String?,
    val unit: String?,
    val quantity: Int,
    val updatedAt: Long,
)

@Dao
interface InventoryDao {
    @Query(
        """
        SELECT i.barcode AS barcode, p.name AS name, p.brand AS brand, p.category AS category,
               p.imageUrl AS imageUrl, p.unit AS unit, i.quantity AS quantity, i.updatedAt AS updatedAt
        FROM inventory_items i
        INNER JOIN products p ON p.barcode = i.barcode
        ORDER BY p.name ASC
        """
    )
    fun observeInventoryWithProduct(): Flow<List<InventoryItemWithProduct>>

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode LIMIT 1")
    fun observeByBarcode(barcode: String): Flow<InventoryItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE barcode = :barcode")
    suspend fun deleteByBarcode(barcode: String)

    @Query("SELECT COUNT(*) FROM inventory_items WHERE barcode = :barcode")
    suspend fun isInInventory(barcode: String): Int
}
