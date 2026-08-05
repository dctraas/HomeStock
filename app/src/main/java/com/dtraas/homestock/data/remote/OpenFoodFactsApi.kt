package com.dtraas.homestock.data.remote

import com.dtraas.homestock.data.remote.dto.OffProductResponse
import com.dtraas.homestock.data.remote.dto.OffSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Read-only client for the free, keyless Open Food Facts product database.
 * https://world.openfoodfacts.org/data
 */
interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String =
            "product_name,brands,categories_tags,categories,image_front_small_url,quantity,status," +
                "nutriscore_grade,ingredients_text,nutriments,allergens_tags,labels_tags",
    ): OffProductResponse

    /** Free-text product search, for finding a product by name instead of scanning its barcode. */
    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") searchTerms: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("fields") fields: String = "code,product_name,brands,image_front_small_url",
    ): OffSearchResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
    }
}
