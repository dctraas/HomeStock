package com.dtraas.homestock.ui.productdetail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ProductRepository
import com.dtraas.homestock.data.repository.ShoppingListRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductDetailUiState(
    val product: ProductEntity? = null,
    val quantityInInventory: Int? = null,
    val expirationDate: Long? = null,
    val minQuantity: Int? = null,
    val note: String? = null,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
)

class ProductDetailViewModel(
    private val barcode: String,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
) : ViewModel() {

    val uiState: StateFlow<ProductDetailUiState> = combine(
        productRepository.observeProduct(barcode),
        inventoryRepository.observeInventoryItem(barcode),
    ) { product, inventoryItem ->
        ProductDetailUiState(
            product = product,
            quantityInInventory = inventoryItem?.quantity,
            expirationDate = inventoryItem?.expirationDate,
            minQuantity = inventoryItem?.minQuantity,
            note = inventoryItem?.note,
            isFavorite = inventoryItem?.isFavorite ?: false,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailUiState())

    private val _restockEvents = Channel<String>(Channel.BUFFERED)

    /** Emits a product name whenever a quantity change here triggers an auto-restock. */
    val restockEvents: Flow<String> = _restockEvents.receiveAsFlow()

    private val _isRetryingLookup = MutableStateFlow(false)
    val isRetryingLookup: StateFlow<Boolean> = _isRetryingLookup

    private val _retryLookupSucceeded = Channel<Boolean>(Channel.BUFFERED)
    val retryLookupSucceeded: Flow<Boolean> = _retryLookupSucceeded.receiveAsFlow()

    fun setQuantity(quantity: Int) {
        viewModelScope.launch {
            val restockedProductName = inventoryRepository.updateQuantity(barcode, quantity)
            if (restockedProductName != null) _restockEvents.send(restockedProductName)
        }
    }

    fun setExpirationDate(expirationDate: Long?) {
        viewModelScope.launch { inventoryRepository.setExpirationDate(barcode, expirationDate) }
    }

    fun setMinQuantity(minQuantity: Int?) {
        viewModelScope.launch {
            val restockedProductName = inventoryRepository.setMinQuantity(barcode, minQuantity)
            if (restockedProductName != null) _restockEvents.send(restockedProductName)
        }
    }

    /** Re-fetches this product from Open Food Facts, for entries that were filled in manually. */
    fun retryLookup() {
        viewModelScope.launch {
            _isRetryingLookup.value = true
            val result = productRepository.retryLookup(barcode)
            _isRetryingLookup.value = false
            _retryLookupSucceeded.send(result.isSuccess)
        }
    }

    fun setNote(note: String?) {
        viewModelScope.launch { inventoryRepository.setNote(barcode, note) }
    }

    fun updateName(name: String) {
        viewModelScope.launch { productRepository.updateName(barcode, name) }
    }

    fun updateBrand(brand: String?) {
        viewModelScope.launch { productRepository.updateBrand(barcode, brand) }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { productRepository.updateCategory(barcode, category) }
    }

    fun updateUnit(unit: String?) {
        viewModelScope.launch { productRepository.updateUnit(barcode, unit) }
    }

    fun updateLocation(location: String?) {
        viewModelScope.launch { productRepository.updateLocation(barcode, location) }
    }

    /** Premium feature — the caller (ProductDetailScreen) checks isPremium before ever letting
     *  the picker that produces [uri] be launched, so this itself doesn't re-check. */
    fun uploadCustomPhoto(uri: Uri) {
        viewModelScope.launch { productRepository.uploadCustomPhoto(barcode, uri) }
    }

    fun removeCustomPhoto() {
        viewModelScope.launch { productRepository.removeCustomPhoto(barcode) }
    }

    fun toggleFavorite() {
        val newValue = !uiState.value.isFavorite
        viewModelScope.launch { inventoryRepository.setFavorite(barcode, newValue) }
    }

    fun removeFromInventory(wasted: Boolean = false) {
        viewModelScope.launch { inventoryRepository.removeFromInventory(barcode, wasted) }
    }

    fun addToShoppingList() {
        val product = uiState.value.product ?: return
        viewModelScope.launch {
            shoppingListRepository.addItem(
                name = product.name,
                category = Category.fromStorageKey(product.category),
                store = "",
                quantity = 1,
                barcode = barcode,
                imageUrl = product.imageUrl,
            )
        }
    }
}
