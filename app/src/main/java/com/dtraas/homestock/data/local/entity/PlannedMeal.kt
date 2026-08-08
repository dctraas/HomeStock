package com.dtraas.homestock.data.local.entity

/**
 * One entry in a [com.dtraas.homestock.data.model.MealSlot] — either a recipe picked from
 * suggestions ([recipeId] set, so tapping it can open the recipe detail screen) or a plain
 * manually-typed meal name ([recipeId] null, nothing to navigate to). A slot holds a *list*
 * of these — a household can plan more than one dish for e.g. avondeten — so [id] exists
 * purely to give each entry a stable, unique key (for list diffing and for removing exactly
 * this one entry from its slot) independent of whether it came from a recipe or was typed by
 * hand, where there's no natural unique identifier to reuse.
 */
data class PlannedMeal(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val recipeId: String? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "thumbnailUrl" to thumbnailUrl,
        "recipeId" to recipeId,
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
            )
        }
    }
}
