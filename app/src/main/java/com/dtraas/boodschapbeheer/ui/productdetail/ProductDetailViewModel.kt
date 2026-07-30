package com.dtraas.boodschapbeheer.ui.productdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.data.local.entity.ProductEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.Store
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.ProductRepository
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductDetailUiState(
    val product: ProductEntity? = null,
    val quantityInInventory: Int? = null,
    val expirationDate: Long? = null,
    val minQuantity: Int? = null,
    val note: String? = null,
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
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailUiState())

    fun setQuantity(quantity: Int) {
        viewModelScope.launch { inventoryRepository.updateQuantity(barcode, quantity) }
    }

    fun setExpirationDate(expirationDate: Long?) {
        viewModelScope.launch { inventoryRepository.setExpirationDate(barcode, expirationDate) }
    }

    fun setMinQuantity(minQuantity: Int?) {
        viewModelScope.launch { inventoryRepository.setMinQuantity(barcode, minQuantity) }
    }

    fun setNote(note: String?) {
        viewModelScope.launch { inventoryRepository.setNote(barcode, note) }
    }

    fun removeFromInventory() {
        viewModelScope.launch { inventoryRepository.removeFromInventory(barcode) }
    }

    fun addToShoppingList() {
        val product = uiState.value.product ?: return
        viewModelScope.launch {
            shoppingListRepository.addItem(
                name = product.name,
                category = Category.fromStorageKey(product.category),
                store = Store.GEEN,
                quantity = 1,
                barcode = barcode,
                imageUrl = product.imageUrl,
            )
        }
    }
}
