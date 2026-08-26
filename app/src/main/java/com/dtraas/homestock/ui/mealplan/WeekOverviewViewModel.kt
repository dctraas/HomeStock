package com.dtraas.homestock.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.RecipeRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One day's row on [WeekOverviewScreen] — [dinner] is the first avondeten entry planned for
 *  [date], same "featured" pick [MealPlanViewModel.loadDinnerDetail] uses, or null if nothing's
 *  planned yet. [matchedCount]/[totalIngredients] are only meaningful (both > 0, or both 0) when
 *  [dinner] is a real recipe still awaiting opgebruikt/weggegooid — see [WeekOverviewViewModel.load]. */
data class WeekOverviewDayRow(
    val date: LocalDate,
    val dinner: PlannedMeal?,
    val matchedCount: Int = 0,
    val totalIngredients: Int = 0,
)

/**
 * Backs [WeekOverviewScreen] — a one-time (not live) fetch of one Monday-start week's avondeten
 * plan, same [MealPlanRepository.fetchWeekPlan] the main screen's own day-strip uses, plus one
 * [RecipeRepository.getRecipeDetail]/[RecipeRepository.matchedIngredients] lookup per distinct
 * planned recipe so each row can show its own "N van M ingrediënten in huis" ring instead of
 * just the selected day's (which is all [MealPlanViewModel] keeps live). Deliberately its own
 * small ViewModel rather than reusing [MealPlanViewModel] — this screen never edits a plan, it
 * only ever reads one week of it before handing control back to MealPlanScreen (see
 * [WeekOverviewScreen]'s "tapping a row" doc).
 */
class WeekOverviewViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<WeekOverviewDayRow>>(emptyList())
    val rows: StateFlow<List<WeekOverviewDayRow>> = _rows

    fun load(weekStart: LocalDate) {
        viewModelScope.launch {
            val weekPlan = mealPlanRepository.fetchWeekPlan(weekStart)
            // Fetched once per distinct recipe rather than once per day — a household that plans
            // the same leftovers twice in a week shouldn't pay for the same detail/match lookup
            // twice.
            val recipeIds = weekPlan.values.mapNotNull { it[MealSlot.DINNER]?.firstOrNull()?.recipeId }.distinct()
            val matchByRecipeId = recipeIds.associateWith { id ->
                val detail = recipeRepository.getRecipeDetail(id).getOrNull() ?: return@associateWith 0 to 0
                val matched = recipeRepository.matchedIngredients(detail).size
                matched to detail.ingredients.size
            }
            _rows.value = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val dinner = weekPlan[date]?.get(MealSlot.DINNER)?.firstOrNull()
                val (matched, total) = dinner?.recipeId?.let { matchByRecipeId[it] } ?: (0 to 0)
                WeekOverviewDayRow(date = date, dinner = dinner, matchedCount = matched, totalIngredients = total)
            }
        }
    }
}
