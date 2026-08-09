package com.dtraas.homestock.ui.receiptscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ProductRepository
import com.dtraas.homestock.data.repository.ReceiptRecognitionRepository
import com.dtraas.homestock.data.repository.RecognizeReceiptResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ReceiptConfirmItem(
    val id: String,
    val name: String,
    val category: Category,
    val quantity: Int = 1,
    val checked: Boolean = true,
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

    data class Confirming(val items: List<ReceiptConfirmItem>) : ReceiptScanStep
    data object Saving : ReceiptScanStep
    data object Done : ReceiptScanStep
    data class Failed(val reason: ReceiptFailReason) : ReceiptScanStep
}

class ReceiptScanViewModel(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val receiptRecognitionRepository: ReceiptRecognitionRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<ReceiptScanStep>(ReceiptScanStep.Capturing)
    val step: StateFlow<ReceiptScanStep> = _step

    fun onCaptureFailed() {
        _step.value = ReceiptScanStep.Failed(ReceiptFailReason.CAPTURE)
    }

    fun onPhotoCaptured(jpegBytes: ByteArray) {
        _step.value = ReceiptScanStep.Analyzing
        viewModelScope.launch {
            when (val result = receiptRecognitionRepository.recognize(jpegBytes)) {
                is RecognizeReceiptResult.Success -> {
                    val items = result.items.mapIndexed { index, item ->
                        ReceiptConfirmItem(id = index.toString(), name = item.name, category = item.category, quantity = item.quantity)
                    }
                    _step.value = ReceiptScanStep.Confirming(items)
                }
                RecognizeReceiptResult.PremiumRequired -> _step.value = ReceiptScanStep.Failed(ReceiptFailReason.PREMIUM_REQUIRED)
                RecognizeReceiptResult.NoConnection -> _step.value = ReceiptScanStep.Failed(ReceiptFailReason.NO_CONNECTION)
                RecognizeReceiptResult.Failed -> _step.value = ReceiptScanStep.Failed(ReceiptFailReason.UNKNOWN)
            }
        }
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
                // Receipt items have no real barcode, so each gets a synthetic one —
                // the same role a scanned barcode plays elsewhere as the product key.
                val syntheticBarcode = "receipt-${UUID.randomUUID()}"
                productRepository.saveManualProduct(syntheticBarcode, item.name, item.category)
                inventoryRepository.recordScan(syntheticBarcode, item.quantity)
            }
            _step.value = ReceiptScanStep.Done
        }
    }
}
