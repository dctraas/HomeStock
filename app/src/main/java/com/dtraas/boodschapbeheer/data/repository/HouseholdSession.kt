package com.dtraas.boodschapbeheer.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds which household this device currently belongs to. Persisted locally so the
 * choice survives app restarts; every Firestore-backed repository reads [householdId]
 * to know which household's data to read and write.
 */
class HouseholdSession(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _householdId = MutableStateFlow(prefs.getString(KEY_HOUSEHOLD_ID, null))
    val householdId: StateFlow<String?> = _householdId

    // Transient (never persisted) — true for the one composition of the main app right after
    // this device creates or joins a household, so it can offer a one-time "link your account"
    // prompt at the moment someone is most engaged, without it reappearing on every later cold
    // start. See BoodschapBeheerApp's use of this alongside AccountLinkRepository.
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

    fun leaveHousehold() {
        prefs.edit().remove(KEY_HOUSEHOLD_ID).apply()
        _householdId.value = null
    }

    private companion object {
        const val PREFS_NAME = "household_session"
        const val KEY_HOUSEHOLD_ID = "household_id"
    }
}
