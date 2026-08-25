package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Remembers when this device last exported Voorraad/Boodschappenlijst data — Instellingen >
 * Data overzetten shows this as a footer line ("Laatste export: 3 juni 2026") so the household
 * knows how stale a backup they might be relying on actually is. Per-device, not shared via
 * Firestore, same reasoning as [NotificationPreferences]/[ThemePreferences] — each device's own
 * export history is its own concern.
 */
class ExportPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastExportTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_EXPORT, 0L).takeIf { it > 0L })
    val lastExportTimestamp: StateFlow<Long?> = _lastExportTimestamp

    fun recordExportNow() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_EXPORT, now).apply()
        _lastExportTimestamp.value = now
    }

    private companion object {
        const val PREFS_NAME = "export_preferences"
        const val KEY_LAST_EXPORT = "last_export_timestamp"
    }
}
