package com.dtraas.homestock.data.repository

import android.content.Context
import com.dtraas.homestock.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Server-tunable monetization knobs, fetched via Firebase Remote Config so they can change
 * without an app release. Same trust model as the rest of this app's household logic (see
 * [HouseholdMembersRepository]'s class doc) — these are soft business limits read
 * client-side, not a hard security boundary; a modified APK could ignore them, exactly as it
 * already could ignore the free-tier member cap. Every flag defaults to this app's previous
 * hardcoded behavior, so a device that has never successfully fetched Remote Config (e.g.
 * offline first launch) behaves exactly as it did before this repository existed.
 */
class RemoteConfigRepository(context: Context) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    private val _premiumMemberCap = MutableStateFlow(DEFAULT_PREMIUM_MEMBER_CAP)
    /** Max household members a Premium household gets *without* the "Onbeperkt huisgenoten"
     *  add-on (see [HouseholdMembersRepository.PREMIUM_MEMBER_LIMIT]). */
    val premiumMemberCap: StateFlow<Long> = _premiumMemberCap

    private val _trialDays = MutableStateFlow(DEFAULT_TRIAL_DAYS)
    /** Free-trial length shown in the Premium screen's copy. The actual trial is configured
     *  as a Play Console subscription offer (see [BillingRepository]); this only drives what
     *  the app *says* about it, so it must be kept in sync with that offer by hand if it ever
     *  changes — Play Billing has no API to read a trial's length back out of an offer. */
    val trialDays: StateFlow<Long> = _trialDays

    private val _monthlyPlanEnabled = MutableStateFlow(true)
    /** Kill switch for showing the monthly plan card, in case it needs pulling without a
     *  release (e.g. a Play Console product misconfiguration discovered after launch). */
    val monthlyPlanEnabled: StateFlow<Boolean> = _monthlyPlanEnabled

    init {
        // A short minimum fetch interval in debug builds so a changed Remote Config value
        // shows up on the next app restart instead of Firebase's throttling holding onto a
        // stale fetch for up to the default 12h — mirrors BillingRepository's debug-only
        // override in spirit (fast iteration locally, production-safe defaults in release).
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0L else 3600L)
                .build(),
        )
        repositoryScope.launch {
            runCatching {
                remoteConfig.setDefaultsAsync(
                    mapOf(
                        KEY_PREMIUM_MEMBER_CAP to DEFAULT_PREMIUM_MEMBER_CAP,
                        KEY_TRIAL_DAYS to DEFAULT_TRIAL_DAYS,
                        KEY_MONTHLY_PLAN_ENABLED to true,
                    ),
                ).await()
                remoteConfig.fetchAndActivate().await()
            }
            // Read back regardless of whether the fetch above actually succeeded — the
            // defaults just set are already in effect either way, so this never leaves the
            // StateFlows at their pre-init placeholder values.
            _premiumMemberCap.value = remoteConfig.getLong(KEY_PREMIUM_MEMBER_CAP).takeIf { it > 0 } ?: DEFAULT_PREMIUM_MEMBER_CAP
            _trialDays.value = remoteConfig.getLong(KEY_TRIAL_DAYS).takeIf { it > 0 } ?: DEFAULT_TRIAL_DAYS
            _monthlyPlanEnabled.value = remoteConfig.getBoolean(KEY_MONTHLY_PLAN_ENABLED)
        }
    }

    companion object {
        private const val KEY_PREMIUM_MEMBER_CAP = "premium_member_cap"
        private const val KEY_TRIAL_DAYS = "trial_days"
        private const val KEY_MONTHLY_PLAN_ENABLED = "monthly_plan_enabled"

        const val DEFAULT_PREMIUM_MEMBER_CAP = 10L
        const val DEFAULT_TRIAL_DAYS = 7L
    }
}
