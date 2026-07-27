package com.dtraas.boodschp.data.repository

import com.dtraas.boodschp.data.local.dao.ShoppingListDao
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschp.data.model.Category
import kotlinx.coroutines.flow.Flow

class ShoppingListRepository(
    private val shoppingListDao: ShoppingListDao,
) {
    fun observeShoppingList(): Flow<List<ShoppingListItemEntity>> = shoppingListDao.observeAll()

    suspend fun addItem(name: String, category: Category, quantity: Int, barcode: String? = null) {
        shoppingListDao.insert(
            ShoppingListItemEntity(
                barcode = barcode,
                name = name.trim(),
                category = category.storageKey,
                quantity = quantity.coerceAtLeast(1),
                isChecked = false,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setChecked(id: Long, checked: Boolean) = shoppingListDao.setChecked(id, checked)

    suspend fun removeItem(id: Long) = shoppingListDao.deleteById(id)

    suspend fun clearChecked() = shoppingListDao.deleteChecked()
}
