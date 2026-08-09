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
 * they're AI-invented rather than a real, tested recipe.
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
)
