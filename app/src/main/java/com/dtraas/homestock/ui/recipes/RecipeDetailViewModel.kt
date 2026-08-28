package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.model.RecipeTag
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.data.repository.RecipeRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    // Defaults to the household's own member count once loaded (see load()), not the recipe's
    // own [RecipeDetail.servings] — a 4-serving recipe opened by a 2-person household already
    // shows halved amounts. Null (rather than some made-up default like 4) whenever the recipe
    // has no serving count at all, so RecipeDetailScreen knows to hide the stepper entirely.
    val targetServings: Int? = null,
    // The soonest upcoming date this recipe is already planned for, if any — see
    // [MealPlanRepository.findUpcomingPlan] — backs the hero's "MAANDAG GEPLAND" badge.
    val plannedDate: LocalDate? = null,
    val showPlanSheet: Boolean = false,
    val isPlanning: Boolean = false,
)

class RecipeDetailViewModel(
    private val mealId: String,
    private val languageTag: String?,
    private val recipeRepository: RecipeRepository,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val mealPlanRepository: MealPlanRepository,
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
                    // Default the stepper to "how many of us are there" rather than the
                    // recipe's own original serving count — a 4-serving recipe opened by a
                    // 2-person household should already show halved amounts, not require an
                    // extra manual adjustment every single time. Only when the recipe actually
                    // has a serving count to scale from at all; falls back to that original
                    // count if the household size can't be read for some reason (e.g. no
                    // household, momentary read failure) rather than leaving it null.
                    val householdSize = runCatching { householdMembersRepository.observeMemberCount().first() }.getOrNull()
                    val defaultServings = detail.servings?.let { original ->
                        householdSize?.coerceAtLeast(1) ?: original
                    }
                    val plannedDate = runCatching { mealPlanRepository.findUpcomingPlan(detail.id) }.getOrNull()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            matchedIngredients = matched,
                            hasError = false,
                            targetServings = defaultServings,
                            plannedDate = plannedDate,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                }
        }
    }

    fun requestPlan() = _uiState.update { it.copy(showPlanSheet = true) }
    fun dismissPlanSheet() = _uiState.update { it.copy(showPlanSheet = false) }

    /** "Nog een keer inplannen" — same [PlannedMeal] shape MealPlanViewModel.pickMeal builds when
     *  planning a recipe from the picker, just triggered from here instead. */
    fun planForDate(date: LocalDate, slot: MealSlot) {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPlanning = true) }
            mealPlanRepository.addMeal(
                date = date,
                slot = slot,
                meal = PlannedMeal(id = detail.id, name = detail.displayName, thumbnailUrl = detail.thumbnailUrl, recipeId = detail.id),
            )
            val plannedDate = runCatching { mealPlanRepository.findUpcomingPlan(detail.id) }.getOrNull()
            _uiState.update { it.copy(isPlanning = false, showPlanSheet = false, plannedDate = plannedDate) }
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

    /**
     * Adds a free-text label the household typed themselves — a no-op for a blank label, one
     * that collides with a now-retired preset tag's own storage key (case-insensitively, so it
     * can't silently resurrect one — see [RecipeTag]'s doc), or an exact duplicate (also
     * case-insensitively) of a custom label already on the recipe. A no-op if the recipe isn't a
     * custom recipe and isn't (yet) a favorite either, since [RecipeRepository.setRecipeTags] has
     * nowhere durable to write it in that case (see that function's doc) — RecipeDetailScreen
     * only shows the tag editor at all when one of those is true, so this guard is a safety net,
     * not the primary gate.
     */
    fun addCustomTag(label: String) {
        val detail = _uiState.value.detail ?: return
        val isFavorite = _uiState.value.isFavorite
        if (!detail.isCustom && !isFavorite) return
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        if (RecipeTag.entries.any { it.storageKey.equals(trimmed, ignoreCase = true) }) return
        if (detail.tags.any { it.equals(trimmed, ignoreCase = true) }) return
        val updatedTags = detail.tags + trimmed
        viewModelScope.launch {
            recipeRepository.setRecipeTags(detail, updatedTags, isFavorite)
                .onSuccess { updated -> _uiState.update { it.copy(detail = updated) } }
        }
    }

    /** Removes a previously added custom label. Same durable-copy gate as [addCustomTag]. */
    fun removeCustomTag(label: String) {
        val detail = _uiState.value.detail ?: return
        val isFavorite = _uiState.value.isFavorite
        if (!detail.isCustom && !isFavorite) return
        val updatedTags = detail.tags - label
        viewModelScope.launch {
            recipeRepository.setRecipeTags(detail, updatedTags, isFavorite)
                .onSuccess { updated -> _uiState.update { it.copy(detail = updated) } }
        }
    }
}
