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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.MainActivity
import com.dtraas.homestock.R

/**
 * Drains [com.dtraas.homestock.data.repository.ReceiptQueueRepository]'s offline queue once
 * connectivity is available — [schedule]'s `NetworkType.CONNECTED` constraint is what WorkManager
 * itself waits on, surviving app restarts/process death on its own (it has its own persisted work
 * database, independent of this app's own local storage); this worker only needs to know *what*
 * to retry (the queue), not *when*.
 *
 * Notifies on success via [ExpiryCheckWorker.CHANNEL_ID] (reusing that existing channel rather
 * than registering a new one) regardless of
 * [com.dtraas.homestock.data.repository.NotificationPreferences.expiryNotificationsEnabled] —
 * that toggle is specifically about proactive houdbaarheid reminders; this notification is a
 * direct result of something the user themselves just did (scanned a receipt while offline), a
 * different enough case to always report back on.
 */
class ReceiptQueueWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as HomeStockApplication).container
        val result = container.receiptQueueRepository.processQueue()
        if (result.processedReceipts > 0) postNotification(result.addedItems)
        return Result.success()
    }

    private fun postNotification(addedItems: Int) {
        val context = applicationContext
        // Every item this queue adds goes through InventoryRepository.recordScan, which already
        // logs its own "toegevoegd" entry (see activityLogRepository.logScanned) — so routing
        // the tap to Meldingen (reusing the same action HomeStockMessagingService's household-
        // activity push uses) surfaces exactly the products this notification is about, freshest
        // first, instead of landing on whatever the app's default screen happens to be.
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHOW_HOUSEHOLD_ACTIVITY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, ExpiryCheckWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1F6F4A.toInt())
            .setContentTitle(context.getString(R.string.notification_receipt_queue_title))
            .setContentText(context.getString(R.string.notification_receipt_queue_body_format, addedItems))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val WORK_NAME = "receipt_queue"

        /** Enqueues a network-constrained run — call whenever a new receipt is queued (see
         *  ReceiptQueueRepository.enqueue) and, as a safety net for a process death between an
         *  enqueue and its schedule call, on every app start while anything is still pending. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReceiptQueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // REPLACE rather than KEEP: a fresh enqueue should still process whatever's queued
            // right now (including whatever this exact call just added), not be skipped because
            // an earlier still-pending run already claimed the unique work name.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
