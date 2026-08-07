package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipesUiState(
    val isLoading: Boolean = true,
    val recipes: List<RecipeSuggestion> = emptyList(),
    val hasError: Boolean = false,
    val excludedAllergens: Set<Allergen> = emptySet(),
)

class RecipesViewModel(
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState

    /** [languageTag] (e.g. "nl") drives the cuisine/region boost in RecipeRepository.suggestRecipes — see its doc. */
    fun load(languageTag: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            recipeRepository.suggestRecipes(excludedAllergens = _uiState.value.excludedAllergens, languageTag = languageTag)
                .onSuccess { recipes -> _uiState.update { it.copy(isLoading = false, recipes = recipes, hasError = false) } }
                .onFailure { _uiState.update { it.copy(isLoading = false, recipes = emptyList(), hasError = true) } }
        }
    }

    /** Toggles [allergen] in/out of the exclusion filter and re-fetches with [languageTag] carried over. */
    fun toggleAllergen(allergen: Allergen, languageTag: String?) {
        _uiState.update {
            val updated = if (allergen in it.excludedAllergens) it.excludedAllergens - allergen else it.excludedAllergens + allergen
            it.copy(excludedAllergens = updated)
        }
        load(languageTag)
    }
}
