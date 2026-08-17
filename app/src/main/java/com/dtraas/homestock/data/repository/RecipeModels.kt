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
    // How many people [ingredients]' quantities as written feed — lets RecipeDetailScreen offer
    // portion scaling (see scaleMeasure there). Null for older cached entries from before this
    // field existed, and for any source that doesn't have a reliable serving count; the scaling
    // UI simply doesn't show in that case rather than guessing.
    val servings: Int? = null,
    val readyInMinutes: Int? = null,
    // Per serving (Spoonacular's own recipe-level nutrition, divided by its serving count) —
    // not per 100g like a product's NutritionInfo, since a whole recipe has no fixed weight.
    // Null for AI-generated/custom recipes (Claude doesn't estimate this) and for any recipe
    // Spoonacular simply has no nutrition breakdown for.
    val calories: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbohydrates: Double? = null,
    val isAiGenerated: Boolean = false,
    val isCustom: Boolean = false,
    // RecipeTag storage keys (see that enum's doc for why only favorites/custom recipes ever
    // have any) — a plain List<String> rather than List<RecipeTag> so an unrecognized/future key
    // read back from Firestore doesn't need filtering out here; RecipeTag.fromStorageKey does
    // that at the UI edge instead.
    val tags: List<String> = emptyList(),
    val translatedForLocale: String? = null,
    val translatedName: String? = null,
    val translatedCategory: String? = null,
    val translatedArea: String? = null,
    val translatedInstructions: String? = null,
    val translatedIngredients: List<Pair<String, String>>? = null,
) {
    private val hasTranslation: Boolean get() = translatedForLocale != null
    // Every translatedX field falls back to its English original when the translation call
    // didn't come back with that particular field — translatedForLocale being set only means
    // "a translation attempt happened for this locale", not "every field in it succeeded".
    // displayInstructions previously had no such fallback (unlike the others here), so a
    // translation response missing/blank "instructions" — a partial AI/network hiccup, or a
    // stale cached entry from before this fix — silently blanked out the recipe's entire
    // bereidingswijze instead of falling back to the still-intact English original.
    val displayName: String get() = if (hasTranslation) translatedName ?: name else name
    val displayCategory: String? get() = if (hasTranslation) translatedCategory ?: category else category
    val displayArea: String? get() = if (hasTranslation) translatedArea ?: area else area
    val displayInstructions: String? get() = if (hasTranslation) translatedInstructions ?: instructions else instructions
    val displayIngredients: List<Pair<String, String>>
        get() = if (hasTranslation) translatedIngredients ?: ingredients else ingredients
}
