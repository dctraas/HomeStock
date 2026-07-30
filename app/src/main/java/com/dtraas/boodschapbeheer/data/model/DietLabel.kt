package com.dtraas.boodschapbeheer.data.model

import androidx.annotation.StringRes
import com.dtraas.boodschapbeheer.R

/**
 * A curated set of common diet/certification labels, matched against Open
 * Food Facts' `labels_tags`. OFF's label taxonomy is huge and includes a lot
 * of noise (recycling marks, regional certification codes); a whitelist of
 * the labels people actually care about keeps this readable and translatable.
 */
enum class DietLabel(val tags: List<String>, @StringRes val labelRes: Int) {
    VEGAN(listOf("en:vegan"), R.string.diet_label_vegan),
    VEGETARIAN(listOf("en:vegetarian"), R.string.diet_label_vegetarian),
    GLUTEN_FREE(listOf("en:gluten-free"), R.string.diet_label_gluten_free),
    LACTOSE_FREE(listOf("en:no-lactose", "en:lactose-free"), R.string.diet_label_lactose_free),
    ORGANIC(listOf("en:organic"), R.string.diet_label_organic),
    PALM_OIL_FREE(listOf("en:palm-oil-free"), R.string.diet_label_palm_oil_free),
    HALAL(listOf("en:halal"), R.string.diet_label_halal),
    KOSHER(listOf("en:kosher"), R.string.diet_label_kosher);

    companion object {
        fun fromTags(tags: List<String>): List<DietLabel> {
            val tagSet = tags.toSet()
            return entries.filter { entry -> entry.tags.any { it in tagSet } }
        }
    }
}
