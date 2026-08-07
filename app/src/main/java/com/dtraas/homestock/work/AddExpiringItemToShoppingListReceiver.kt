package com.dtraas.homestock.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.data.model.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the "Toevoegen aan lijstje" action button on a single-product expiry notification (see
 * [ExpiryCheckWorker.postNotification]) — lets someone add the expiring product to the shopping
 * list straight from the notification shade, without opening the app. Mirrors
 * [com.dtraas.homestock.ui.inventory.InventoryViewModel.addToShoppingList]'s quick-add: same
 * store ("" = unassigned), quantity 1, and activity log entry.
 *
 * A BroadcastReceiver (like the shopping-list widget's headless actions) rather than a
 * foreground activity, since the point is not to have to open the app at all. [goAsync] keeps
 * the process alive long enough for the Firestore write, which [onReceive] itself can't await
 * directly.
 */
class AddExpiringItemToShoppingListReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val barcode = intent.getStringExtra(EXTRA_BARCODE) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as HomeStockApplication).container
                val item = container.inventoryRepository.observeInventoryWithProduct().first()
                    .firstOrNull { it.barcode == barcode }
                if (item != null) {
                    container.shoppingListRepository.addItem(
                        name = item.name,
                        category = Category.fromStorageKey(item.category),
                        store = "",
                        quantity = 1,
                        barcode = item.barcode,
                        imageUrl = item.imageUrl,
                    )
                    container.activityLogRepository.logAddedToShoppingList(item.barcode)
                }
                // Dismiss the notification to confirm the tap actually did something — there's
                // no app UI open right now to show that feedback in any other way.
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_BARCODE = "barcode"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
