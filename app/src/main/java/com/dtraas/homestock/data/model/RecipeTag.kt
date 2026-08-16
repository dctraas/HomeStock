package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/**
 * A small, fixed set of household-assignable labels for *saved* recipes — favorites and custom
 * recipes (see RecipeRepository's class doc for the three recipe sources). Tagging only applies
 * to those two: they're the only recipes with a durable per-household Firestore doc to store the
 * tags on, unlike a plain Spoonacular browse/search result the household hasn't kept a copy of.
 * Stored by [storageKey] rather than enum name/ordinal, the same pattern as [Category]/[Allergen],
 * so a future reordering or rename here doesn't silently break already-saved data.
 */
enum class RecipeTag(val storageKey: String, @StringRes val labelRes: Int) {
    QUICK("quick", R.string.recipe_tag_quick),
    KID_FRIENDLY("kid_friendly", R.string.recipe_tag_kid_friendly),
    LEFTOVERS("leftovers", R.string.recipe_tag_leftovers);

    companion object {
        fun fromStorageKey(key: String): RecipeTag? = entries.find { it.storageKey == key }
    }
}
