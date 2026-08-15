package com.dtraas.homestock.data.repository

import android.content.Context
import com.dtraas.homestock.data.model.DeveloperNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Local, per-device state for developer notices (see
 * [com.dtraas.homestock.data.model.DeveloperNotices]) — purely local since the notices
 * themselves are static/built into the app rather than coming from Firestore, so there's
 * nothing shared to sync either kind of state below.
 *
 * [dismissedIds] — swiped away on the Meldingen tab; the notice disappears from that list for
 * good.
 *
 * [seenIds] — the Meldingen tab has been opened since the notice shipped; the notice stays in
 * the list, only the unread badge on Voorraad's Meldingen icon stops counting it. Distinct from
 * dismissal on purpose: opening the tab clears the "new" indicator, but the notice itself is
 * still worth keeping around to read (or re-read) until the user explicitly swipes it away.
 */
class DismissedNoticesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val storeScope = CoroutineScope(Dispatchers.IO)

    private val _dismissedIds = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED_IDS, emptySet()).orEmpty())
    val dismissedIds: StateFlow<Set<String>> = _dismissedIds

    private val _seenIds = MutableStateFlow(prefs.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty())
    val seenIds: StateFlow<Set<String>> = _seenIds

    /** Notices that are neither dismissed nor seen yet — drives the red counter badge on
     *  Voorraad's Meldingen icon. Deliberately ignores Geschiedenis (the activity log tab on the
     *  same screen) — the badge is about new developer announcements, not app activity. */
    val unreadCount: StateFlow<Int> = combine(_dismissedIds, _seenIds) { dismissed, seen ->
        DeveloperNotices.all.count { it.id !in dismissed && it.id !in seen }
    }.stateIn(storeScope, SharingStarted.Eagerly, 0)

    fun dismiss(id: String) {
        val updated = _dismissedIds.value + id
        prefs.edit().putStringSet(KEY_DISMISSED_IDS, updated).apply()
        _dismissedIds.value = updated
    }

    /** Call once the Meldingen tab has actually been shown to the user — marks every notice
     *  that currently exists as seen, so the badge drops to 0 until a new notice ships. */
    fun markAllSeen() {
        val allIds = DeveloperNotices.all.map { it.id }.toSet()
        if (allIds.all { it in _seenIds.value }) return
        val updated = _seenIds.value + allIds
        prefs.edit().putStringSet(KEY_SEEN_IDS, updated).apply()
        _seenIds.value = updated
    }

    private companion object {
        const val PREFS_NAME = "dismissed_notices"
        const val KEY_DISMISSED_IDS = "dismissed_ids"
        const val KEY_SEEN_IDS = "seen_ids"
    }
}
