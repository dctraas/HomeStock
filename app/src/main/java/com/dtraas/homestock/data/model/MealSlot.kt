package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/** A meal-of-the-day slot in the maaltijdplanner — see [com.dtraas.homestock.data.repository.MealPlanRepository]. */
enum class MealSlot(val storageKey: String, @StringRes val labelRes: Int) {
    BREAKFAST("breakfast", R.string.meal_plan_slot_breakfast),
    LUNCH("lunch", R.string.meal_plan_slot_lunch),
    DINNER("dinner", R.string.meal_plan_slot_dinner),
    SNACK("snack", R.string.meal_plan_slot_snack);

    companion object {
        val ORDERED = listOf(BREAKFAST, LUNCH, DINNER, SNACK)
    }
}
