package com.dtraas.homestock.data.repository

import android.content.Context

/**
 * Whether the one-time "wat kan deze app allemaal"-tour (see
 * [com.dtraas.homestock.ui.onboarding.OnboardingTourScreen], shown from HomeStockApp) has
 * already been shown on this device. Purely local (SharedPreferences), same minimal
 * "one flag, set once" shape as [AccountLinkRepository.hasShownLinkPrompt] — this is a
 * single nudge, not something to re-show on every app start, and there's no equivalent
 * always-available entry point to replay it later (unlike the account-link prompt, which has
 * Meer > Account koppelen as a permanent fallback).
 */
class OnboardingTourPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hasSeenTour: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_TOUR, false)

    fun markTourSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_TOUR, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "onboarding_tour_prefs"
        const val KEY_HAS_SEEN_TOUR = "has_seen_tour"
    }
}
