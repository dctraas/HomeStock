package com.dtraas.homestock.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealPlanUiState(
    val plan: Map<DayOfWeek, PlannedMeal?> = emptyMap(),
    /** Non-null while the "pick a recipe for this day" dialog is open. */
    val pickerDay: DayOfWeek? = null,
    val isPickerLoading: Boolean = false,
    val pickerSuggestions: List<RecipeSuggestion> = emptyList(),
    val isGenerating: Boolean = false,
    /** Set once generateShoppingList() succeeds, to trigger a one-shot snackbar; see [MealPlanViewModel.consumeGeneratedCount]. */
    val generatedCount: Int? = null,
    val hasGenerateError: Boolean = false,
)

class MealPlanViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState

    init {
        viewModelScope.launch {
            mealPlanRepository.observeMealPlan().collect { plan ->
                _uiState.update { it.copy(plan = plan) }
            }
        }
    }

    /** Opens the recipe picker for [day], loading suggestions fresh every time — inventory may have changed since it was last opened. */
    fun openPicker(day: DayOfWeek) {
        _uiState.update { it.copy(pickerDay = day, isPickerLoading = true, pickerSuggestions = emptyList()) }
        viewModelScope.launch {
            recipeRepository.suggestRecipes()
                .onSuccess { suggestions -> _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = suggestions) } }
                .onFailure { _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = emptyList()) } }
        }
    }

    fun dismissPicker() {
        _uiState.update { it.copy(pickerDay = null, isPickerLoading = false, pickerSuggestions = emptyList()) }
    }

    fun pickMeal(suggestion: RecipeSuggestion) {
        val day = _uiState.value.pickerDay ?: return
        val meal = PlannedMeal(suggestion.meal.id, suggestion.meal.name, suggestion.meal.thumbnailUrl)
        viewModelScope.launch { mealPlanRepository.setMeal(day, meal) }
        dismissPicker()
    }

    fun clearDay(day: DayOfWeek) {
        viewModelScope.launch { mealPlanRepository.clearMeal(day) }
    }

    fun generateShoppingList() {
        _uiState.update { it.copy(isGenerating = true, hasGenerateError = false) }
        viewModelScope.launch {
            mealPlanRepository.generateShoppingList()
                .onSuccess { count -> _uiState.update { it.copy(isGenerating = false, generatedCount = count) } }
                .onFailure { _uiState.update { it.copy(isGenerating = false, hasGenerateError = true) } }
        }
    }

    fun consumeGeneratedCount() {
        _uiState.update { it.copy(generatedCount = null) }
    }
}
