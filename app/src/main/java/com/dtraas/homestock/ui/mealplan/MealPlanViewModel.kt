package com.dtraas.homestock.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealPlanUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: Map<MealSlot, PlannedMeal?> = emptyMap(),
    /** Non-null while the "pick a recipe for this slot" dialog is open. */
    val pickerSlot: MealSlot? = null,
    val isPickerLoading: Boolean = false,
    val pickerSuggestions: List<RecipeSuggestion> = emptyList(),
)

class MealPlanViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState

    // Re-launched on every date change (see changeDate) rather than a single collect over a
    // flatMapLatest-on-date flow — the date lives in plain UI state, not its own StateFlow, so
    // there's nothing to flatMapLatest from; cancelling the previous listener by hand achieves
    // the same "only one day's snapshot listener open at a time" result.
    private var planObservationJob: Job? = null

    init {
        observeCurrentDate()
    }

    private fun observeCurrentDate() {
        planObservationJob?.cancel()
        planObservationJob = viewModelScope.launch {
            mealPlanRepository.observeMealPlan(_uiState.value.date).collect { plan ->
                _uiState.update { it.copy(plan = plan) }
            }
        }
    }

    fun goToPreviousDay() = changeDate(_uiState.value.date.minusDays(1))

    fun goToNextDay() = changeDate(_uiState.value.date.plusDays(1))

    private fun changeDate(date: LocalDate) {
        _uiState.update { it.copy(date = date, plan = emptyMap()) }
        observeCurrentDate()
    }

    /** Opens the recipe picker for [slot], loading suggestions fresh every time — inventory may have changed since it was last opened. */
    fun openPicker(slot: MealSlot) {
        _uiState.update { it.copy(pickerSlot = slot, isPickerLoading = true, pickerSuggestions = emptyList()) }
        viewModelScope.launch {
            recipeRepository.suggestRecipes()
                .onSuccess { suggestions -> _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = suggestions) } }
                .onFailure { _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = emptyList()) } }
        }
    }

    fun dismissPicker() {
        _uiState.update { it.copy(pickerSlot = null, isPickerLoading = false, pickerSuggestions = emptyList()) }
    }

    fun pickMeal(suggestion: RecipeSuggestion) {
        val slot = _uiState.value.pickerSlot ?: return
        val date = _uiState.value.date
        val meal = PlannedMeal(suggestion.meal.id, suggestion.meal.name, suggestion.meal.thumbnailUrl)
        viewModelScope.launch { mealPlanRepository.setMeal(date, slot, meal) }
        dismissPicker()
    }

    fun clearSlot(slot: MealSlot) {
        val date = _uiState.value.date
        viewModelScope.launch { mealPlanRepository.clearMeal(date, slot) }
    }
}
