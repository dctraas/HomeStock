package com.dtraas.homestock.data.repository

import android.content.Context
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.model.ActivityType
import com.dtraas.homestock.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Human-readable "detail" text for each activity log entry is rendered once, at
 * write time, using whichever device/locale performed the action — not re-rendered
 * per viewer. For a small household app that's a reasonable simplification: it
 * avoids storing structured per-locale data in Firestore, at the cost of history
 * entries staying in the writer's language rather than adapting per reader.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityLogRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
    private val deviceProfile: DeviceProfile,
) {
    private fun collection(householdId: String, name: String) =
        firestore.collection("households").document(householdId).collection(name)

    private fun activityLogCollection(householdId: String) = collection(householdId, "activityLog")
    private fun productsCollection(householdId: String) = collection(householdId, "products")

    fun observeRecent(limit: Int = 200): Flow<List<ActivityLogWithProduct>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    activityLogCollection(householdId).observeSnapshots(),
                    productsCollection(householdId).observeSnapshots(),
                ) { logSnapshot, productsSnapshot ->
                    val products = productsSnapshot.documents
                        .mapNotNull { ProductEntity.fromDocument(it) }
                        .associateBy { it.barcode }
                    logSnapshot.documents
                        .mapNotNull { doc ->
                            val barcode = doc.getString("barcode") ?: return@mapNotNull null
                            val type = doc.getString("type") ?: return@mapNotNull null
                            val detail = doc.getString("detail") ?: return@mapNotNull null
                            val timestamp = doc.getLong("timestamp") ?: return@mapNotNull null
                            ActivityLogWithProduct(
                                id = doc.id,
                                barcode = barcode,
                                productName = products[barcode]?.name ?: barcode,
                                type = type,
                                detail = detail,
                                timestamp = timestamp,
                                actorName = doc.getString("actorName"),
                            )
                        }
                        .sortedByDescending { it.timestamp }
                        .take(limit)
                }
            }
        }

    suspend fun logScanned(barcode: String, quantityDelta: Int) {
        val sign = if (quantityDelta >= 0) "+" else ""
        log(barcode, ActivityType.SCANNED, context.getString(R.string.activity_detail_scanned, sign, quantityDelta))
    }

    suspend fun logQuantityChanged(barcode: String, previousQuantity: Int, newQuantity: Int) {
        log(
            barcode,
            ActivityType.QUANTITY_CHANGED,
            context.getString(R.string.activity_detail_quantity_changed, previousQuantity, newQuantity),
        )
    }

    suspend fun logRemoved(barcode: String, quantity: Int) {
        log(barcode, ActivityType.REMOVED, context.getString(R.string.activity_detail_removed, quantity))
    }

    suspend fun logWasted(barcode: String, quantity: Int) {
        log(barcode, ActivityType.WASTED, context.getString(R.string.activity_detail_wasted, quantity))
    }

    suspend fun logAddedToShoppingList(barcode: String) {
        log(barcode, ActivityType.ADDED_TO_SHOPPING_LIST, context.getString(R.string.activity_type_added_to_shopping_list))
    }

    private suspend fun log(barcode: String, type: ActivityType, detail: String) {
        val householdId = householdSession.householdId.value ?: return
        activityLogCollection(householdId).add(
            mapOf(
                "barcode" to barcode,
                "type" to type.storageKey,
                "detail" to detail,
                "timestamp" to System.currentTimeMillis(),
                "actorName" to deviceProfile.displayName.value,
            )
        ).await()
    }
}
