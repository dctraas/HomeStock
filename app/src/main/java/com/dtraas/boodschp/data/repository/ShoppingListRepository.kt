package com.dtraas.boodschp.data.repository

import com.dtraas.boodschp.data.local.dao.ShoppingListDao
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.model.Store
import kotlinx.coroutines.flow.Flow

class ShoppingListRepository(
    private val shoppingListDao: ShoppingListDao,
) {
    fun observeShoppingList(): Flow<List<ShoppingListItemEntity>> = shoppingListDao.observeAll()

    suspend fun addItem(
        name: String,
        category: Category,
        store: Store,
        quantity: Int,
        barcode: String? = null,
        imageUrl: String? = null,
    ) {
        shoppingListDao.insert(
            ShoppingListItemEntity(
                barcode = barcode,
                name = name.trim(),
                category = category.storageKey,
                store = store.storageKey,
                imageUrl = imageUrl,
                quantity = quantity.coerceAtLeast(1),
                isChecked = false,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateItem(item: ShoppingListItemEntity) {
        shoppingListDao.update(item.copy(name = item.name.trim(), quantity = item.quantity.coerceAtLeast(1)))
    }

    suspend fun setChecked(id: Long, checked: Boolean) = shoppingListDao.setChecked(id, checked)

    suspend fun removeItem(id: Long) = shoppingListDao.deleteById(id)

    suspend fun clearChecked() = shoppingListDao.deleteChecked()
}
