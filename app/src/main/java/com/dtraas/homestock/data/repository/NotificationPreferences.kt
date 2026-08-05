package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether this device wants local expiry-reminder notifications. A per-device
 * setting (not shared via Firestore) since each household member decides for
 * themselves whether they want to be pinged.
 */
class NotificationPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _expiryNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_EXPIRY_ENABLED, false))
    val expiryNotificationsEnabled: StateFlow<Boolean> = _expiryNotificationsEnabled

    fun setExpiryNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXPIRY_ENABLED, enabled).apply()
        _expiryNotificationsEnabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "notification_preferences"
        const val KEY_EXPIRY_ENABLED = "expiry_notifications_enabled"
    }
}
