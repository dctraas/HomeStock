package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.remote.dto.MealDbDetail
import com.dtraas.homestock.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val isLoading: Boolean = true,
    val detail: MealDbDetail? = null,
    val matchedIngredients: Set<String> = emptySet(),
    val hasError: Boolean = false,
    val addedToShoppingList: Boolean = false,
)

class RecipeDetailViewModel(
    private val mealId: String,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeDetailUiState())
    val uiState: StateFlow<RecipeDetailUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            recipeRepository.getRecipeDetail(mealId)
                .onSuccess { detail ->
                    val matched = recipeRepository.matchedIngredients(detail)
                    _uiState.update { it.copy(isLoading = false, detail = detail, matchedIngredients = matched, hasError = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                }
        }
    }

    fun addMissingIngredientsToShoppingList() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            recipeRepository.addMissingIngredientsToShoppingList(detail)
            _uiState.update { it.copy(addedToShoppingList = true) }
        }
    }
}
