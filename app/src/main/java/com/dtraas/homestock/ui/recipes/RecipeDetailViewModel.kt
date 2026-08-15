package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val isLoading: Boolean = true,
    val detail: RecipeDetail? = null,
    val matchedIngredients: Set<String> = emptySet(),
    val hasError: Boolean = false,
    val addedToShoppingList: Boolean = false,
    val isFavorite: Boolean = false,
    // Portion scaling — how many people the shown ingredient amounts should feed right now.
    // Starts at the recipe's own [RecipeDetail.servings] once loaded; null (rather than some
    // default like 4) whenever the recipe has no serving count at all, so RecipeDetailScreen
    // knows to hide the stepper entirely instead of scaling against a made-up baseline.
    val targetServings: Int? = null,
)

class RecipeDetailViewModel(
    private val mealId: String,
    private val languageTag: String?,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeDetailUiState())
    val uiState: StateFlow<RecipeDetailUiState> = _uiState

    init {
        load()
        // A separate collector rather than folded into load(): favorite state can change (from
        // this screen's own toggle, or another device in the household) without needing to
        // re-fetch/re-translate the whole recipe detail again.
        viewModelScope.launch {
            recipeRepository.observeFavoriteIds().collectLatest { ids ->
                _uiState.update { it.copy(isFavorite = mealId in ids) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            recipeRepository.getRecipeDetail(mealId, languageTag)
                .onSuccess { detail ->
                    val matched = recipeRepository.matchedIngredients(detail)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            matchedIngredients = matched,
                            hasError = false,
                            targetServings = detail.servings,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                }
        }
    }

    /** Adjusts the ingredient list's scaling target — never below 1 (a 0- or negative-serving
     *  recipe makes no sense) and only meaningful while [RecipeDetailUiState.detail] actually
     *  has a serving count, which is what gates the stepper being shown at all. */
    fun setTargetServings(servings: Int) {
        _uiState.update { it.copy(targetServings = servings.coerceAtLeast(1)) }
    }

    fun addMissingIngredientsToShoppingList() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            recipeRepository.addMissingIngredientsToShoppingList(detail)
            _uiState.update { it.copy(addedToShoppingList = true) }
        }
    }

    /** Bookmarks/un-bookmarks the currently loaded recipe — see [RecipeRepository.addFavorite]. */
    fun toggleFavorite() {
        val detail = _uiState.value.detail ?: return
        val currentlyFavorite = _uiState.value.isFavorite
        viewModelScope.launch {
            if (currentlyFavorite) recipeRepository.removeFavorite(detail.id) else recipeRepository.addFavorite(detail)
        }
    }
}
