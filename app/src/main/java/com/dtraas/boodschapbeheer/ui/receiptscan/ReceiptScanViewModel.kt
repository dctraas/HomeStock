package com.dtraas.boodschapbeheer.ui.receiptscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.receipt.ReceiptParser
import com.dtraas.boodschapbeheer.data.remote.CategoryMapper
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ReceiptConfirmItem(
    val id: String,
    val name: String,
    val category: Category,
    val checked: Boolean = true,
)

sealed interface ReceiptScanStep {
    data object Capturing : ReceiptScanStep
    data object Processing : ReceiptScanStep
    data class Confirming(val items: List<ReceiptConfirmItem>) : ReceiptScanStep
    data object Saving : ReceiptScanStep
    data object Done : ReceiptScanStep
    data object Failed : ReceiptScanStep
}

class ReceiptScanViewModel(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<ReceiptScanStep>(ReceiptScanStep.Capturing)
    val step: StateFlow<ReceiptScanStep> = _step

    fun onCaptureFailed() {
        _step.value = ReceiptScanStep.Failed
    }

    fun onTextRecognized(rawText: String) {
        _step.value = ReceiptScanStep.Processing
        val items = ReceiptParser.parse(rawText).mapIndexed { index, line ->
            ReceiptConfirmItem(
                id = index.toString(),
                name = line.name,
                category = CategoryMapper.guessCategory(categoriesTags = null, categoriesText = null, productName = line.name),
            )
        }
        _step.value = ReceiptScanStep.Confirming(items)
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
                inventoryRepository.recordScan(syntheticBarcode, 1)
            }
            _step.value = ReceiptScanStep.Done
        }
    }
}
