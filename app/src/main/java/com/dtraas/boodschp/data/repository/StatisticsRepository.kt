package com.dtraas.boodschp.data.repository

import com.dtraas.boodschp.data.local.dao.CategoryCount
import com.dtraas.boodschp.data.local.dao.TopScannedProduct
import com.dtraas.boodschp.data.local.entity.InventoryItemEntity
import com.dtraas.boodschp.data.local.entity.ProductEntity
import com.dtraas.boodschp.data.local.entity.ScanHistoryEntity
import com.dtraas.boodschp.data.remote.observeSnapshots
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
) {
    private fun collection(householdId: String, name: String) =
        firestore.collection("households").document(householdId).collection(name)

    private fun products(householdId: String) =
        collection(householdId, "products").observeSnapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { ProductEntity.fromDocument(it) }.associateBy { it.barcode } }

    private fun inventoryWithProducts(): Flow<List<Pair<InventoryItemEntity, ProductEntity>>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    collection(householdId, "inventory").observeSnapshots()
                        .map { snapshot -> snapshot.documents.mapNotNull { InventoryItemEntity.fromDocument(it) } },
                    products(householdId),
                ) { inventory, products ->
                    inventory.mapNotNull { item -> products[item.barcode]?.let { item to it } }
                }
            }
        }

    private fun scanHistory(): Flow<List<ScanHistoryEntity>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                collection(householdId, "scanHistory").observeSnapshots()
                    .map { snapshot -> snapshot.documents.mapNotNull { ScanHistoryEntity.fromDocument(it) } }
            }
        }

    fun observeInventoryCount(): Flow<Int> = inventoryWithProducts().map { it.size }

    fun observeCategoryDistribution(): Flow<List<CategoryCount>> =
        inventoryWithProducts().map { items ->
            items.groupingBy { (_, product) -> product.category }
                .eachCount()
                .map { (category, count) -> CategoryCount(category, count) }
        }

    fun observeTotalScanCount(): Flow<Int> = scanHistory().map { it.size }

    fun observeScanCountSince(sinceMillis: Long): Flow<Int> =
        scanHistory().map { history -> history.count { it.scannedAt >= sinceMillis } }

    fun observeTopScannedProducts(limit: Int = 5): Flow<List<TopScannedProduct>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    collection(householdId, "scanHistory").observeSnapshots()
                        .map { snapshot -> snapshot.documents.mapNotNull { ScanHistoryEntity.fromDocument(it) } },
                    products(householdId),
                ) { history, products ->
                    history
                        .groupingBy { it.barcode }
                        .eachCount()
                        .mapNotNull { (barcode, count) ->
                            products[barcode]?.let { product ->
                                TopScannedProduct(
                                    barcode = barcode,
                                    name = product.name,
                                    category = product.category,
                                    imageUrl = product.imageUrl,
                                    scanCount = count,
                                )
                            }
                        }
                        .sortedByDescending { it.scanCount }
                        .take(limit)
                }
            }
        }
}
