package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.entity.NutritionInfo
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.DietLabel
import com.dtraas.homestock.data.remote.CategoryMapper
import com.dtraas.homestock.data.remote.OpenFoodFactsApi
import com.dtraas.homestock.data.remote.dto.OffProduct
import com.dtraas.homestock.data.remote.observeSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException

@OptIn(ExperimentalCoroutinesApi::class)
class ProductRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
    private val api: OpenFoodFactsApi,
) {
    private fun productsCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("products")

    /**
     * A brief connectivity blip (Wi-Fi handing off to cellular, a slow tower, a single dropped
     * packet) throws [IOException] the very first time but usually succeeds a moment later — the
     * kind of "soms" (sometimes) failure that isn't a real "Geen verbinding" at all. One silent
     * retry after a short delay absorbs that without bothering the user; anything that fails
     * twice in a row (or a non-IOException, like a malformed response) is treated as real.
     */
    private suspend fun <T> withNetworkRetry(block: suspend () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            delay(1000)
            block()
        }

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
            val response = withNetworkRetry { api.getProduct(barcode) }
            val offProduct = response.product
            if (response.status != 1 || offProduct == null || offProduct.productName.isNullOrBlank()) {
                Result.failure(ProductNotFoundException(barcode))
            } else {
                val entity = mapOffProduct(barcode, offProduct)
                productsCollection(householdId).document(barcode).set(entity.toMap()).await()
                Result.success(entity)
            }
        } catch (e: HttpException) {
            // Open Food Facts' v2 API answers an unknown barcode with HTTP 404 rather than a
            // 200 body with status 0 — Retrofit surfaces that as an HttpException, which would
            // otherwise fall into the generic branch below and get shown as "Geen verbinding"
            // even though connectivity is fine and the barcode is simply not in their database.
            if (e.code() == 404) Result.failure(ProductNotFoundException(barcode)) else Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Re-fetches [barcode] from Open Food Facts even if it's already cached, so a product that
     * was originally entered manually (no photo/nutrition/scores) can be filled in later. Keeps
     * the existing name and category rather than overwriting them with Open Food Facts' values.
     */
    suspend fun retryLookup(barcode: String): Result<ProductEntity> {
        val householdId = householdSession.householdId.value
            ?: return Result.failure(IllegalStateException("Geen huishouden gekoppeld"))

        return try {
            val response = withNetworkRetry { api.getProduct(barcode) }
            val offProduct = response.product
            if (response.status != 1 || offProduct == null || offProduct.productName.isNullOrBlank()) {
                Result.failure(ProductNotFoundException(barcode))
            } else {
                val existing = findCached(barcode)
                val entity = mapOffProduct(barcode, offProduct, existing)
                productsCollection(householdId).document(barcode).set(entity.toMap()).await()
                Result.success(entity)
            }
        } catch (e: HttpException) {
            // See getOrFetchProduct: a 404 here means "not in Open Food Facts", not "offline".
            if (e.code() == 404) Result.failure(ProductNotFoundException(barcode)) else Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Maps an Open Food Facts response to our entity, optionally preserving [existing]'s name/category. */
    private fun mapOffProduct(barcode: String, offProduct: OffProduct, existing: ProductEntity? = null): ProductEntity {
        val category = existing?.let { Category.fromStorageKey(it.category) } ?: CategoryMapper.guessCategory(
            categoriesTags = offProduct.categoriesTags,
            categoriesText = offProduct.categories,
            productName = offProduct.productName,
        )
        val nutriments = offProduct.nutriments
        return ProductEntity(
            barcode = barcode,
            name = existing?.name?.takeIf { it.isNotBlank() } ?: offProduct.productName!!.trim(),
            brand = offProduct.brands?.substringBefore(',')?.trim(),
            category = category.storageKey,
            imageUrl = offProduct.imageUrl,
            unit = offProduct.quantity,
            lastFetchedAt = System.currentTimeMillis(),
            nutriScoreGrade = offProduct.nutriscoreGrade?.takeIf { it.isNotBlank() && it != "unknown" },
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

    /**
     * Free-text product search, for finding something to add without scanning its barcode.
     * Returns lightweight results only — picking one still goes through [getOrFetchProduct]
     * (via its barcode) to fetch and cache the full product data.
     */
    suspend fun searchByName(query: String): Result<List<ProductSearchResult>> = try {
        val response = withNetworkRetry { api.searchProducts(searchTerms = query) }
        val results = response.products.orEmpty().mapNotNull { product ->
            val barcode = product.code?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = product.productName?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ProductSearchResult(
                barcode = barcode,
                name = name,
                brand = product.brands?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = product.imageUrl,
            )
        }
        Result.success(results)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

data class ProductSearchResult(
    val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
)

class ProductNotFoundException(barcode: String) : Exception("Product $barcode not found in Open Food Facts")
