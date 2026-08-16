package com.dtraas.homestock.data.repository

import android.net.Uri
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
import com.google.firebase.storage.FirebaseStorage
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
    private val storage: FirebaseStorage,
    private val householdSession: HouseholdSession,
    private val api: OpenFoodFactsApi,
) {
    private fun productsCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("products")

    private fun customPhotoRef(householdId: String, barcode: String) =
        storage.reference.child("households/$householdId/products/$barcode/photo.jpg")

    // Open Food Facts' own data for a barcode is identical no matter which household scans it,
    // so it's cached once here (top-level, shared across every household — see firestore.rules)
    // rather than re-fetched from OFF by every household that happens to buy the same product.
    // A household's own [productsCollection] doc stays the source of truth for anything a
    // household can personalize (updateName/updateCategory/updateBrand/updateUnit) — this is
    // purely a cache to seed that doc from, never read from directly by the rest of the app.
    private fun globalProductsCollection() = firestore.collection("products")

    // Same idea as [globalProductsCollection] but for Open Food Facts' free-text search: the
    // list of candidate products for a given query string is the same for everyone, and this is
    // hit hard by the bonnetje scanner's per-line-item matching (see ReceiptScanViewModel).
    private fun searchCacheCollection() = firestore.collection("productSearchCache")

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
     * Returns the cached product for [barcode] if the household already knows it, otherwise
     * checks whether any *other* household has already resolved this exact barcode (see
     * [globalProductsCollection]) before ever asking Open Food Facts itself, and only then falls
     * back to a real OFF lookup. Never throws: network/parse failures surface as [Result.failure].
     */
    suspend fun getOrFetchProduct(barcode: String): Result<ProductEntity> {
        val householdId = householdSession.householdId.value
            ?: return Result.failure(IllegalStateException("Geen huishouden gekoppeld"))

        findCached(barcode)?.let { return Result.success(it) }

        findInGlobalCache(barcode)?.let { entity ->
            productsCollection(householdId).document(barcode).set(entity.toMap()).await()
            return Result.success(entity)
        }

        return try {
            val response = withNetworkRetry { api.getProduct(barcode) }
            val offProduct = response.product
            if (response.status != 1 || offProduct == null || offProduct.productName.isNullOrBlank()) {
                Result.failure(ProductNotFoundException(barcode))
            } else {
                val entity = mapOffProduct(barcode, offProduct)
                productsCollection(householdId).document(barcode).set(entity.toMap()).await()
                cacheGlobally(entity)
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
     * Deliberately bypasses [globalProductsCollection] on the read side (the whole point is a
     * fresh OFF fetch) but still refreshes it on write, so other households benefit from this
     * household's manual refresh too.
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
                cacheGlobally(entity)
                Result.success(entity)
            }
        } catch (e: HttpException) {
            // See getOrFetchProduct: a 404 here means "not in Open Food Facts", not "offline".
            if (e.code() == 404) Result.failure(ProductNotFoundException(barcode)) else Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Best-effort: a hiccup writing/reading the shared cache should never break the actual scan/lookup it's speeding up, so failures here are swallowed rather than propagated. */
    private suspend fun findInGlobalCache(barcode: String): ProductEntity? =
        runCatching { globalProductsCollection().document(barcode).get().await() }
            .getOrNull()
            ?.let { ProductEntity.fromDocument(it) }

    private suspend fun cacheGlobally(entity: ProductEntity) {
        runCatching { globalProductsCollection().document(entity.barcode).set(entity.toMap()).await() }
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

    suspend fun saveManualProduct(
        barcode: String,
        name: String,
        category: Category,
        brand: String? = null,
        unit: String? = null,
    ): ProductEntity {
        val householdId = householdSession.householdId.value ?: error("Geen huishouden gekoppeld")
        val entity = ProductEntity(
            barcode = barcode,
            name = name.trim(),
            brand = brand,
            category = category.storageKey,
            imageUrl = null,
            unit = unit,
            lastFetchedAt = System.currentTimeMillis(),
        )
        productsCollection(householdId).document(barcode).set(entity.toMap()).await()
        return entity
    }

    suspend fun updateCategory(barcode: String, category: Category) {
        val householdId = householdSession.householdId.value ?: return
        productsCollection(householdId).document(barcode).update("category", category.storageKey).await()
    }

    suspend fun updateName(barcode: String, name: String) {
        val householdId = householdSession.householdId.value ?: return
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        productsCollection(householdId).document(barcode).update("name", trimmed).await()
    }

    suspend fun updateBrand(barcode: String, brand: String?) {
        val householdId = householdSession.householdId.value ?: return
        productsCollection(householdId).document(barcode).update("brand", brand).await()
    }

    suspend fun updateUnit(barcode: String, unit: String?) {
        val householdId = householdSession.householdId.value ?: return
        productsCollection(householdId).document(barcode).update("unit", unit).await()
    }

    suspend fun updateLocation(barcode: String, location: String?) {
        val householdId = householdSession.householdId.value ?: return
        productsCollection(householdId).document(barcode).update("location", location).await()
    }

    /**
     * Uploads a household-picked photo for [barcode] to Firebase Storage and points the
     * product's `imageUrl` at it, overwriting whatever image was there before (an Open Food
     * Facts photo, an earlier custom upload, or nothing). A Premium feature — gated in the UI
     * (see ProductDetailScreen), not here, same pattern as every other premium gate in the app.
     */
    suspend fun uploadCustomPhoto(barcode: String, uri: Uri) {
        val householdId = householdSession.householdId.value ?: return
        val downloadUrl = customPhotoRef(householdId, barcode).putFile(uri).await().storage.downloadUrl.await().toString()
        productsCollection(householdId).document(barcode).update("imageUrl", downloadUrl).await()
    }

    /** Removes a custom photo uploaded via [uploadCustomPhoto], clearing `imageUrl` back to
     *  empty — there's no original Open Food Facts photo to fall back to since the upload
     *  overwrote it; [retryLookup] re-fetches one from OFF if the product still has a match. */
    suspend fun removeCustomPhoto(barcode: String) {
        val householdId = householdSession.householdId.value ?: return
        runCatching { customPhotoRef(householdId, barcode).delete().await() }
        productsCollection(householdId).document(barcode).update("imageUrl", null).await()
    }

    /**
     * Free-text product search, for finding something to add without scanning its barcode.
     * Returns lightweight results only — picking one still goes through [getOrFetchProduct]
     * (via its barcode) to fetch and cache the full product data. Same query text turns up the
     * same OFF results for anyone, so this checks [searchCacheCollection] (shared across
     * households, see [globalProductsCollection]'s doc) before ever calling OFF — the bonnetje
     * scanner in particular re-runs this once per receipt line item (see
     * ReceiptScanViewModel.matchItem), and a household's own shopping list repeats a lot from
     * week to week.
     */
    suspend fun searchByName(query: String): Result<List<ProductSearchResult>> {
        val cacheDocId = searchCacheDocId(query)
        findFreshSearchCache(cacheDocId)?.let { return Result.success(it) }

        return try {
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
            cacheSearchResults(cacheDocId, results)
            Result.success(results)
        } catch (e: IOException) {
            // A genuine connectivity failure (already retried once inside withNetworkRetry) —
            // this is the only case the UI should show as "Geen verbinding".
            Result.failure(e)
        } catch (e: Exception) {
            // Anything else (an HTTP error status, or a response body that didn't parse as
            // expected) isn't a connectivity problem — Open Food Facts' legacy search endpoint
            // is known to occasionally answer unusual search terms with something other than
            // the normal JSON shape. Same idea as the HTTP-404 handling in getOrFetchProduct
            // above: don't let that surface as "no connection" when connectivity is fine and
            // this particular search just didn't turn up a usable result. Deliberately not
            // cached (unlike the success path below) — a transient hiccup shouldn't get
            // permanently remembered as "this query has no results" for two weeks.
            Result.success(emptyList())
        }
    }

    /** Doc id for [query] in [searchCacheCollection] — a readable, sanitized prefix (for debugging in the Firestore console) plus a hash suffix so two different queries that sanitize the same way still land in different docs. */
    private fun searchCacheDocId(query: String): String {
        val normalized = query.trim().lowercase()
        val readablePrefix = normalized.filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(Regex("\\s+"), "_").take(80)
        return (readablePrefix.ifEmpty { "q" }) + "_" + normalized.hashCode()
    }

    private suspend fun findFreshSearchCache(docId: String): List<ProductSearchResult>? = runCatching {
        val snapshot = searchCacheCollection().document(docId).get().await()
        if (!snapshot.exists()) return@runCatching null
        val cachedAt = snapshot.getLong("cachedAt") ?: return@runCatching null
        if (System.currentTimeMillis() - cachedAt > SEARCH_CACHE_TTL_MILLIS) return@runCatching null
        @Suppress("UNCHECKED_CAST")
        val rawResults = snapshot.get("results") as? List<Map<String, Any?>> ?: return@runCatching null
        rawResults.mapNotNull { map ->
            val barcode = map["barcode"] as? String ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            ProductSearchResult(barcode = barcode, name = name, brand = map["brand"] as? String, imageUrl = map["imageUrl"] as? String)
        }
    }.getOrNull()

    private suspend fun cacheSearchResults(docId: String, results: List<ProductSearchResult>) {
        runCatching {
            searchCacheCollection().document(docId).set(
                mapOf(
                    "results" to results.map {
                        mapOf("barcode" to it.barcode, "name" to it.name, "brand" to it.brand, "imageUrl" to it.imageUrl)
                    },
                    "cachedAt" to System.currentTimeMillis(),
                ),
            ).await()
        }
    }

    companion object {
        // Search-result rankings for a given term rarely change; long enough to matter for a
        // household's recurring weekly staples, short enough that a genuinely renamed/discontinued
        // product doesn't stay wrong for too long.
        private const val SEARCH_CACHE_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000 // 14 days
    }
}

data class ProductSearchResult(
    val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
)

class ProductNotFoundException(barcode: String) : Exception("Product $barcode not found in Open Food Facts")
