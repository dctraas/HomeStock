package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/**
 * Retired: these 3 fixed preset labels used to be offered alongside per-recipe custom labels on
 * every tag editor/filter (RecipeDetailScreen, CustomRecipeEditScreen, RecipesScreen's Favorieten/
 * Eigen recepten filter row), but were removed from all of that UI per explicit request — only
 * free-text custom labels remain there now. This enum itself stays only as a lookup
 * ([fromStorageKey]) so those screens can still recognize and quietly drop a legacy "quick"/
 * "kid_friendly"/"leftovers" storage key a recipe was tagged with before the removal, instead of
 * it resurfacing as a garbled custom-looking chip.
 */
enum class RecipeTag(val storageKey: String, @StringRes val labelRes: Int) {
    QUICK("quick", R.string.recipe_tag_quick),
    KID_FRIENDLY("kid_friendly", R.string.recipe_tag_kid_friendly),
    LEFTOVERS("leftovers", R.string.recipe_tag_leftovers);

    companion object {
        fun fromStorageKey(key: String): RecipeTag? = entries.find { it.storageKey == key }
    }
}
