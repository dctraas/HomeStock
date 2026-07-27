package com.dtraas.boodschp.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.repository.InventoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    val groupedInventory: StateFlow<Map<Category, List<InventoryItemWithProduct>>> =
        inventoryRepository.observeInventoryGroupedByCategory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuantity(barcode: String, quantity: Int) {
        viewModelScope.launch { inventoryRepository.updateQuantity(barcode, quantity) }
    }

    fun removeFromInventory(barcode: String) {
        viewModelScope.launch { inventoryRepository.removeFromInventory(barcode) }
    }
}
