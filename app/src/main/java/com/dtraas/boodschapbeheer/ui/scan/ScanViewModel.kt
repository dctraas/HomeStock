package com.dtraas.boodschapbeheer.ui.scan

import androidx.lifecycle.ViewModel
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.ProductRepository

/** Result of handling a freshly scanned barcode. */
sealed interface ScanOutcome {
    /** The product was already known, so it was added to the inventory right away. */
    data class QuickAdded(val productName: String) : ScanOutcome

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
            inventoryRepository.recordScan(barcode, 1)
            ScanOutcome.QuickAdded(cached.name)
        } else {
            ScanOutcome.NeedsConfirmation
        }
    }
}
