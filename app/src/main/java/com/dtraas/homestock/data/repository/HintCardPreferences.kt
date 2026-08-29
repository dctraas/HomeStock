package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-device collapsed/expanded state for the "hint" cards pinned above a screen's own scrollable
 * content — "Eerst opmaken" (Voorraad), "Kook wat je hebt" (Recepten), and the missing-
 * ingredients bar (Maaltijden). All three take up real space above the thing a household actually
 * came to that screen for; collapsing one is a persisted per-device choice (mirrors
 * [InventoryPreferences]/[ThemePreferences]'s own SharedPreferences pattern) rather than a
 * one-time "dismiss forever" — the collapsed row stays right there and re-expands with the same
 * tap that collapsed it, so nothing is ever permanently hidden or needs hunting down in a
 * settings screen to get back.
 */
class HintCardPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _inventoryExpiringSoonCollapsed = MutableStateFlow(prefs.getBoolean(KEY_INVENTORY_EXPIRING_SOON, false))
    val inventoryExpiringSoonCollapsed: StateFlow<Boolean> = _inventoryExpiringSoonCollapsed
    fun setInventoryExpiringSoonCollapsed(collapsed: Boolean) {
        prefs.edit().putBoolean(KEY_INVENTORY_EXPIRING_SOON, collapsed).apply()
        _inventoryExpiringSoonCollapsed.value = collapsed
    }

    private val _recipesCookWithWhatYouHaveCollapsed = MutableStateFlow(prefs.getBoolean(KEY_RECIPES_COOK, false))
    val recipesCookWithWhatYouHaveCollapsed: StateFlow<Boolean> = _recipesCookWithWhatYouHaveCollapsed
    fun setRecipesCookWithWhatYouHaveCollapsed(collapsed: Boolean) {
        prefs.edit().putBoolean(KEY_RECIPES_COOK, collapsed).apply()
        _recipesCookWithWhatYouHaveCollapsed.value = collapsed
    }

    private val _mealPlanMissingIngredientsCollapsed = MutableStateFlow(prefs.getBoolean(KEY_MEAL_PLAN_MISSING, false))
    val mealPlanMissingIngredientsCollapsed: StateFlow<Boolean> = _mealPlanMissingIngredientsCollapsed
    fun setMealPlanMissingIngredientsCollapsed(collapsed: Boolean) {
        prefs.edit().putBoolean(KEY_MEAL_PLAN_MISSING, collapsed).apply()
        _mealPlanMissingIngredientsCollapsed.value = collapsed
    }

    private companion object {
        const val PREFS_NAME = "hint_card_preferences"
        const val KEY_INVENTORY_EXPIRING_SOON = "inventory_expiring_soon_collapsed"
        const val KEY_RECIPES_COOK = "recipes_cook_with_what_you_have_collapsed"
        const val KEY_MEAL_PLAN_MISSING = "meal_plan_missing_ingredients_collapsed"
    }
}
