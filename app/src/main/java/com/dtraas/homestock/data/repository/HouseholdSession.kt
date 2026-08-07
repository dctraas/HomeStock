package com.dtraas.homestock.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A household this device was in before, for the "switch huishouden" list — see [HouseholdSession.recentHouseholds]. */
data class RecentHousehold(val id: String, val name: String?)

/**
 * Holds which household this device currently belongs to. Persisted locally so the
 * choice survives app restarts; every Firestore-backed repository reads [householdId]
 * to know which household's data to read and write.
 */
class HouseholdSession(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _householdId = MutableStateFlow(prefs.getString(KEY_HOUSEHOLD_ID, null))
    val householdId: StateFlow<String?> = _householdId

    // Every household this device has ever created or joined, most-recent-first — lets
    // "wisselen van huishouden" (Instellingen > Huishouden) rejoin one with a single tap
    // instead of retyping its code. Deliberately device-local rather than synced: which
    // households *this device* has passed through isn't shared household data.
    private val _recentHouseholds = MutableStateFlow(loadRecentHouseholds())
    val recentHouseholds: StateFlow<List<RecentHousehold>> = _recentHouseholds

    private fun loadRecentHouseholds(): List<RecentHousehold> {
        val json = prefs.getString(KEY_RECENT_HOUSEHOLDS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<RecentHousehold>>() {}.type
            gson.fromJson<List<RecentHousehold>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /**
     * Records [id] as most-recently-used, keeping its cached [name] if this call doesn't know
     * it (e.g. right after joining by code, before the household document has been read) —
     * never regresses a known name back to null. Safe to call often; e.g.
     * HouseholdSettingsScreen calls it whenever the live household name resolves, so a rename
     * (by any member) keeps the switcher's cached label in sync too.
     */
    fun rememberHousehold(id: String, name: String?) {
        val current = _recentHouseholds.value.toMutableList()
        val resolvedName = name ?: current.find { it.id == id }?.name
        current.removeAll { it.id == id }
        current.add(0, RecentHousehold(id, resolvedName))
        saveRecentHouseholds(current.take(MAX_RECENT_HOUSEHOLDS))
    }

    /** Drops [id] from the switcher list — e.g. once switching to it turns out to have failed because it no longer exists. */
    fun forgetHousehold(id: String) {
        saveRecentHouseholds(_recentHouseholds.value.filterNot { it.id == id })
    }

    private fun saveRecentHouseholds(list: List<RecentHousehold>) {
        _recentHouseholds.value = list
        prefs.edit().putString(KEY_RECENT_HOUSEHOLDS, gson.toJson(list)).apply()
    }

    // Transient (never persisted) — true for the one composition of the main app right after
    // this device creates or joins a household, so it can offer a one-time "link your account"
    // prompt at the moment someone is most engaged, without it reappearing on every later cold
    // start. See HomeStockApp's use of this alongside AccountLinkRepository.
    private val _justJoinedHousehold = MutableStateFlow(false)
    val justJoinedHousehold: StateFlow<Boolean> = _justJoinedHousehold

    fun setHousehold(id: String) {
        prefs.edit().putString(KEY_HOUSEHOLD_ID, id).apply()
        _householdId.value = id
        _justJoinedHousehold.value = true
    }

    fun consumeJustJoinedHousehold() {
        _justJoinedHousehold.value = false
    }

    // HouseholdScreen isn't a Navigation-Compose destination — MainActivity just conditionally
    // composes it based on householdId — so there's no NavBackStackEntry-scoped
    // ViewModelStoreOwner to clear HouseholdViewModel for us when this device leaves a
    // household. Without something forcing a new instance, Android's viewModel() call keeps
    // returning the SAME retained HouseholdViewModel (scoped to the whole Activity) the next
    // time HouseholdScreen appears, complete with whatever stale createdCode/joinCodeInput/mode
    // it had from before — e.g. re-showing an already-deleted household's old code as if it had
    // just been created again. HouseholdScreen keys its viewModel() call off this counter so a
    // fresh instance is created every time onboarding needs to start over.
    private val _onboardingGeneration = MutableStateFlow(0)
    val onboardingGeneration: StateFlow<Int> = _onboardingGeneration

    fun leaveHousehold() {
        prefs.edit().remove(KEY_HOUSEHOLD_ID).apply()
        _householdId.value = null
        _onboardingGeneration.value += 1
    }

    private companion object {
        const val PREFS_NAME = "household_session"
        const val KEY_HOUSEHOLD_ID = "household_id"
        const val KEY_RECENT_HOUSEHOLDS = "recent_households"
        const val MAX_RECENT_HOUSEHOLDS = 5
    }
}
