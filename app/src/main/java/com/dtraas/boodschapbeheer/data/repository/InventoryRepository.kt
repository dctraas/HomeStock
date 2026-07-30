package com.dtraas.boodschapbeheer.data.repository

import com.dtraas.boodschapbeheer.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschapbeheer.data.local.entity.InventoryItemEntity
import com.dtraas.boodschapbeheer.data.local.entity.ProductEntity
import com.dtraas.boodschapbeheer.data.remote.observeSnapshot
import com.dtraas.boodschapbeheer.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
    private val activityLogRepository: ActivityLogRepository,
) {
    private fun collection(householdId: String, name: String) =
        firestore.collection("households").document(householdId).collection(name)

    private fun inventoryCollection(householdId: String) = collection(householdId, "inventory")
    private fun productsCollection(householdId: String) = collection(householdId, "products")
    private fun scanHistoryCollection(householdId: String) = collection(householdId, "scanHistory")

    /** Flat inventory list joined with product data, unsorted into categories. */
    fun observeInventoryWithProduct(): Flow<List<InventoryItemWithProduct>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    inventoryCollection(householdId).observeSnapshots(),
                    productsCollection(householdId).observeSnapshots(),
                ) { inventorySnapshot, productsSnapshot ->
                    val products = productsSnapshot.documents
                        .mapNotNull { ProductEntity.fromDocument(it) }
                        .associateBy { it.barcode }
                    inventorySnapshot.documents
                        .mapNotNull { InventoryItemEntity.fromDocument(it) }
                        .mapNotNull { item ->
                            val product = products[item.barcode] ?: return@mapNotNull null
                            InventoryItemWithProduct(
                                barcode = item.barcode,
                                name = product.name,
                                brand = product.brand,
                                category = product.category,
                                imageUrl = product.imageUrl,
                                unit = product.unit,
                                quantity = item.quantity,
                                updatedAt = item.updatedAt,
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                }
            }
        }

    /**
     * Registers a barcode scan: bumps (or creates) the inventory quantity by
     * [quantityDelta] and appends a scan-history entry.
     */
    suspend fun recordScan(barcode: String, quantityDelta: Int = 1) {
        val householdId = householdSession.householdId.value ?: return
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val existing = InventoryItemEntity.fromDocument(inventoryDoc.get().await())
        val newQuantity = (existing?.quantity ?: 0) + quantityDelta
        inventoryDoc.set(
            InventoryItemEntity(
                barcode = barcode,
                quantity = newQuantity.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
            ).toMap()
        ).await()
        scanHistoryCollection(householdId).add(
            mapOf(
                "barcode" to barcode,
                "scannedAt" to System.currentTimeMillis(),
                "quantityDelta" to quantityDelta,
            )
        ).await()
        activityLogRepository.logScanned(barcode, quantityDelta)
    }

    suspend fun updateQuantity(barcode: String, quantity: Int) {
        val householdId = householdSession.householdId.value ?: return
        val clamped = quantity.coerceAtLeast(0)
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val previousQuantity = InventoryItemEntity.fromDocument(inventoryDoc.get().await())?.quantity ?: 0
        inventoryDoc.set(
            InventoryItemEntity(barcode = barcode, quantity = clamped, updatedAt = System.currentTimeMillis()).toMap()
        ).await()
        if (previousQuantity != clamped) {
            activityLogRepository.logQuantityChanged(barcode, previousQuantity, clamped)
        }
    }

    suspend fun removeFromInventory(barcode: String) {
        val householdId = householdSession.householdId.value ?: return
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val existing = InventoryItemEntity.fromDocument(inventoryDoc.get().await())
        inventoryDoc.delete().await()
        if (existing != null) {
            activityLogRepository.logRemoved(barcode, existing.quantity)
        }
    }

    /** Re-creates an inventory row after an undo action, without touching the activity log. */
    suspend fun restoreItem(barcode: String, quantity: Int) {
        val householdId = householdSession.householdId.value ?: return
        inventoryCollection(householdId).document(barcode).set(
            InventoryItemEntity(
                barcode = barcode,
                quantity = quantity.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
            ).toMap()
        ).await()
    }

    fun observeInventoryItem(barcode: String): Flow<InventoryItemEntity?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                inventoryCollection(householdId).document(barcode).observeSnapshot()
                    .map { InventoryItemEntity.fromDocument(it) }
            }
        }

}
