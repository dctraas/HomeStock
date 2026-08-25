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
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Daily background check for inventory items whose [expirationDate][com.dtraas.homestock.data.local.entity.InventoryItemEntity.expirationDate]
 * is within [EXPIRY_THRESHOLD_DAYS], posting a single local notification if any are found — its
 * expanded body groups them by day (see [groupedByDayText]) rather than one flat list, so
 * "Vandaag" vs. "Over 2 dagen" stays legible once there's more than a couple of items.
 * A no-op whenever the user hasn't opted in (see [com.dtraas.homestock.data.repository.NotificationPreferences])
 * or hasn't joined a household yet.
 */
class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as HomeStockApplication).container

        if (!container.notificationPreferences.expiryNotificationsEnabled.first()) return Result.success()
        if (container.householdSession.householdId.value == null) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val leadTimeDays = container.notificationPreferences.expiryLeadTimeDays.first()
        val today = LocalDate.now(ZoneOffset.UTC)
        val expiringSoon = container.inventoryRepository.observeInventoryWithProduct().first()
            .filter { item ->
                val expirationDate = item.expirationDate ?: return@filter false
                val date = Instant.ofEpochMilli(expirationDate).atZone(ZoneOffset.UTC).toLocalDate()
                ChronoUnit.DAYS.between(today, date) <= leadTimeDays
            }

        if (expiringSoon.isNotEmpty()) {
            postNotification(expiringSoon)
        }
        return Result.success()
    }

    private fun postNotification(items: List<InventoryItemWithProduct>) {
        val context = applicationContext
        val productNames = items.map { it.name }
        val contentText = if (productNames.size == 1) {
            productNames.first()
        } else {
            context.getString(R.string.notification_expiry_body_multiple_format, productNames.size)
        }
        // The action (not just launching MainActivity plain) is what MainActivity reads to
        // switch Voorraad's "Verloopt bijna" quick filter on, so tapping this notification
        // actually shows the near-expiry products it's about instead of just the default view.
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHOW_EXPIRING_SOON
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1F6F4A.toInt())
            .setContentTitle(context.getString(R.string.notification_expiry_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(groupedByDayText(items)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        // Only offered when there's exactly one expiring product — with several, which one
        // "voeg toe aan lijstje" should mean is ambiguous, so those still just open the app.
        items.singleOrNull()?.let { item ->
            val addToListIntent = Intent(context, AddExpiringItemToShoppingListReceiver::class.java).apply {
                putExtra(AddExpiringItemToShoppingListReceiver.EXTRA_BARCODE, item.barcode)
                putExtra(AddExpiringItemToShoppingListReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            }
            val addToListPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                addToListIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                android.R.drawable.ic_menu_add,
                context.getString(R.string.product_detail_add_to_shopping_list),
                addToListPendingIntent,
            )
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    /**
     * The expanded (BigTextStyle) notification body, grouped by day rather than one flat
     * comma-separated list of every near-expiry product — "Vandaag: Melk, Kaas / Morgen:
     * Yoghurt" reads at a glance, where a long undifferentiated list doesn't once there are
     * more than a couple of items. Groups are ordered soonest-first (Verlopen, then Vandaag,
     * Morgen, Over N dagen), same urgency ordering as the collapsed count implies.
     */
    private fun groupedByDayText(items: List<InventoryItemWithProduct>): String {
        val context = applicationContext
        val today = LocalDate.now(ZoneOffset.UTC)
        val groupedByDaysUntil = items
            .groupBy { item ->
                val date = Instant.ofEpochMilli(item.expirationDate!!).atZone(ZoneOffset.UTC).toLocalDate()
                ChronoUnit.DAYS.between(today, date)
            }
            .toSortedMap()

        return groupedByDaysUntil.entries.joinToString("\n") { (daysUntil, groupItems) ->
            val label = when {
                daysUntil < 0 -> context.getString(R.string.notification_expiry_group_expired)
                daysUntil == 0L -> context.getString(R.string.notification_expiry_group_today)
                daysUntil == 1L -> context.getString(R.string.notification_expiry_group_tomorrow)
                else -> context.getString(R.string.notification_expiry_group_days_format, daysUntil)
            }
            "$label: ${groupItems.joinToString(", ") { it.name }}"
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "expiry_check"
        const val CHANNEL_ID = "expiry_reminders"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /**
         * Arms the daily check so its first run lands on the next occurrence of [hour]:[minute]
         * (device-local wall-clock time — Instellingen > Meldingen's "Tijdstip" control), then
         * repeats every 24h from there. Safe to call unconditionally on every app start (and
         * again whenever the household changes the time) — [ExistingPeriodicWorkPolicy.UPDATE]
         * replaces the pending request's schedule without losing the periodic work's identity,
         * and [doWork] itself no-ops when the user has the setting turned off.
         */
        fun schedule(context: Context, hour: Int = 18, minute: Int = 0) {
            val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMillis(hour, minute), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Milliseconds until the next [hour]:[minute] in the device's own time zone — today if
         *  that moment hasn't passed yet, tomorrow otherwise. */
        private fun initialDelayMillis(hour: Int, minute: Int): Long {
            val now = LocalDateTime.now()
            var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target).toMillis()
        }

        /** Runs a single check right away, e.g. for instant feedback when the user enables the setting. */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build())
        }
    }
}
