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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Daily background check for inventory items whose [expirationDate][com.dtraas.homestock.data.local.entity.InventoryItemEntity.expirationDate]
 * is within [EXPIRY_THRESHOLD_DAYS], posting a single local notification if any are found.
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

        val today = LocalDate.now(ZoneOffset.UTC)
        val expiringSoon = container.inventoryRepository.observeInventoryWithProduct().first()
            .filter { item ->
                val expirationDate = item.expirationDate ?: return@filter false
                val date = Instant.ofEpochMilli(expirationDate).atZone(ZoneOffset.UTC).toLocalDate()
                ChronoUnit.DAYS.between(today, date) <= EXPIRY_THRESHOLD_DAYS
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
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.notification_expiry_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(productNames.joinToString(", ")))
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

    companion object {
        private const val EXPIRY_THRESHOLD_DAYS = 3L
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
         * Arms the daily check. Idempotent (KEEP) and safe to call unconditionally on every
         * app start — [doWork] itself no-ops when the user has the setting turned off.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Runs a single check right away, e.g. for instant feedback when the user enables the setting. */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build())
        }
    }
}
