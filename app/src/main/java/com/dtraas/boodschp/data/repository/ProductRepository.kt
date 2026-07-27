package com.dtraas.boodschp.data.repository

import com.dtraas.boodschp.data.local.dao.ProductDao
import com.dtraas.boodschp.data.local.entity.ProductEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.remote.CategoryMapper
import com.dtraas.boodschp.data.remote.OpenFoodFactsApi
import kotlinx.coroutines.flow.Flow

class ProductRepository(
    private val productDao: ProductDao,
    private val api: OpenFoodFactsApi,
) {
    fun observeProduct(barcode: String): Flow<ProductEntity?> = productDao.observeByBarcode(barcode)

    /** Returns the cached product for [barcode], or null if we've never seen it before. */
    suspend fun findCached(barcode: String): ProductEntity? = productDao.findByBarcode(barcode)

    /**
     * Returns the cached product for [barcode] if we already know it,
     * otherwise looks it up via Open Food Facts and caches the result.
     * Never throws: network/parse failures surface as [Result.failure].
     */
    suspend fun getOrFetchProduct(barcode: String): Result<ProductEntity> {
        productDao.findByBarcode(barcode)?.let { return Result.success(it) }

        return try {
            val response = api.getProduct(barcode)
            val offProduct = response.product
            if (response.status != 1 || offProduct == null || offProduct.productName.isNullOrBlank()) {
                Result.failure(ProductNotFoundException(barcode))
            } else {
                val category = CategoryMapper.guessCategory(
                    categoriesTags = offProduct.categoriesTags,
                    categoriesText = offProduct.categories,
                    productName = offProduct.productName,
                )
                val entity = ProductEntity(
                    barcode = barcode,
                    name = offProduct.productName.trim(),
                    brand = offProduct.brands?.substringBefore(',')?.trim(),
                    category = category.storageKey,
                    imageUrl = offProduct.imageUrl,
                    unit = offProduct.quantity,
                    lastFetchedAt = System.currentTimeMillis(),
                )
                productDao.upsert(entity)
                Result.success(entity)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveManualProduct(barcode: String, name: String, category: Category): ProductEntity {
        val entity = ProductEntity(
            barcode = barcode,
            name = name.trim(),
            brand = null,
            category = category.storageKey,
            imageUrl = null,
            unit = null,
            lastFetchedAt = System.currentTimeMillis(),
        )
        productDao.upsert(entity)
        return entity
    }

    suspend fun updateCategory(barcode: String, category: Category) {
        productDao.updateCategory(barcode, category.storageKey)
    }
}

class ProductNotFoundException(barcode: String) : Exception("Product $barcode not found in Open Food Facts")
