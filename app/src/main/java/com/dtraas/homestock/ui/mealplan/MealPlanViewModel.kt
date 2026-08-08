package com.dtraas.homestock.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealPlanUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: Map<MealSlot, List<PlannedMeal>> = emptyMap(),
    /** Non-null while the "add a meal for this slot" dialog is open — offers both a recipe picker and manual entry. */
    val pickerSlot: MealSlot? = null,
    val manualEntryText: String = "",
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

    /** Opens the "add a meal" dialog for [slot] — recipe suggestions load fresh every time, inventory may have changed since it was last opened. */
    fun openPicker(slot: MealSlot) {
        _uiState.update {
            it.copy(pickerSlot = slot, manualEntryText = "", isPickerLoading = true, pickerSuggestions = emptyList())
        }
        viewModelScope.launch {
            recipeRepository.suggestRecipes()
                .onSuccess { suggestions -> _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = suggestions) } }
                .onFailure { _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = emptyList()) } }
        }
    }

    fun dismissPicker() {
        _uiState.update { it.copy(pickerSlot = null, manualEntryText = "", isPickerLoading = false, pickerSuggestions = emptyList()) }
    }

    fun onManualEntryTextChange(text: String) {
        _uiState.update { it.copy(manualEntryText = text) }
    }

    fun pickMeal(suggestion: RecipeSuggestion) {
        val slot = _uiState.value.pickerSlot ?: return
        val date = _uiState.value.date
        val meal = PlannedMeal(
            id = suggestion.meal.id,
            name = suggestion.meal.name,
            thumbnailUrl = suggestion.meal.thumbnailUrl,
            recipeId = suggestion.meal.id,
        )
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
        dismissPicker()
    }

    /** Adds the currently-typed [MealPlanUiState.manualEntryText] as a plain (non-recipe) meal — a no-op if it's blank. */
    fun addManualMeal() {
        val slot = _uiState.value.pickerSlot ?: return
        val name = _uiState.value.manualEntryText.trim()
        if (name.isEmpty()) return
        val date = _uiState.value.date
        val meal = PlannedMeal(id = UUID.randomUUID().toString(), name = name)
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
        dismissPicker()
    }

    fun removeMeal(slot: MealSlot, meal: PlannedMeal) {
        val date = _uiState.value.date
        viewModelScope.launch { mealPlanRepository.removeMeal(date, slot, meal) }
    }
}
