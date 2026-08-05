package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which developer notices (see [com.dtraas.homestock.data.model.DeveloperNotices])
 * this device has swiped away. Purely local — the notices themselves are static/built into
 * the app rather than coming from Firestore, so there's nothing shared to dismiss.
 */
class DismissedNoticesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dismissedIds = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED_IDS, emptySet()).orEmpty())
    val dismissedIds: StateFlow<Set<String>> = _dismissedIds

    fun dismiss(id: String) {
        val updated = _dismissedIds.value + id
        prefs.edit().putStringSet(KEY_DISMISSED_IDS, updated).apply()
        _dismissedIds.value = updated
    }

    private companion object {
        const val PREFS_NAME = "dismissed_notices"
        const val KEY_DISMISSED_IDS = "dismissed_ids"
    }
}
