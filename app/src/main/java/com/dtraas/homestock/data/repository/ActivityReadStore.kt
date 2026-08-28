package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Local, per-device "when did I last look at the household activity timeline" marker — powers
 * Activiteit's "N wijzigingen sinds [tijdstip] · Markeer gelezen" banner and each row's unread
 * dot (see NotificationsScreen). Deliberately separate from [DismissedNoticesStore]: that one
 * auto-clears the moment the screen opens (see its own doc), but this banner is explicit on
 * purpose — the mockup shows a "Markeer gelezen" action rather than silently clearing on open,
 * so a household member can still tell what's new even after they've already glanced at the
 * screen once.
 */
class ActivityReadStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // No stored value yet means this is either a fresh install or an existing install that
    // hasn't seen this feature before — either way, nothing that already happened should
    // retroactively count as unread. "Now" becomes the cutoff, and gets persisted immediately so
    // it doesn't drift to a later "now" on the next process start before markSeenNow() is ever
    // called.
    private val initialLastSeenAt: Long = run {
        val stored = prefs.getLong(KEY_LAST_SEEN_AT, -1L)
        if (stored >= 0L) return@run stored
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SEEN_AT, now).apply()
        now
    }

    private val _lastSeenAt = MutableStateFlow(initialLastSeenAt)
    val lastSeenAt: StateFlow<Long> = _lastSeenAt

    /** Called from the "Markeer gelezen" banner action — every entry up to this moment stops
     *  counting as unread. */
    fun markSeenNow() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SEEN_AT, now).apply()
        _lastSeenAt.value = now
    }

    private companion object {
        const val PREFS_NAME = "activity_read_state"
        const val KEY_LAST_SEEN_AT = "last_seen_at"
    }
}
