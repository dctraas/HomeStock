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
import java.util.concurrent.TimeUnit

/**
 * Daily background check for inventory items at or below the minimum quantity the household set
 * for them ([InventoryItemWithProduct.minQuantity]), posting a single local notification if any
 * are found. Same shape as [ExpiryCheckWorker] (its doc comment covers the general pattern —
 * daily [CoroutineWorker], gated on preference + household + permission, one notification per
 * run) but for "lage voorraad" instead of "bijna over de datum", and without day-grouping since
 * there's no date dimension here — just a flat list of names.
 */
class LowStockCheckWorker(
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

        val lowStock = container.inventoryRepository.observeInventoryWithProduct().first()
            .filter { item -> item.minQuantity != null && item.quantity <= item.minQuantity }

        if (lowStock.isNotEmpty()) {
            postNotification(lowStock)
        }
        return Result.success()
    }

    private fun postNotification(items: List<InventoryItemWithProduct>) {
        val context = applicationContext
        val productNames = items.map { it.name }
        val contentText = if (productNames.size == 1) {
            productNames.first()
        } else {
            context.getString(R.string.notification_low_stock_body_multiple_format, productNames.size)
        }
        // Mirrors ACTION_SHOW_EXPIRING_SOON on ExpiryCheckWorker — MainActivity reads this to
        // switch Voorraad's "Lage voorraad" quick filter on, so tapping the notification shows
        // exactly the products it's about.
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHOW_LOW_STOCK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1F6F4A.toInt())
            .setContentTitle(context.getString(R.string.notification_low_stock_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(productNames.joinToString(", ")))
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
        private const val NOTIFICATION_ID = 1003
        private const val WORK_NAME = "low_stock_check"
        const val CHANNEL_ID = "inventory_insights"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_inventory_insight_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_inventory_insight_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Arms the daily check. Idempotent (KEEP) and safe to call unconditionally on every
         *  app start — [doWork] itself no-ops when the user has the setting turned off. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LowStockCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Runs a single check right away, e.g. for instant feedback when the user enables the setting. */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<LowStockCheckWorker>().build())
        }
    }
}
