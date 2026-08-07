package com.dtraas.homestock.data.remote

import com.dtraas.homestock.data.remote.dto.MealDbCategoriesResponse
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

    /** Recipes in a given category, e.g. "Dessert" — see RecipeRepository.browseAllRecipes, which enumerates every category to approximate "all recipes" (TheMealDB has no single "list everything" endpoint). */
    @GET("filter.php")
    suspend fun filterByCategory(@Query("c") category: String): MealDbFilterResponse

    /** Every category TheMealDB has, e.g. "Beef", "Dessert", "Vegetarian". */
    @GET("categories.php")
    suspend fun listCategories(): MealDbCategoriesResponse

    /** Free-text search by (partial) recipe name — unlike the filter.php family, this returns full recipe details, not just summaries. */
    @GET("search.php")
    suspend fun searchByName(@Query("s") query: String): MealDbLookupResponse

    @GET("lookup.php")
    suspend fun lookupMeal(@Query("i") id: String): MealDbLookupResponse

    companion object {
        const val BASE_URL = "https://www.themealdb.com/api/json/v1/1/"
    }
}
