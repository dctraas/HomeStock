package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.remote.dto.MealDbSummary
import com.dtraas.homestock.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipesUiState(
    val isLoading: Boolean = true,
    val recipes: List<MealDbSummary> = emptyList(),
    val hasError: Boolean = false,
)

class RecipesViewModel(
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            recipeRepository.suggestRecipes()
                .onSuccess { recipes -> _uiState.update { it.copy(isLoading = false, recipes = recipes, hasError = false) } }
                .onFailure { _uiState.update { it.copy(isLoading = false, recipes = emptyList(), hasError = true) } }
        }
    }
}
