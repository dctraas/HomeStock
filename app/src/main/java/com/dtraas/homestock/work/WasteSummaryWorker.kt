package com.dtraas.homestock.work

import android.Manifest
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
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Weekly background check summarizing food waste logged in the trailing [WINDOW_DAYS] days (see
 * [com.dtraas.homestock.data.repository.StatisticsRepository.observeWasteSince]) — a fixed
 * rolling window rather than tracking "since the last successful run", so a delayed or skipped
 * firing (e.g. the device was off) doesn't shrink or widen what the next one reports. A no-op
 * when nothing was wasted in that window — this is meant as an occasional nudge, not a weekly
 * "good job, zero waste" notification. Same gating shape as [ExpiryCheckWorker].
 */
class WasteSummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as HomeStockApplication).container

        if (!container.notificationPreferences.inventoryInsightNotificationsEnabled.first()) return Result.success()
        if (container.householdSession.householdId.value == null) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val sinceMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(WINDOW_DAYS)
        val summary = container.statisticsRepository.observeWasteSince(sinceMillis).first()

        if (summary.count > 0) {
            postNotification(summary.count, summary.totalValue)
        }
        return Result.success()
    }

    private fun postNotification(count: Int, totalValue: Double) {
        val context = applicationContext
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
        val contentText = if (totalValue > 0.0) {
            context.getString(R.string.notification_waste_summary_body_with_value_format, count, currencyFormat.format(totalValue))
        } else {
            context.getString(R.string.notification_waste_summary_body_format, count)
        }
        // Opens Statistieken, which is where the waste breakdown itself lives (see
        // StatisticsScreen's "meest verspild" section) — nothing item-specific to filter on
        // here the way expiry/low-stock's quick filters do.
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHOW_WASTE_SUMMARY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, LowStockCheckWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1F6F4A.toInt())
            .setContentTitle(context.getString(R.string.notification_waste_summary_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
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
        private const val WINDOW_DAYS = 7L
        private const val NOTIFICATION_ID = 1004
        private const val WORK_NAME = "waste_summary_check"

        /** Arms the weekly check. Idempotent (KEEP) and safe to call unconditionally on every
         *  app start — [doWork] itself no-ops when the user has the setting turned off. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WasteSummaryWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Runs a single check right away, e.g. for instant feedback when the user enables the setting. */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WasteSummaryWorker>().build())
        }
    }
}
