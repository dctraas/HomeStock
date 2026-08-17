package com.dtraas.homestock.data.repository

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin wrapper around Firebase Analytics, scoped to the Premium funnel: paywall views, plan
 * selection, purchase outcomes, and the household member-cap upsell (see [BillingRepository],
 * [HouseholdMembersRepository]). Every event here is anonymous product-usage telemetry — an
 * event name plus a handful of non-identifying params (which plan, which product id). Nothing
 * personally identifying (display name, household code, email) is ever logged; Firebase
 * Analytics' own pseudonymous app-instance id is the only "identity" involved, same as any
 * other Firebase Analytics integration.
 *
 * Plain [FirebaseAnalytics] API rather than the `firebase-analytics-ktx` `logEvent { }` builder
 * — matches this project's existing convention of using the base Firebase Java APIs everywhere
 * else (see [AppContainer]) with kotlinx-coroutines-play-services only for `.await()`.
 */
class AnalyticsRepository(context: Context) {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext)

    /** Where the paywall was opened from — helps tell "browsed in from Meer" apart from
     *  "hit a hard lock trying to use a Premium feature", which convert very differently. */
    fun logPremiumScreenViewed(source: String) {
        analytics.logEvent("premium_screen_viewed", Bundle().apply { putString("source", source) })
    }

    /** A plan card (monthly/yearly/lifetime) was tapped, before the Play checkout sheet opens. */
    fun logPremiumPlanSelected(plan: String) {
        analytics.logEvent("premium_plan_selected", Bundle().apply { putString("plan", plan) })
    }

    /** The Play Billing checkout sheet was actually launched for [productId]. Doesn't imply
     *  completion — [logPurchaseCompleted] is the conversion event, this is funnel context for
     *  how many people get as far as seeing Play's own checkout UI. */
    fun logPurchaseStarted(productId: String) {
        analytics.logEvent("premium_purchase_started", Bundle().apply { putString(FirebaseAnalytics.Param.ITEM_ID, productId) })
    }

    /** A purchase (subscription or one-time) was acknowledged as PURCHASED — see
     *  [BillingRepository.handlePurchases]. [isTrial] marks a subscription that started under
     *  a free-trial offer, so trial-to-paid conversion can be tracked separately from the raw
     *  purchase count. */
    fun logPurchaseCompleted(productId: String, isTrial: Boolean) {
        analytics.logEvent(
            "premium_purchase_completed",
            Bundle().apply {
                putString(FirebaseAnalytics.Param.ITEM_ID, productId)
                putLong("is_trial", if (isTrial) 1L else 0L)
            },
        )
    }

    /** The one-time "Onbeperkt huisgenoten" add-on (see [HouseholdMembersRepository]) was
     *  purchased — tracked separately from the main Premium purchase events since it's sold
     *  to already-Premium households, a distinct upsell funnel. */
    fun logUnlimitedMembersAddonPurchased() {
        analytics.logEvent("unlimited_members_addon_purchased", null)
    }

    /** A join attempt was refused by the free-tier 2-member cap (see
     *  [HouseholdMembersRepository.FREE_MEMBER_LIMIT]) — the household isn't Premium at all. */
    fun logHouseholdJoinBlockedFreeLimit() {
        analytics.logEvent("household_join_blocked_free_limit", null)
    }

    /** A join attempt was refused by the Premium household cap (see
     *  [HouseholdMembersRepository.PREMIUM_MEMBER_LIMIT]) — the household is already Premium
     *  but hasn't bought the unlimited-members add-on. The clearest possible signal that this
     *  add-on's upsell is actually being shown to a household that needs it. */
    fun logHouseholdJoinBlockedPremiumCap() {
        analytics.logEvent("household_join_blocked_premium_cap", null)
    }

    /** "Eerdere aankoop herstellen" was tapped on the Premium screen. */
    fun logRestorePurchasesTapped() {
        analytics.logEvent("premium_restore_tapped", null)
    }

    /** The first-run feature tour (see
     *  [com.dtraas.homestock.ui.onboarding.OnboardingTourScreen]) was watched all the way to
     *  its last page and finished via "Aan de slag". */
    fun logOnboardingTourCompleted() {
        analytics.logEvent("onboarding_tour_completed", null)
    }

    /** The tour was dismissed early via "Overslaan" — [page] is the 0-indexed page it was
     *  skipped from, useful for spotting which page people bail out at. */
    fun logOnboardingTourSkipped(page: Int) {
        analytics.logEvent("onboarding_tour_skipped", Bundle().apply { putLong("page", page.toLong()) })
    }
}
