package com.dtraas.boodschapbeheer.data.remote

import com.dtraas.boodschapbeheer.data.remote.dto.OffProductResponse
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
            "product_name,brands,categories_tags,categories,image_front_small_url,quantity,status",
    ): OffProductResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
    }
}
