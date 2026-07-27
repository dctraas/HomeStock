package com.dtraas.boodschp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dtraas.boodschp.data.local.dao.ActivityLogDao
import com.dtraas.boodschp.data.local.dao.InventoryDao
import com.dtraas.boodschp.data.local.dao.ProductDao
import com.dtraas.boodschp.data.local.dao.ScanHistoryDao
import com.dtraas.boodschp.data.local.dao.ShoppingListDao
import com.dtraas.boodschp.data.local.entity.ActivityLogEntity
import com.dtraas.boodschp.data.local.entity.InventoryItemEntity
import com.dtraas.boodschp.data.local.entity.ProductEntity
import com.dtraas.boodschp.data.local.entity.ScanHistoryEntity
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity

@Database(
    entities = [
        ProductEntity::class,
        InventoryItemEntity::class,
        ScanHistoryEntity::class,
        ShoppingListItemEntity::class,
        ActivityLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        const val DATABASE_NAME = "boodschp.db"
    }
}
