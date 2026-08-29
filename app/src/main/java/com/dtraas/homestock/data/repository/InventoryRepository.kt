package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.local.entity.InventoryItemEntity
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.local.entity.ScanHistoryEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.ExpiryEstimator
import com.dtraas.homestock.data.remote.observeSnapshot
import com.dtraas.homestock.data.remote.observeSnapshots
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
    private val inventoryPreferences: InventoryPreferences,
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
                                isFavorite = item.isFavorite,
                                location = product.location,
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                }
            }
        }

    /**
     * Registers a barcode scan: bumps (or creates) the inventory quantity by
     * [quantityDelta] and appends a scan-history entry.
     *
     * [category], when given, seeds a suggested houdbaarheidsdatum (see [ExpiryEstimator]) —
     * but only the first time this barcode enters inventory (`existing == null`). A restock of
     * an item that's already here keeps whatever expiry it already has (including none, if the
     * user cleared it), rather than silently reintroducing a guessed date.
     *
     * Returns the product name if this scan dropped the quantity below its
     * minimum and triggered an auto-restock onto the shopping list, so the
     * caller can tell the user why an item just appeared there.
     */
    suspend fun recordScan(barcode: String, quantityDelta: Int = 1, category: Category? = null): String? {
        val householdId = householdSession.householdId.value ?: return null
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val existing = InventoryItemEntity.fromDocument(inventoryDoc.get().await())
        val newQuantity = (existing?.quantity ?: 0) + quantityDelta
        val expirationDate = if (existing == null) category?.let { ExpiryEstimator.estimate(it) } else existing.expirationDate
        val updated = (existing ?: InventoryItemEntity(barcode = barcode, quantity = 0, updatedAt = 0L))
            .copy(quantity = newQuantity.coerceAtLeast(0), updatedAt = System.currentTimeMillis(), expirationDate = expirationDate)
        inventoryDoc.set(updated.toMap()).await()
        scanHistoryCollection(householdId).add(
            mapOf(
                "barcode" to barcode,
                "scannedAt" to System.currentTimeMillis(),
                "quantityDelta" to quantityDelta,
            )
        ).await()
        activityLogRepository.logScanned(barcode, quantityDelta)
        return maybeRestockOnLowQuantity(updated)
    }

    /** Returns the restocked product name — see [recordScan]. */
    suspend fun updateQuantity(barcode: String, quantity: Int): String? {
        val householdId = householdSession.householdId.value ?: return null
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
        return maybeRestockOnLowQuantity(updated)
    }

    suspend fun setExpirationDate(barcode: String, expirationDate: Long?) {
        val householdId = householdSession.householdId.value ?: return
        inventoryCollection(householdId).document(barcode).update("expirationDate", expirationDate).await()
    }

    /** Returns the restocked product name — see [recordScan]. */
    suspend fun setMinQuantity(barcode: String, minQuantity: Int?): String? {
        val householdId = householdSession.householdId.value ?: return null
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        inventoryDoc.update("minQuantity", minQuantity).await()
        val updated = InventoryItemEntity.fromDocument(inventoryDoc.get().await()) ?: return null
        return maybeRestockOnLowQuantity(updated)
    }

    suspend fun setNote(barcode: String, note: String?) {
        val householdId = householdSession.householdId.value ?: return
        inventoryCollection(householdId).document(barcode).update("note", note).await()
    }

    suspend fun setFavorite(barcode: String, isFavorite: Boolean) {
        val householdId = householdSession.householdId.value ?: return
        inventoryCollection(householdId).document(barcode).update("isFavorite", isFavorite).await()
    }

    /** All scan-history entries for one barcode, newest first — powers Productdetail's
     *  "Gescand N× / ≈ elke X dagen" stat tile. [scanHistoryCollection] already keeps a record
     *  per scan across the whole household; this just narrows it to one product. */
    fun observeScanHistoryForBarcode(barcode: String): Flow<List<ScanHistoryEntity>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                scanHistoryCollection(householdId)
                    .whereEqualTo("barcode", barcode)
                    .observeSnapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { ScanHistoryEntity.fromDocument(it) }
                            .sortedByDescending { it.scannedAt }
                    }
            }
        }

    /**
     * Auto re-adds [item]'s product to the shopping list once its quantity drops below its
     * minimum. Returns the product name when it actually added something, so callers can
     * surface that to the user — this otherwise happens silently.
     */
    private suspend fun maybeRestockOnLowQuantity(item: InventoryItemEntity): String? {
        if (!inventoryPreferences.autoRestockEnabled.value) return null
        val minQuantity = item.minQuantity ?: return null
        if (item.quantity >= minQuantity) return null
        if (shoppingListRepository.hasOpenItemForBarcode(item.barcode)) return null
        val product = productRepository.findCached(item.barcode) ?: return null
        shoppingListRepository.addItem(
            name = product.name,
            category = Category.fromStorageKey(product.category),
            store = "",
            quantity = 1,
            barcode = item.barcode,
            imageUrl = product.imageUrl,
        )
        return product.name
    }

    /**
     * Consumes one unit of [barcode] as the result of marking a meal-plan product opgebruikt or
     * weggegooid (see MealPlanViewModel.markMealEaten/markMealWasted) — by one unit rather than
     * the item's entire quantity the way [removeFromInventory] works, since a meal is one
     * portion, not necessarily everything left in stock. The inventory row is dropped entirely
     * once it hits 0, same end state [removeFromInventory] leaves. A no-op if [barcode] isn't in
     * inventory at all (e.g. this meal referenced a product the household has since used up or
     * removed by hand some other way).
     */
    suspend fun consumeOneFromMeal(barcode: String, wasted: Boolean) {
        val householdId = householdSession.householdId.value ?: return
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val existing = InventoryItemEntity.fromDocument(inventoryDoc.get().await()) ?: return
        val newQuantity = existing.quantity - 1
        if (newQuantity <= 0) {
            inventoryDoc.delete().await()
        } else {
            inventoryDoc.set(existing.copy(quantity = newQuantity, updatedAt = System.currentTimeMillis()).toMap()).await()
        }
        if (wasted) activityLogRepository.logWasted(barcode, 1) else activityLogRepository.logRemoved(barcode, 1)
    }

    suspend fun removeFromInventory(barcode: String, wasted: Boolean = false) {
        val householdId = householdSession.householdId.value ?: return
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val existing = InventoryItemEntity.fromDocument(inventoryDoc.get().await())
        inventoryDoc.delete().await()
        if (existing != null) {
            if (wasted) {
                activityLogRepository.logWasted(barcode, existing.quantity)
            } else {
                activityLogRepository.logRemoved(barcode, existing.quantity)
            }
        }
    }

    /**
     * Marks only [amount] of [barcode]'s current stock as used up/wasted, rather than
     * [removeFromInventory]'s "the whole quantity is gone" assumption — ProductDetailScreen asks
     * for [amount] instead of just removing outright whenever a product's own quantity is more
     * than 1, since "opgemaakt"/"weggegooid" on, say, 6 yoghurts on the shelf usually means one
     * of them, not all six. [amount] is clamped to what's actually there (never negative-
     * quantities the doc, never logs more than it had); deletes the doc outright once the
     * remainder hits zero, same as [consumeOneFromMeal] one unit at a time already does.
     */
    suspend fun removeQuantityFromInventory(barcode: String, amount: Int, wasted: Boolean = false) {
        val householdId = householdSession.householdId.value ?: return
        val inventoryDoc = inventoryCollection(householdId).document(barcode)
        val existing = InventoryItemEntity.fromDocument(inventoryDoc.get().await()) ?: return
        val removedAmount = amount.coerceIn(1, existing.quantity)
        val newQuantity = existing.quantity - removedAmount
        if (newQuantity <= 0) {
            inventoryDoc.delete().await()
        } else {
            inventoryDoc.set(existing.copy(quantity = newQuantity, updatedAt = System.currentTimeMillis()).toMap()).await()
        }
        if (wasted) {
            activityLogRepository.logWasted(barcode, removedAmount)
        } else {
            activityLogRepository.logRemoved(barcode, removedAmount)
        }
    }

    /** Re-creates an inventory row after an undo action, without touching the activity log. */
    suspend fun restoreItem(
        barcode: String,
        quantity: Int,
        expirationDate: Long? = null,
        minQuantity: Int? = null,
        note: String? = null,
        isFavorite: Boolean = false,
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
                isFavorite = isFavorite,
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
