package com.dtraas.homestock.messaging

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
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.MainActivity
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.NotificationPreferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the two real-time cross-device pushes this app sends via FCM — see the matching
 * Firestore-triggered Cloud Functions in functions/src/index.ts, which call
 * `admin.messaging().sendEachForMulticast()` against every *other* household member's token
 * (never the acting device's own, so nobody gets pinged about their own action):
 *  - `type: "activity"` — a huisgenoot changed something in the shared inventory/shopping list.
 *  - `type: "member_joined"` / `type: "member_left"` — someone joined or left the household.
 *
 * Deliberately a data-only payload (no top-level "notification" key) rather than letting FCM
 * auto-display a notification: a data message always reaches [onMessageReceived] — foreground,
 * background, or the app not running (Firebase starts a receiver for it) — where the app-level
 * [NotificationPreferences.householdActivityNotificationsEnabled] toggle can be honored; a
 * "notification"-keyed payload would bypass that check entirely whenever the app isn't in the
 * foreground.
 */
class HomeStockMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val container = (applicationContext as HomeStockApplication).container
        serviceScope.launch { container.householdMembersRepository.updateFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Read directly rather than through the app container's StateFlow — this service can
        // be woken by the system with no other part of the app having run yet, and the
        // constructor already eagerly reads the current SharedPreferences value, so this is
        // synchronous and always current.
        if (!NotificationPreferences(applicationContext).householdActivityNotificationsEnabled.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val data = message.data
        val actorName = data["actorName"]?.takeIf { it.isNotBlank() }
        val (title, body) = when (data["type"]) {
            "activity" -> getString(R.string.notification_household_activity_title) to
                if (actorName != null) {
                    getString(R.string.notification_household_activity_body_format, actorName)
                } else {
                    getString(R.string.notification_household_activity_body_generic)
                }
            "member_joined" -> getString(R.string.notification_household_change_title) to
                getString(R.string.notification_member_joined_body_format, actorName ?: return)
            "member_left" -> getString(R.string.notification_household_change_title) to
                getString(R.string.notification_member_left_body_format, actorName ?: return)
            else -> return
        }

        postNotification(title, body)
    }

    private fun postNotification(title: String, body: String) {
        val context = applicationContext
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHOW_HOUSEHOLD_ACTIVITY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            // A fixed id (rather than per-message) so a burst of activity from a busy household
            // collapses into "latest wins" instead of stacking a dozen separate notifications —
            // tapping any of them already opens the same Meldingen screen with the full history.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1006
        const val CHANNEL_ID = "household_activity"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_household_activity_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_household_activity_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
