package com.dtraas.homestock.data.repository

/** A recipe as returned in list/search results — summary fields only, no ingredients/instructions. */
data class RecipeSummary(val id: String, val name: String, val thumbnailUrl: String?)

/**
 * Full recipe detail: ingredients + instructions, fetched once a specific recipe is opened (or
 * returned inline by a search that already includes full detail — see
 * [RecipeRepository.searchRecipesByName]/[RecipeRepository.browseAllRecipes]).
 *
 * [isAiGenerated] recipes (see [RecipeRepository.generateRecipe]) come from Claude rather than
 * the Spoonacular database — RecipeDetailScreen shows a small badge for these so it's clear
 * they're AI-invented rather than a real, tested recipe. [isCustom] recipes (see
 * [RecipeRepository.saveCustomRecipe]) are hand-entered by the household itself — like
 * [isAiGenerated] ones they're already in whatever language the household typed them in, so
 * [RecipeRepository.translatedDetailIfNeeded] skips both rather than machine-translating them.
 *
 * Spoonacular's content is always English. When the app's language isn't English,
 * [RecipeRepository.getRecipeDetail] fetches an AI translation into the separate `translatedX`
 * fields below rather than overwriting [name]/[category]/[area]/[instructions]/[ingredients] in
 * place — [RecipeRepository.matchedIngredients]/[RecipeRepository.missingIngredients] depend on
 * [ingredients] staying in English to match against the household's (Dutch-named) inventory.
 * UI code should read the `displayX` getters, which prefer the translation when present;
 * matching/functional code should keep using the plain English fields directly.
 * [translatedForLocale] records which locale the `translatedX` fields (if any) are in — null
 * means no translation has been fetched yet (or none is needed, e.g. for [isAiGenerated]
 * recipes, which Claude already generates directly in the target language).
 */
data class RecipeDetail(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val category: String?,
    val area: String?,
    val instructions: String?,
    val ingredients: List<Pair<String, String>>,
    val readyInMinutes: Int? = null,
    val isAiGenerated: Boolean = false,
    val isCustom: Boolean = false,
    val translatedForLocale: String? = null,
    val translatedName: String? = null,
    val translatedCategory: String? = null,
    val translatedArea: String? = null,
    val translatedInstructions: String? = null,
    val translatedIngredients: List<Pair<String, String>>? = null,
) {
    private val hasTranslation: Boolean get() = translatedForLocale != null
    val displayName: String get() = if (hasTranslation) translatedName ?: name else name
    val displayCategory: String? get() = if (hasTranslation) translatedCategory else category
    val displayArea: String? get() = if (hasTranslation) translatedArea else area
    val displayInstructions: String? get() = if (hasTranslation) translatedInstructions else instructions
    val displayIngredients: List<Pair<String, String>>
        get() = if (hasTranslation) translatedIngredients ?: ingredients else ingredients
}
