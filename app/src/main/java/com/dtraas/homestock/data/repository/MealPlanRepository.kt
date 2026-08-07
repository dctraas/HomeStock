package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.remote.observeSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The household's maaltijdplanner — a real, date-based plan (not a repeating weekly template):
 * one Firestore document per calendar date (`households/{id}/mealPlan/{yyyy-MM-dd}`) with up to
 * 4 fields, one per [MealSlot] (ontbijt/lunch/avondeten/tussendoor), so reading or writing a
 * whole day is a single round-trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    private fun mealPlanCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("mealPlan")

    private fun dayDoc(householdId: String, date: LocalDate) =
        mealPlanCollection(householdId).document(date.format(DATE_FORMATTER))

    fun observeMealPlan(date: LocalDate): Flow<Map<MealSlot, PlannedMeal?>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyMap())
            } else {
                dayDoc(householdId, date).observeSnapshot().map { snapshot ->
                    MealSlot.ORDERED.associateWith { slot ->
                        PlannedMeal.fromMap(snapshot.get(slot.storageKey) as? Map<*, *>)
                    }
                }
            }
        }

    suspend fun setMeal(date: LocalDate, slot: MealSlot, meal: PlannedMeal) {
        val householdId = householdSession.householdId.value ?: return
        dayDoc(householdId, date).set(mapOf(slot.storageKey to meal.toMap()), SetOptions.merge()).await()
    }

    /** [SetOptions.merge] with a delete sentinel, rather than update(), so this still works even if the day's document doesn't exist yet. */
    suspend fun clearMeal(date: LocalDate, slot: MealSlot) {
        val householdId = householdSession.householdId.value ?: return
        dayDoc(householdId, date).set(mapOf(slot.storageKey to FieldValue.delete()), SetOptions.merge()).await()
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
