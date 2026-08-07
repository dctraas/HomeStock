package com.dtraas.homestock.data.local.entity

/** A recipe assigned to a day in the household's weekmenu — see [com.dtraas.homestock.data.repository.MealPlanRepository]. */
data class PlannedMeal(
    val mealId: String,
    val name: String,
    val thumbnailUrl: String?,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "mealId" to mealId,
        "name" to name,
        "thumbnailUrl" to thumbnailUrl,
    )

    companion object {
        fun fromMap(map: Map<*, *>?): PlannedMeal? {
            if (map == null) return null
            val mealId = map["mealId"] as? String ?: return null
            val name = map["name"] as? String ?: return null
            return PlannedMeal(mealId = mealId, name = name, thumbnailUrl = map["thumbnailUrl"] as? String)
        }
    }
}
