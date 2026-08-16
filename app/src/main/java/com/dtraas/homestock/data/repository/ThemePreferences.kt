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
 * The user's preferred app theme (Nachtmodus) plus two accessibility toggles — groot lettertype
 * (see [HomeStockTheme]'s typography scaling) and hoog contrast (stronger text/outline contrast
 * against the app's own palette, still built from it rather than a separate high-contrast theme).
 * All three are a per-device setting, not shared via Firestore, mirroring
 * [NotificationPreferences]'s persistence pattern.
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

    private val _largeText = MutableStateFlow(prefs.getBoolean(KEY_LARGE_TEXT, false))
    val largeText: StateFlow<Boolean> = _largeText

    fun setLargeText(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LARGE_TEXT, enabled).apply()
        _largeText.value = enabled
    }

    private val _highContrast = MutableStateFlow(prefs.getBoolean(KEY_HIGH_CONTRAST, false))
    val highContrast: StateFlow<Boolean> = _highContrast

    fun setHighContrast(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _highContrast.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "theme_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LARGE_TEXT = "large_text"
        const val KEY_HIGH_CONTRAST = "high_contrast"
    }
}
