package com.dtraas.boodschapbeheer.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response shape of the Open Food Facts "get product by barcode" endpoint. */
data class OffProductResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("product") val product: OffProduct?,
)

data class OffProduct(
    @SerializedName("product_name") val productName: String?,
    @SerializedName("brands") val brands: String?,
    @SerializedName("categories_tags") val categoriesTags: List<String>?,
    @SerializedName("categories") val categories: String?,
    @SerializedName("image_front_small_url") val imageUrl: String?,
    @SerializedName("quantity") val quantity: String?,
    @SerializedName("nutriscore_grade") val nutriscoreGrade: String?,
    @SerializedName("ingredients_text") val ingredientsText: String?,
    @SerializedName("nutriments") val nutriments: OffNutriments?,
    @SerializedName("allergens_tags") val allergensTags: List<String>?,
    @SerializedName("labels_tags") val labelsTags: List<String>?,
)

/** Nutritional values per 100g/100ml, as reported by Open Food Facts. */
data class OffNutriments(
    @SerializedName("energy-kcal_100g") val energyKcal100g: Double?,
    @SerializedName("fat_100g") val fat100g: Double?,
    @SerializedName("saturated-fat_100g") val saturatedFat100g: Double?,
    @SerializedName("carbohydrates_100g") val carbohydrates100g: Double?,
    @SerializedName("sugars_100g") val sugars100g: Double?,
    @SerializedName("fiber_100g") val fiber100g: Double?,
    @SerializedName("proteins_100g") val proteins100g: Double?,
    @SerializedName("salt_100g") val salt100g: Double?,
)
