package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** System default, always light, or always dark — a per-device display preference. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * The user's preferred app theme (Nachtmodus). A per-device setting, not shared
 * via Firestore, mirroring [NotificationPreferences]'s persistence pattern.
 */
class ThemePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private companion object {
        const val PREFS_NAME = "theme_preferences"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
