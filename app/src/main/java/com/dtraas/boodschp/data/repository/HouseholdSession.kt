package com.dtraas.boodschp.data.repository

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

    fun setHousehold(id: String) {
        prefs.edit().putString(KEY_HOUSEHOLD_ID, id).apply()
        _householdId.value = id
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
