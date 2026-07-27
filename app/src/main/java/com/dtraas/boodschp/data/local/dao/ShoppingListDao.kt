package com.dtraas.boodschp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_list_items ORDER BY isChecked ASC, addedAt DESC")
    fun observeAll(): Flow<List<ShoppingListItemEntity>>

    @Insert
    suspend fun insert(item: ShoppingListItemEntity)

    @Query("UPDATE shopping_list_items SET isChecked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM shopping_list_items WHERE isChecked = 1")
    suspend fun deleteChecked()
}
