package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether this device wants each category of local/push notification HomeStock can send.
 * A per-device setting (not shared via Firestore) since each household member decides for
 * themselves whether they want to be pinged — four independent toggles rather than one
 * blanket switch, so e.g. someone who wants expiry reminders but not household-activity
 * pings isn't forced to choose between all-or-nothing.
 */
class NotificationPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Governs [com.dtraas.homestock.work.ExpiryCheckWorker]'s "bijna over de datum" reminder. */
    private val _expiryNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_EXPIRY_ENABLED, false))
    val expiryNotificationsEnabled: StateFlow<Boolean> = _expiryNotificationsEnabled

    /** How many days out [com.dtraas.homestock.work.ExpiryCheckWorker] starts warning about an
     *  item — Instellingen > Meldingen's "Waarschuw" control (1/2/3 dagen). */
    private val _expiryLeadTimeDays = MutableStateFlow(prefs.getInt(KEY_EXPIRY_LEAD_DAYS, DEFAULT_EXPIRY_LEAD_DAYS))
    val expiryLeadTimeDays: StateFlow<Int> = _expiryLeadTimeDays

    /** Wall-clock hour/minute (device-local time) [com.dtraas.homestock.work.ExpiryCheckWorker]'s
     *  daily check aims to fire at — Instellingen > Meldingen's "Tijdstip" control. */
    private val _expiryNotifyHour = MutableStateFlow(prefs.getInt(KEY_EXPIRY_NOTIFY_HOUR, DEFAULT_EXPIRY_NOTIFY_HOUR))
    val expiryNotifyHour: StateFlow<Int> = _expiryNotifyHour

    private val _expiryNotifyMinute = MutableStateFlow(prefs.getInt(KEY_EXPIRY_NOTIFY_MINUTE, DEFAULT_EXPIRY_NOTIFY_MINUTE))
    val expiryNotifyMinute: StateFlow<Int> = _expiryNotifyMinute

    /** Governs [com.dtraas.homestock.work.LowStockCheckWorker]'s "lage voorraad" reminder and
     *  [com.dtraas.homestock.work.WasteSummaryWorker]'s weekly verspilling-samenvatting — grouped
     *  under one toggle since both are periodic voorraad-inzichten, not urgent-per-item pings. */
    private val _inventoryInsightNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_INVENTORY_INSIGHT_ENABLED, false))
    val inventoryInsightNotificationsEnabled: StateFlow<Boolean> = _inventoryInsightNotificationsEnabled

    /** Governs [com.dtraas.homestock.work.PremiumTrialCheckWorker]'s trial-eindigt-binnenkort reminder. */
    private val _premiumNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM_ENABLED, true))
    val premiumNotificationsEnabled: StateFlow<Boolean> = _premiumNotificationsEnabled

    /** Governs the real-time cross-device pushes sent via Cloud Functions + FCM (see
     *  HomeStockMessagingService): a huisgenoot's activity, and someone joining/leaving the
     *  household. Defaults to on — unlike the other three (which are opt-in, matching the
     *  existing "Meldingen" toggle's default), these are the closest thing this app has to a
     *  "someone did something in our shared household" ping, which most people expect on by
     *  default the way a chat app's message notifications are. */
    private val _householdActivityNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HOUSEHOLD_ACTIVITY_ENABLED, true))
    val householdActivityNotificationsEnabled: StateFlow<Boolean> = _householdActivityNotificationsEnabled

    fun setExpiryNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXPIRY_ENABLED, enabled).apply()
        _expiryNotificationsEnabled.value = enabled
    }

    fun setExpiryLeadTimeDays(days: Int) {
        prefs.edit().putInt(KEY_EXPIRY_LEAD_DAYS, days).apply()
        _expiryLeadTimeDays.value = days
    }

    /** Also re-arms [com.dtraas.homestock.work.ExpiryCheckWorker] at the new time — see its
     *  `schedule`'s doc; the caller (Instellingen > Meldingen) is expected to do that right after
     *  calling this, same as it already does for the other toggles here. */
    fun setExpiryNotifyTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_EXPIRY_NOTIFY_HOUR, hour).putInt(KEY_EXPIRY_NOTIFY_MINUTE, minute).apply()
        _expiryNotifyHour.value = hour
        _expiryNotifyMinute.value = minute
    }

    fun setInventoryInsightNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INVENTORY_INSIGHT_ENABLED, enabled).apply()
        _inventoryInsightNotificationsEnabled.value = enabled
    }

    fun setPremiumNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM_ENABLED, enabled).apply()
        _premiumNotificationsEnabled.value = enabled
    }

    fun setHouseholdActivityNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOUSEHOLD_ACTIVITY_ENABLED, enabled).apply()
        _householdActivityNotificationsEnabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "notification_preferences"
        const val KEY_EXPIRY_ENABLED = "expiry_notifications_enabled"
        const val KEY_INVENTORY_INSIGHT_ENABLED = "inventory_insight_notifications_enabled"
        const val KEY_PREMIUM_ENABLED = "premium_notifications_enabled"
        const val KEY_HOUSEHOLD_ACTIVITY_ENABLED = "household_activity_notifications_enabled"
        const val KEY_EXPIRY_LEAD_DAYS = "expiry_lead_time_days"
        const val KEY_EXPIRY_NOTIFY_HOUR = "expiry_notify_hour"
        const val KEY_EXPIRY_NOTIFY_MINUTE = "expiry_notify_minute"
        const val DEFAULT_EXPIRY_LEAD_DAYS = 2
        const val DEFAULT_EXPIRY_NOTIFY_HOUR = 18
        const val DEFAULT_EXPIRY_NOTIFY_MINUTE = 0
    }
}
