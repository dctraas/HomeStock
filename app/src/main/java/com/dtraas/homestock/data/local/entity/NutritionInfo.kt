package com.dtraas.homestock.data.local.entity

/** Nutritional values per 100g/100ml, sourced from Open Food Facts. */
data class NutritionInfo(
    val energyKcal100g: Double? = null,
    val fat100g: Double? = null,
    val saturatedFat100g: Double? = null,
    val carbohydrates100g: Double? = null,
    val sugars100g: Double? = null,
    val fiber100g: Double? = null,
    val proteins100g: Double? = null,
    val salt100g: Double? = null,
) {
    val isEmpty: Boolean
        get() = listOfNotNull(
            energyKcal100g, fat100g, saturatedFat100g, carbohydrates100g,
            sugars100g, fiber100g, proteins100g, salt100g,
        ).isEmpty()

    fun toMap(): Map<String, Any?> = mapOf(
        "energyKcal100g" to energyKcal100g,
        "fat100g" to fat100g,
        "saturatedFat100g" to saturatedFat100g,
        "carbohydrates100g" to carbohydrates100g,
        "sugars100g" to sugars100g,
        "fiber100g" to fiber100g,
        "proteins100g" to proteins100g,
        "salt100g" to salt100g,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>?): NutritionInfo? {
            if (map == null) return null
            val info = NutritionInfo(
                energyKcal100g = (map["energyKcal100g"] as? Number)?.toDouble(),
                fat100g = (map["fat100g"] as? Number)?.toDouble(),
                saturatedFat100g = (map["saturatedFat100g"] as? Number)?.toDouble(),
                carbohydrates100g = (map["carbohydrates100g"] as? Number)?.toDouble(),
                sugars100g = (map["sugars100g"] as? Number)?.toDouble(),
                fiber100g = (map["fiber100g"] as? Number)?.toDouble(),
                proteins100g = (map["proteins100g"] as? Number)?.toDouble(),
                salt100g = (map["salt100g"] as? Number)?.toDouble(),
            )
            return if (info.isEmpty) null else info
        }
    }
}
