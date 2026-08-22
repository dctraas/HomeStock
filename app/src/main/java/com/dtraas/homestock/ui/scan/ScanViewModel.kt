package com.dtraas.homestock.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Result of handling a freshly scanned barcode. */
sealed interface ScanOutcome {
    /** The product was already known, so it was added to the inventory right away — see
     *  [ScanViewModel.lastScanResult] for the details the persistent result card needs. */
    data object QuickAdded : ScanOutcome

    /** Barcode is new to us; the user needs to confirm/fill in details first. */
    data object NeedsConfirmation : ScanOutcome
}

/**
 * What the persistent result card at the bottom of the scanner shows — replaced by the next
 * scan's result rather than auto-dismissing, and undoable via [ScanViewModel.undoLastScan].
 */
data class ScanResultCard(
    val barcode: String,
    val productName: String,
    val imageUrl: String?,
    val newQuantity: Int,
    val previousQuantity: Int,
    val restockedProductName: String? = null,
)

class ScanViewModel(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val _sessionScanCount = MutableStateFlow(0)
    val sessionScanCount: StateFlow<Int> = _sessionScanCount

    private val _lastScanResult = MutableStateFlow<ScanResultCard?>(null)
    val lastScanResult: StateFlow<ScanResultCard?> = _lastScanResult

    /**
     * Known products are added straight away (for fast, repeated scanning);
     * unknown barcodes still need the confirmation screen.
     */
    suspend fun handleScannedBarcode(barcode: String): ScanOutcome {
        val cached = productRepository.findCached(barcode) ?: return ScanOutcome.NeedsConfirmation
        _sessionScanCount.value += 1
        val previousQuantity = inventoryRepository.observeInventoryItem(barcode).first()?.quantity ?: 0
        val restockedProductName = inventoryRepository.recordScan(barcode, 1, Category.fromStorageKey(cached.category))
        _lastScanResult.value = ScanResultCard(
            barcode = barcode,
            productName = cached.name,
            imageUrl = cached.imageUrl,
            newQuantity = previousQuantity + 1,
            previousQuantity = previousQuantity,
            restockedProductName = restockedProductName,
        )
        return ScanOutcome.QuickAdded
    }

    /** Reverses the inventory change from the currently shown result card — back to zero (i.e.
     *  removed entirely) if this scan is what put the item in the inventory in the first place,
     *  otherwise back to whatever quantity it had just before this scan. The session scan count
     *  itself is left untouched: it counts how many barcodes were scanned this session, which
     *  undoing one scan's inventory effect doesn't change. */
    fun undoLastScan() {
        val result = _lastScanResult.value ?: return
        _lastScanResult.value = null
        viewModelScope.launch {
            if (result.previousQuantity <= 0) {
                inventoryRepository.removeFromInventory(result.barcode)
            } else {
                inventoryRepository.updateQuantity(result.barcode, result.previousQuantity)
            }
        }
    }
}
