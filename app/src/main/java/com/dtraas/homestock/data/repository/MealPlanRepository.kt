package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.remote.observeSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.time.DayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The household's weekmenu — a single, repeating weekly plan ("what do we usually cook on
 * Mondays") rather than a full date-based calendar, which keeps this to one small Firestore
 * document (`households/{id}/mealPlan/current`) with up to 7 fields, one per [DayOfWeek], each
 * holding a [PlannedMeal]. [DayOfWeek]'s own English constant name backs the Firestore field
 * key (stable regardless of the app's display locale); [DayOfWeek.getDisplayName] backs the
 * localized label shown in the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
    private val recipeRepository: RecipeRepository,
) {
    private fun mealPlanDoc(householdId: String) =
        firestore.collection("households").document(householdId).collection("mealPlan").document(DOC_ID)

    private fun fieldKey(day: DayOfWeek): String = day.name.lowercase()

    fun observeMealPlan(): Flow<Map<DayOfWeek, PlannedMeal?>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyMap())
            } else {
                mealPlanDoc(householdId).observeSnapshot().map { snapshot ->
                    DayOfWeek.entries.associateWith { day ->
                        PlannedMeal.fromMap(snapshot.get(fieldKey(day)) as? Map<*, *>)
                    }
                }
            }
        }

    suspend fun setMeal(day: DayOfWeek, meal: PlannedMeal) {
        val householdId = householdSession.householdId.value ?: return
        mealPlanDoc(householdId).set(mapOf(fieldKey(day) to meal.toMap()), SetOptions.merge()).await()
    }

    /** [SetOptions.merge] with a delete sentinel value, rather than update(), so this still
     *  works even if the document doesn't exist yet (nothing was ever planned before). */
    suspend fun clearMeal(day: DayOfWeek) {
        val householdId = householdSession.householdId.value ?: return
        mealPlanDoc(householdId)
            .set(mapOf(fieldKey(day) to FieldValue.delete()), SetOptions.merge())
            .await()
    }

    /**
     * Fetches full ingredient lists for every currently planned meal and adds whatever isn't
     * already in inventory (or already on the shopping list) to it — one combined list for the
     * whole week rather than duplicating shared ingredients per day. Returns how many distinct
     * ingredients were actually added.
     */
    suspend fun generateShoppingList(): Result<Int> = try {
        val plan = observeMealPlan().first()
        val mealIds = plan.values.filterNotNull().map { it.mealId }.distinct()
        if (mealIds.isEmpty()) {
            Result.success(0)
        } else {
            val details = mealIds.mapNotNull { recipeRepository.getRecipeDetail(it).getOrNull() }
            val missing = recipeRepository.missingIngredients(details)
            Result.success(recipeRepository.addIngredientsToShoppingList(missing))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val DOC_ID = "current"
    }
}
