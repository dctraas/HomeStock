package com.dtraas.boodschapbeheer.data.local.entity

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Cached catalog data for a scanned barcode, sourced from the Open Food
 * Facts API (or entered manually when a barcode is unknown). Stored at
 * `households/{householdId}/products/{barcode}` — [barcode] is the document
 * id, not a stored field.
 */
data class ProductEntity(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: String,
    val imageUrl: String?,
    val unit: String?,
    val lastFetchedAt: Long,
    val nutriScoreGrade: String? = null,
    val ingredients: String? = null,
    val nutrition: NutritionInfo? = null,
    val allergens: List<String> = emptyList(),
    val dietLabels: List<String> = emptyList(),
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "brand" to brand,
        "category" to category,
        "imageUrl" to imageUrl,
        "unit" to unit,
        "lastFetchedAt" to lastFetchedAt,
        "nutriScoreGrade" to nutriScoreGrade,
        "ingredients" to ingredients,
        "nutrition" to nutrition?.toMap(),
        "allergens" to allergens,
        "dietLabels" to dietLabels,
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): ProductEntity? {
            val name = document.getString("name") ?: return null
            @Suppress("UNCHECKED_CAST")
            val nutritionMap = document.get("nutrition") as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val allergens = document.get("allergens") as? List<String> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val dietLabels = document.get("dietLabels") as? List<String> ?: emptyList()
            return ProductEntity(
                barcode = document.id,
                name = name,
                brand = document.getString("brand"),
                category = document.getString("category") ?: "overig",
                imageUrl = document.getString("imageUrl"),
                unit = document.getString("unit"),
                lastFetchedAt = document.getLong("lastFetchedAt") ?: 0L,
                nutriScoreGrade = document.getString("nutriScoreGrade"),
                ingredients = document.getString("ingredients"),
                nutrition = NutritionInfo.fromMap(nutritionMap),
                allergens = allergens,
                dietLabels = dietLabels,
            )
        }
    }
}
