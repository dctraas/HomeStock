package com.dtraas.homestock.ui.scanresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ProductNotFoundException
import com.dtraas.homestock.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanResultUiState(
    val barcode: String,
    val isLoading: Boolean = true,
    val name: String = "",
    val brand: String? = null,
    val imageUrl: String? = null,
    val unit: String? = null,
    val category: Category = Category.OVERIG,
    val quantity: Int = 1,
    val wasFoundOnline: Boolean = false,
    /** True while a lookup failure looks like a connectivity problem rather than a genuinely unknown product. */
    val networkError: Boolean = false,
    val savedToInventory: Boolean = false,
)

class ScanResultViewModel(
    private val barcode: String,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanResultUiState(barcode = barcode))
    val uiState: StateFlow<ScanResultUiState> = _uiState

    init {
        loadProduct()
    }

    private fun loadProduct() {
        _uiState.update { it.copy(isLoading = true, networkError = false) }
        viewModelScope.launch {
            val result = productRepository.getOrFetchProduct(barcode)
            result.onSuccess { product ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = product.name,
                        brand = product.brand,
                        imageUrl = product.imageUrl,
                        unit = product.unit,
                        category = Category.fromStorageKey(product.category),
                        wasFoundOnline = true,
                        networkError = false,
                    )
                }
            }.onFailure { error ->
                // A genuinely unknown barcode (Open Food Facts says "no such product") is
                // different from failing to reach the server at all — the first means "fill
                // this in yourself", the second means "try again once you have a connection".
                val isNetworkIssue = error !is ProductNotFoundException
                _uiState.update {
                    it.copy(isLoading = false, wasFoundOnline = false, networkError = isNetworkIssue)
                }
            }
        }
    }

    fun retry() = loadProduct()

    fun continueManually() = _uiState.update { it.copy(networkError = false) }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onCategoryChange(category: Category) = _uiState.update { it.copy(category = category) }

    fun onQuantityChange(quantity: Int) = _uiState.update { it.copy(quantity = quantity.coerceAtLeast(1)) }

    fun onConfirm() {
        val state = _uiState.value
        if (state.name.isBlank()) return
        viewModelScope.launch {
            if (state.wasFoundOnline) {
                productRepository.updateCategory(barcode, state.category)
            } else {
                productRepository.saveManualProduct(barcode, state.name, state.category)
            }
            inventoryRepository.recordScan(barcode, state.quantity)
            _uiState.update { it.copy(savedToInventory = true) }
        }
    }
}
