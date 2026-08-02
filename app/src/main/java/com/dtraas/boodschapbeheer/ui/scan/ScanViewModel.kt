package com.dtraas.boodschapbeheer.ui.scan

import androidx.lifecycle.ViewModel
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.ProductRepository

/** Result of handling a freshly scanned barcode. */
sealed interface ScanOutcome {
    /**
     * The product was already known, so it was added to the inventory right away.
     * [restockedProductName] is set when the new quantity is still below the
     * item's configured minimum, which auto-adds it to the shopping list.
     */
    data class QuickAdded(val productName: String, val restockedProductName: String? = null) : ScanOutcome

    /** Barcode is new to us; the user needs to confirm/fill in details first. */
    data object NeedsConfirmation : ScanOutcome
}

class ScanViewModel(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    /**
     * Known products are added straight away (for fast, repeated scanning);
     * unknown barcodes still need the confirmation screen.
     */
    suspend fun handleScannedBarcode(barcode: String): ScanOutcome {
        val cached = productRepository.findCached(barcode)
        return if (cached != null) {
            val restockedProductName = inventoryRepository.recordScan(barcode, 1)
            ScanOutcome.QuickAdded(cached.name, restockedProductName)
        } else {
            ScanOutcome.NeedsConfirmation
        }
    }
}
