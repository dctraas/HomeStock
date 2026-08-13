package com.dtraas.homestock.data.local.entity

/**
 * One entry in a [com.dtraas.homestock.data.model.MealSlot] — one of three kinds: a recipe
 * picked from suggestions ([recipeId] set, so tapping it can open the recipe detail screen), a
 * product added via the "Product" flow ([isProduct] true — [productBarcode] additionally set
 * when it matched the household's voorraad, so tapping it can open that product's detail
 * screen; null when it didn't, e.g. still worth a "toevoegen aan boodschappenlijst" offer since
 * it's a product the household doesn't have yet), or a plain manually-typed name from the
 * "Recept" flow (neither [recipeId] nor [isProduct] set, nothing to navigate to or buy). A slot
 * holds a *list* of these — a household can plan more than one dish for e.g. avondeten — so [id]
 * exists purely to give each entry a stable, unique key (for list diffing and for removing
 * exactly this one entry from its slot) independent of which kind it is, where there's no
 * natural unique identifier to reuse.
 */
data class PlannedMeal(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val recipeId: String? = null,
    val productBarcode: String? = null,
    val isProduct: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "thumbnailUrl" to thumbnailUrl,
        "recipeId" to recipeId,
        "productBarcode" to productBarcode,
        "isProduct" to isProduct,
    )

    companion object {
        fun fromMap(map: Map<*, *>?): PlannedMeal? {
            if (map == null) return null
            val id = map["id"] as? String ?: return null
            val name = map["name"] as? String ?: return null
            return PlannedMeal(
                id = id,
                name = name,
                thumbnailUrl = map["thumbnailUrl"] as? String,
                recipeId = map["recipeId"] as? String,
                productBarcode = map["productBarcode"] as? String,
                isProduct = map["isProduct"] as? Boolean ?: false,
            )
        }
    }
}
