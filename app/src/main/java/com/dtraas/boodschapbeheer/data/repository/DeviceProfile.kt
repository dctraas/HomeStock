package com.dtraas.boodschapbeheer.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * An optional, per-device display name (e.g. "Mama", "Jip") used to attribute
 * activity log entries to a person. Purely local — not an account, not shared
 * via Firestore beyond being stamped onto the entries this device writes.
 */
class DeviceProfile(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, null))
    val displayName: StateFlow<String?> = _displayName

    fun setDisplayName(name: String?) {
        val normalized = name?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().putString(KEY_DISPLAY_NAME, normalized).apply()
        _displayName.value = normalized
    }

    private companion object {
        const val PREFS_NAME = "device_profile"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}
