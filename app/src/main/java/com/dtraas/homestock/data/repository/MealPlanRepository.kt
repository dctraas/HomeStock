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
 * 4 fields, one per [MealSlot] (ontbijt/lunch/avondeten/tussendoor). Each field holds a *list*
 * of [PlannedMeal] maps rather than a single one — a household can plan more than one dish per
 * slot — so reading or writing a whole day is still a single round-trip.
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

    fun observeMealPlan(date: LocalDate): Flow<Map<MealSlot, List<PlannedMeal>>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyMap())
            } else {
                dayDoc(householdId, date).observeSnapshot().map { snapshot ->
                    MealSlot.ORDERED.associateWith { slot ->
                        (snapshot.get(slot.storageKey) as? List<*>).orEmpty()
                            .mapNotNull { PlannedMeal.fromMap(it as? Map<*, *>) }
                    }
                }
            }
        }

    /** Appends [meal] to [slot]'s list — [PlannedMeal.id] is unique per entry, so this never collides with an existing one. */
    suspend fun addMeal(date: LocalDate, slot: MealSlot, meal: PlannedMeal) {
        val householdId = householdSession.householdId.value ?: return
        dayDoc(householdId, date).set(mapOf(slot.storageKey to FieldValue.arrayUnion(meal.toMap())), SetOptions.merge()).await()
    }

    /**
     * Removes [meal] from [slot]'s list, matched by [PlannedMeal.id] rather than
     * `FieldValue.arrayRemove` — arrayRemove only matches by exact map equality, which broke for
     * any entry stored before a field was later added to [PlannedMeal] (its round-tripped
     * `toMap()` then carries a key the original stored map never had, so they'd never compare
     * equal and the removal silently did nothing). Reading, filtering, and writing back inside a
     * transaction keeps this atomic against a concurrent [addMeal] on the same day/slot, the way
     * arrayRemove was.
     */
    suspend fun removeMeal(date: LocalDate, slot: MealSlot, meal: PlannedMeal) {
        val householdId = householdSession.householdId.value ?: return
        val doc = dayDoc(householdId, date)
        firestore.runTransaction { transaction ->
            val current = (transaction.get(doc).get(slot.storageKey) as? List<*>).orEmpty()
            val updated = current.filterNot { (it as? Map<*, *>)?.get("id") == meal.id }
            transaction.set(doc, mapOf(slot.storageKey to updated), SetOptions.merge())
        }.await()
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
