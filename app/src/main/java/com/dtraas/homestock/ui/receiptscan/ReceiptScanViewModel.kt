package com.dtraas.homestock.ui.receiptscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ProductRepository
import com.dtraas.homestock.data.repository.ReceiptQueueRepository
import com.dtraas.homestock.data.repository.ReceiptRecognitionRepository
import com.dtraas.homestock.data.repository.RecognizeReceiptResult
import com.dtraas.homestock.data.repository.RecognizedReceiptItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One receipt line, ready for the confirm screen. [matchedBarcode]/[brand]/[unit]/[imageUrl] come
 * from looking [name] up in the product database (see [ReceiptScanViewModel.matchItem]) so the
 * confirm row can show the same name/merk/eenheid + stepper layout as a Voorraad list row rather
 * than bare text — null when nothing matched, which just falls back to a manually-entered product
 * at save time, exactly like today.
 */
data class ReceiptConfirmItem(
    val id: String,
    val name: String,
    val category: Category,
    val quantity: Int = 1,
    val checked: Boolean = true,
    val matchedBarcode: String? = null,
    val brand: String? = null,
    val unit: String? = null,
    val imageUrl: String? = null,
    // Total line price as read off the receipt (not per-unit yet) — divided by quantity in
    // confirmAndSave() before it's stored as the product's lastPrice. Null when unreadable.
    val price: Double? = null,
)

enum class ReceiptFailReason {
    /** Camera capture itself failed (device/CameraX issue), before any network call. */
    CAPTURE,
    NO_CONNECTION,

    /** Server re-checked and this household isn't (or is no longer) premium. */
    PREMIUM_REQUIRED,
    UNKNOWN,
}

sealed interface ReceiptScanStep {
    data object Capturing : ReceiptScanStep

    /** Photo taken, waiting on [ReceiptRecognitionRepository.recognize] — a real network round trip to the Cloud Function (which itself calls Claude). */
    data object Analyzing : ReceiptScanStep

    /** Looking each read-off item up in the product database (see [ReceiptScanViewModel.matchItem]) before showing the confirm list. */
    data object Matching : ReceiptScanStep

    data class Confirming(val items: List<ReceiptConfirmItem>) : ReceiptScanStep
    data object Saving : ReceiptScanStep
    data object Done : ReceiptScanStep

    /** Offline (or a momentary hiccup) when [onPhotoCaptured] tried to reach `recognizeReceipt` —
     *  the photo has already been handed to [ReceiptQueueRepository] instead of losing it to a
     *  dead-end error, and will be processed automatically once the device is back online. */
    data object Queued : ReceiptScanStep
    data class Failed(val reason: ReceiptFailReason) : ReceiptScanStep
}

class ReceiptScanViewModel(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val receiptRecognitionRepository: ReceiptRecognitionRepository,
    private val receiptQueueRepository: ReceiptQueueRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<ReceiptScanStep>(ReceiptScanStep.Capturing)
    val step: StateFlow<ReceiptScanStep> = _step

    /** How many receipts (from this device, any session) are still waiting in the offline
     *  queue — shown on the Capturing screen so scanning while offline doesn't feel like it
     *  silently vanished. See [ReceiptQueueRepository]. */
    val pendingQueueCount: StateFlow<Int> = receiptQueueRepository.pendingCount

    fun onCaptureFailed() {
        _step.value = ReceiptScanStep.Failed(ReceiptFailReason.CAPTURE)
    }

    fun onPhotoCaptured(jpegBytes: ByteArray) {
        _step.value = ReceiptScanStep.Analyzing
        viewModelScope.launch {
            when (val result = receiptRecognitionRepository.recognize(jpegBytes)) {
                is RecognizeReceiptResult.Success -> {
                    _step.value = ReceiptScanStep.Matching
                    val items = coroutineScope {
                        result.items.mapIndexed { index, item -> async { matchItem(index.toString(), item) } }.awaitAll()
                    }
                    _step.value = ReceiptScanStep.Confirming(items)
                }
                RecognizeReceiptResult.PremiumRequired -> _step.value = ReceiptScanStep.Failed(ReceiptFailReason.PREMIUM_REQUIRED)
                // Offline-first: rather than a dead-end "geen verbinding" error that loses the
                // photo the moment the user leaves this screen, hand it to the local queue —
                // ReceiptQueueWorker processes it automatically once connectivity returns.
                RecognizeReceiptResult.NoConnection -> {
                    receiptQueueRepository.enqueue(jpegBytes)
                    _step.value = ReceiptScanStep.Queued
                }
                RecognizeReceiptResult.Failed -> _step.value = ReceiptScanStep.Failed(ReceiptFailReason.UNKNOWN)
            }
        }
    }

    /**
     * Looks [item] up by name in the product database (same free-text search as "Zoeken op
     * naam" — see [ProductRepository.searchByName]) and, on a hit, fetches that product's full
     * detail so the confirm row can show brand/eenheid/foto like a real Voorraad item. Best
     * effort only: any failure (no match, offline, a flaky single request) just falls back to an
     * unmatched [ReceiptConfirmItem] — one bad lookup shouldn't block the rest of the receipt,
     * and [confirmAndSave] already knows how to save an unmatched item manually, same as before
     * this matching pass existed.
     */
    private suspend fun matchItem(id: String, item: RecognizedReceiptItem): ReceiptConfirmItem {
        val base = ReceiptConfirmItem(id = id, name = item.name, category = item.category, quantity = item.quantity, price = item.price)
        val barcode = productRepository.searchByName(item.name).getOrNull()?.firstOrNull()?.barcode ?: return base
        val product = productRepository.getOrFetchProduct(barcode).getOrNull() ?: return base
        return base.copy(matchedBarcode = product.barcode, brand = product.brand, unit = product.unit, imageUrl = product.imageUrl)
    }

    fun retake() {
        _step.value = ReceiptScanStep.Capturing
    }

    fun toggleItem(id: String) {
        updateConfirming { items -> items.map { if (it.id == id) it.copy(checked = !it.checked) else it } }
    }

    fun updateItemName(id: String, name: String) {
        updateConfirming { items -> items.map { if (it.id == id) it.copy(name = name) else it } }
    }

    fun increaseQuantity(id: String) {
        updateConfirming { items -> items.map { if (it.id == id) it.copy(quantity = it.quantity + 1) else it } }
    }

    fun decreaseQuantity(id: String) {
        updateConfirming { items -> items.map { if (it.id == id) it.copy(quantity = (it.quantity - 1).coerceAtLeast(1)) else it } }
    }

    private inline fun updateConfirming(transform: (List<ReceiptConfirmItem>) -> List<ReceiptConfirmItem>) {
        val current = _step.value
        if (current is ReceiptScanStep.Confirming) {
            _step.value = current.copy(items = transform(current.items))
        }
    }

    fun confirmAndSave() {
        val current = _step.value
        if (current !is ReceiptScanStep.Confirming) return
        val toAdd = current.items.filter { it.checked && it.name.isNotBlank() }
        if (toAdd.isEmpty()) return

        _step.value = ReceiptScanStep.Saving
        viewModelScope.launch {
            toAdd.forEach { item ->
                val matchedBarcode = item.matchedBarcode
                val barcode: String
                if (matchedBarcode != null) {
                    // Already fetched/cached during matchItem() — just apply the receipt's own
                    // category read (same "found online" convention as ScanResultViewModel:
                    // keep the database's name/brand/unit, only the category comes from us).
                    productRepository.updateCategory(matchedBarcode, item.category)
                    inventoryRepository.recordScan(matchedBarcode, item.quantity, item.category)
                    barcode = matchedBarcode
                } else {
                    // No database match — synthesize a barcode, same role a scanned barcode
                    // plays elsewhere as the product key.
                    val syntheticBarcode = "receipt-${UUID.randomUUID()}"
                    productRepository.saveManualProduct(syntheticBarcode, item.name, item.category)
                    inventoryRepository.recordScan(syntheticBarcode, item.quantity, item.category)
                    barcode = syntheticBarcode
                }
                // The receipt's price is a line total, not per-unit — divide it back down so
                // ProductEntity.lastPrice always reflects "price per unit", matching how the
                // product is priced everywhere else in the app.
                item.price?.let { totalPrice ->
                    productRepository.updateLastPrice(barcode, totalPrice / item.quantity.coerceAtLeast(1))
                }
            }
            _step.value = ReceiptScanStep.Done
        }
    }
}
