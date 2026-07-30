package com.dtraas.boodschapbeheer.data.repository

import com.dtraas.boodschapbeheer.data.local.entity.NutritionInfo
import com.dtraas.boodschapbeheer.data.local.entity.ProductEntity
import com.dtraas.boodschapbeheer.data.model.Allergen
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.DietLabel
import com.dtraas.boodschapbeheer.data.remote.CategoryMapper
import com.dtraas.boodschapbeheer.data.remote.OpenFoodFactsApi
import com.dtraas.boodschapbeheer.data.remote.observeSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class ProductRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
    private val api: OpenFoodFactsApi,
) {
    private fun productsCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("products")

    fun observeProduct(barcode: String): Flow<ProductEntity?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                productsCollection(householdId).document(barcode).observeSnapshot()
                    .map { ProductEntity.fromDocument(it) }
            }
        }

    /** Returns the cached product for [barcode], or null if we've never seen it before. */
    suspend fun findCached(barcode: String): ProductEntity? {
        val householdId = householdSession.householdId.value ?: return null
        val snapshot = productsCollection(householdId).document(barcode).get().await()
        return ProductEntity.fromDocument(snapshot)
    }

    /**
     * Returns the cached product for [barcode] if the household already knows it,
     * otherwise looks it up via Open Food Facts and caches the result for everyone
     * in the household. Never throws: network/parse failures surface as [Result.failure].
     */
    suspend fun getOrFetchProduct(barcode: String): Result<ProductEntity> {
        val householdId = householdSession.householdId.value
            ?: return Result.failure(IllegalStateException("Geen huishouden gekoppeld"))

        findCached(barcode)?.let { return Result.success(it) }

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
                val nutriments = offProduct.nutriments
                val entity = ProductEntity(
                    barcode = barcode,
                    name = offProduct.productName.trim(),
                    brand = offProduct.brands?.substringBefore(',')?.trim(),
                    category = category.storageKey,
                    imageUrl = offProduct.imageUrl,
                    unit = offProduct.quantity,
                    lastFetchedAt = System.currentTimeMillis(),
                    nutriScoreGrade = offProduct.nutriscoreGrade?.takeIf { it.isNotBlank() && it != "unknown" },
                    ecoScoreGrade = offProduct.ecoscoreGrade?.takeIf { it.isNotBlank() && it != "unknown" && it != "not-applicable" },
                    ingredients = offProduct.ingredientsText?.trim()?.takeIf { it.isNotEmpty() },
                    nutrition = nutriments?.let {
                        NutritionInfo(
                            energyKcal100g = it.energyKcal100g,
                            fat100g = it.fat100g,
                            saturatedFat100g = it.saturatedFat100g,
                            carbohydrates100g = it.carbohydrates100g,
                            sugars100g = it.sugars100g,
                            fiber100g = it.fiber100g,
                            proteins100g = it.proteins100g,
                            salt100g = it.salt100g,
                        ).takeIf { info -> !info.isEmpty }
                    },
                    allergens = Allergen.fromTags(offProduct.allergensTags.orEmpty()).map { it.name },
                    dietLabels = DietLabel.fromTags(offProduct.labelsTags.orEmpty()).map { it.name },
                )
                productsCollection(householdId).document(barcode).set(entity.toMap()).await()
                Result.success(entity)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveManualProduct(barcode: String, name: String, category: Category): ProductEntity {
        val householdId = householdSession.householdId.value ?: error("Geen huishouden gekoppeld")
        val entity = ProductEntity(
            barcode = barcode,
            name = name.trim(),
            brand = null,
            category = category.storageKey,
            imageUrl = null,
            unit = null,
            lastFetchedAt = System.currentTimeMillis(),
        )
        productsCollection(householdId).document(barcode).set(entity.toMap()).await()
        return entity
    }

    suspend fun updateCategory(barcode: String, category: Category) {
        val householdId = householdSession.householdId.value ?: return
        productsCollection(householdId).document(barcode).update("category", category.storageKey).await()
    }
}

class ProductNotFoundException(barcode: String) : Exception("Product $barcode not found in Open Food Facts")
