package com.dtraas.homestock.data.remote

import com.dtraas.homestock.data.remote.dto.MealDbFilterResponse
import com.dtraas.homestock.data.remote.dto.MealDbLookupResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Read-only client for TheMealDB's free, keyless recipe database
 * (https://www.themealdb.com/api.php). Uses the shared "1" test API key, which
 * is rate-limited and intended for small-scale/non-commercial use — matches
 * this being a Beta feature rather than a fully supported one.
 */
interface TheMealDbApi {
    @GET("filter.php")
    suspend fun filterByIngredient(@Query("i") ingredient: String): MealDbFilterResponse

    /** Recipes from a given cuisine/region, e.g. "Dutch", "French" — see RecipeRepository's language-to-area mapping. */
    @GET("filter.php")
    suspend fun filterByArea(@Query("a") area: String): MealDbFilterResponse

    @GET("lookup.php")
    suspend fun lookupMeal(@Query("i") id: String): MealDbLookupResponse

    companion object {
        const val BASE_URL = "https://www.themealdb.com/api/json/v1/1/"
    }
}
