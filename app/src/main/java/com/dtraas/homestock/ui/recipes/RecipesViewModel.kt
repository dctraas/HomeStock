package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.repository.GenerateRecipeResult
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GenerateRecipeError { NO_CONNECTION, PREMIUM_REQUIRED, UNKNOWN }

data class RecipesUiState(
    val isLoading: Boolean = true,
    val recipes: List<RecipeSuggestion> = emptyList(),
    val hasError: Boolean = false,
    val excludedAllergens: Set<Allergen> = emptySet(),
    val searchQuery: String = "",
    val isGenerating: Boolean = false,
    val generateError: GenerateRecipeError? = null,
)

/**
 * Browses Spoonacular's recipe catalog by default (see [RecipeRepository.browseAllRecipes])
 * rather than only recipes matching household inventory — [search] switches to a name search
 * instead (see [RecipeRepository.searchRecipesByName]) when [RecipesUiState.searchQuery] is
 * non-blank. The inventory-based [RecipeRepository.suggestRecipes] is still used elsewhere (the
 * maaltijdplanner's "kies een recept" picker), just not here. [generateRecipe] is a separate,
 * AI-authored alternative (see [RecipeRepository.generateRecipe]) rather than a search at all.
 */
class RecipesViewModel(
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState

    /** Emits the newly generated recipe's id once [generateRecipe] succeeds — the screen navigates to RecipeDetailScreen with it. */
    private val _generatedRecipeId = MutableSharedFlow<String>()
    val generatedRecipeId: SharedFlow<String> = _generatedRecipeId

    // Remembered from the last load() call so search()/toggleAllergen()/generateRecipe() don't
    // need the caller (RecipesScreen) to keep threading the current app language through every action.
    private var languageTag: String? = null

    /** [languageTag] (e.g. "nl") drives the cuisine/region boost in RecipeRepository — see its doc. */
    fun load(languageTag: String? = null) {
        this.languageTag = languageTag
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            val state = _uiState.value
            val result = if (state.searchQuery.isNotBlank()) {
                recipeRepository.searchRecipesByName(state.searchQuery.trim(), state.excludedAllergens)
            } else {
                recipeRepository.browseAllRecipes(languageTag, state.excludedAllergens)
            }
            result
                .onSuccess { recipes -> _uiState.update { it.copy(isLoading = false, recipes = recipes, hasError = false) } }
                .onFailure { _uiState.update { it.copy(isLoading = false, recipes = emptyList(), hasError = true) } }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Runs the currently typed search query — call on the search field's IME action, not on every keystroke. */
    fun search() = load(languageTag)

    /** Clears the search field and immediately goes back to browsing everything. */
    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        load(languageTag)
    }

    /** Toggles [allergen] in/out of the exclusion filter and re-fetches (browse or search, whichever is active). */
    fun toggleAllergen(allergen: Allergen) {
        _uiState.update {
            val updated = if (allergen in it.excludedAllergens) it.excludedAllergens - allergen else it.excludedAllergens + allergen
            it.copy(excludedAllergens = updated)
        }
        load(languageTag)
    }

    /** Asks Claude to invent one recipe from the household's current inventory, optionally steered by [wish]. */
    fun generateRecipe(wish: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generateError = null) }
            when (val result = recipeRepository.generateRecipe(wish.takeIf { it.isNotBlank() }, languageTag)) {
                is GenerateRecipeResult.Success -> {
                    _uiState.update { it.copy(isGenerating = false) }
                    _generatedRecipeId.emit(result.detail.id)
                }
                GenerateRecipeResult.PremiumRequired ->
                    _uiState.update { it.copy(isGenerating = false, generateError = GenerateRecipeError.PREMIUM_REQUIRED) }
                GenerateRecipeResult.NoConnection ->
                    _uiState.update { it.copy(isGenerating = false, generateError = GenerateRecipeError.NO_CONNECTION) }
                GenerateRecipeResult.Failed ->
                    _uiState.update { it.copy(isGenerating = false, generateError = GenerateRecipeError.UNKNOWN) }
            }
        }
    }

    fun dismissGenerateError() {
        _uiState.update { it.copy(generateError = null) }
    }
}
