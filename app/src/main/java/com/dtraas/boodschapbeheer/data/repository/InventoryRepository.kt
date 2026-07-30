package com.dtraas.boodschapbeheer.data.repository

import com.dtraas.boodschapbeheer.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschapbeheer.data.local.entity.InventoryItemEntity
import com.dtraas.boodschapbeheer.data.local.entity.ProductEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.Store
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
    private val productRepository: ProductRepository,
    private val shoppingListRepository: ShoppingListRepository,
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
                                expirationDate = item.expirationDate,
                                minQuantity = item.minQuantity,
                                note = item.note,
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
        val updated = (existing ?: InventoryItemEntity(barcode = barcode, quantity = 0, updatedAt = 0L))
            .copy(quantity = newQuantity.coerceAtLeast(0), updatedAt = System.currentTimeMillis())
        inventoryDoc.set(updated.toMap()).await()
        scanHistoryCollection(householdId).add(
            mapOf(
                "barcode" to barcode,
                "scannedAt" to System.currentTimeMillis(),
                "quantityDelta" to quantityDelta,
            )
        ).await()
        activityLogRepository.logScanned(barcode, quantityDelta)
        maybeRestockOnLowQuantity(updated)
    }

    suspend fun updateQuantity(barcode: String, quantity: Int) {
        val householdId = householdSession.householdId.value ?: return
        val clamped = quantity.coerceAtLeast(0)
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val existing = InventoryItemEntity.fromDocument(inventoryDoc.get().await())
        val previousQuantity = existing?.quantity ?: 0
        val updated = (existing ?: InventoryItemEntity(barcode = barcode, quantity = 0, updatedAt = 0L))
            .copy(quantity = clamped, updatedAt = System.currentTimeMillis())
        inventoryDoc.set(updated.toMap()).await()
        if (previousQuantity != clamped) {
            activityLogRepository.logQuantityChanged(barcode, previousQuantity, clamped)
        }
        maybeRestockOnLowQuantity(updated)
    }

    suspend fun setExpirationDate(barcode: String, expirationDate: Long?) {
        val householdId = householdSession.householdId.value ?: return
        inventoryCollection(householdId).document(barcode).update("expirationDate", expirationDate).await()
    }

    suspend fun setMinQuantity(barcode: String, minQuantity: Int?) {
        val householdId = householdSession.householdId.value ?: return
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        inventoryDoc.update("minQuantity", minQuantity).await()
        val updated = InventoryItemEntity.fromDocument(inventoryDoc.get().await()) ?: return
        maybeRestockOnLowQuantity(updated)
    }

    suspend fun setNote(barcode: String, note: String?) {
        val householdId = householdSession.householdId.value ?: return
        inventoryCollection(householdId).document(barcode).update("note", note).await()
    }

    /** Auto re-adds [item]'s product to the shopping list once its quantity drops below its minimum. */
    private suspend fun maybeRestockOnLowQuantity(item: InventoryItemEntity) {
        val minQuantity = item.minQuantity ?: return
        if (item.quantity >= minQuantity) return
        if (shoppingListRepository.hasOpenItemForBarcode(item.barcode)) return
        val product = productRepository.findCached(item.barcode) ?: return
        shoppingListRepository.addItem(
            name = product.name,
            category = Category.fromStorageKey(product.category),
            store = Store.GEEN,
            quantity = 1,
            barcode = item.barcode,
            imageUrl = product.imageUrl,
        )
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
    suspend fun restoreItem(
        barcode: String,
        quantity: Int,
        expirationDate: Long? = null,
        minQuantity: Int? = null,
        note: String? = null,
    ) {
        val householdId = householdSession.householdId.value ?: return
        inventoryCollection(householdId).document(barcode).set(
            InventoryItemEntity(
                barcode = barcode,
                quantity = quantity.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
                expirationDate = expirationDate,
                minQuantity = minQuantity,
                note = note,
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
