package com.dtraas.homestock.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.MainActivity
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.hasTrialOffer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Daily background check for a Premium free trial that's about to end, so someone who forgot
 * they started one isn't surprised by the first real charge.
 *
 * There's no server-confirmed trial-expiry date anywhere in this app yet (see
 * [com.dtraas.homestock.data.repository.BillingRepository]'s class doc: `isPremium` is
 * Play-derived, not a stored expiry timestamp) — asking the Play Developer API for one would
 * mean extending the `verifyPurchase` Cloud Function and storing the result, a bigger change
 * than this reminder needs. Instead this approximates the trial's end locally: a purchase's own
 * `purchaseTime` (when the subscription was bought — Play Billing's own field, present on every
 * [com.android.billingclient.api.Purchase]) plus
 * [com.dtraas.homestock.data.repository.RemoteConfigRepository.trialDays] (the trial length
 * configured in the Play Console, mirrored here so this app's own copy always matches — see that
 * property's doc). Only fires for a purchase whose offer actually included a
 * trial phase ([hasTrialOffer]); it can't know whether *this* purchase actually got that trial
 * (Play decides eligibility server-side, per [hasTrialOffer]'s own doc) so on the rare case of a
 * returning subscriber who was charged immediately, this may fire a reminder for a trial that
 * was never actually granted — a false positive that costs nothing more than one dismissible
 * notification, versus the alternative of silently never reminding anyone.
 */
class PremiumTrialCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as HomeStockApplication).container

        if (!container.notificationPreferences.premiumNotificationsEnabled.first()) return Result.success()
        if (container.householdSession.householdId.value == null) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val billingRepository = container.billingRepository
        val trialDaysMillis = TimeUnit.DAYS.toMillis(container.remoteConfigRepository.trialDays.value)
        val productDetails = billingRepository.productDetails.value
        val now = System.currentTimeMillis()

        val endingTrial = billingRepository.activePurchases.value.firstOrNull { purchase ->
            val productId = purchase.products.firstOrNull() ?: return@firstOrNull false
            if (productDetails[productId]?.hasTrialOffer != true) return@firstOrNull false
            val trialEnd = purchase.purchaseTime + trialDaysMillis
            val daysRemaining = TimeUnit.MILLISECONDS.toDays(trialEnd - now)
            daysRemaining in 0..TRIAL_REMINDER_THRESHOLD_DAYS
        } ?: return Result.success()

        // Dedupes across this worker's own daily runs — without this, every run inside the
        // threshold window (not just the first) would re-post the same reminder.
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_NOTIFIED_TOKEN, null) == endingTrial.purchaseToken) return Result.success()
        prefs.edit().putString(KEY_LAST_NOTIFIED_TOKEN, endingTrial.purchaseToken).apply()

        postNotification()
        return Result.success()
    }

    private fun postNotification() {
        val context = applicationContext
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHOW_PREMIUM_TRIAL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.notification_premium_trial_title))
            .setContentText(context.getString(R.string.notification_premium_trial_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_premium_trial_body)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder)
        }
    }

    companion object {
        // Reminds once the trial has 2 days or fewer left (but hasn't lapsed yet, hence 0 as
        // the lower bound too — a purchase that already converted to paid no longer has
        // hasTrialOffer-eligible activePurchases entries worth reminding about differently).
        private const val TRIAL_REMINDER_THRESHOLD_DAYS = 2L
        private const val NOTIFICATION_ID = 1005
        private const val WORK_NAME = "premium_trial_check"
        private const val PREFS_NAME = "premium_trial_worker_prefs"
        private const val KEY_LAST_NOTIFIED_TOKEN = "last_notified_purchase_token"
        const val CHANNEL_ID = "premium_reminders"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_premium_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_premium_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Arms the daily check. Idempotent (KEEP) and safe to call unconditionally on every
         *  app start — [doWork] itself no-ops when the user has the setting turned off. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PremiumTrialCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Runs a single check right away, e.g. for instant feedback when the user enables the setting. */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PremiumTrialCheckWorker>().build())
        }
    }
}
