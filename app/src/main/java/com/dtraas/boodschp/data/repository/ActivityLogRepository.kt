package com.dtraas.boodschp.data.repository

import com.dtraas.boodschp.data.local.dao.ActivityLogWithProduct
import com.dtraas.boodschp.data.local.entity.ProductEntity
import com.dtraas.boodschp.data.model.ActivityType
import com.dtraas.boodschp.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityLogRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
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
                            )
                        }
                        .sortedByDescending { it.timestamp }
                        .take(limit)
                }
            }
        }

    suspend fun log(barcode: String, type: ActivityType, detail: String) {
        val householdId = householdSession.householdId.value ?: return
        activityLogCollection(householdId).add(
            mapOf(
                "barcode" to barcode,
                "type" to type.storageKey,
                "detail" to detail,
                "timestamp" to System.currentTimeMillis(),
            )
        ).await()
    }
}
