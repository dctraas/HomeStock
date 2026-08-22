package com.dtraas.homestock.ui.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.ProductRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A handful of common groceries offered as one-tap chips on the "wat staat er nu in je
 *  keuken?" step — [nameRes] doubles as both the chip label and (once resolved to a real
 *  string) the product name saved to the inventory, so the same key drives both. */
enum class StapleId(@StringRes val nameRes: Int, val category: Category) {
    MILK(R.string.onboarding_staple_milk, Category.ZUIVEL),
    BREAD(R.string.onboarding_staple_bread, Category.BROOD_BAKKERIJ),
    EGGS(R.string.onboarding_staple_eggs, Category.ZUIVEL),
    CHEESE(R.string.onboarding_staple_cheese, Category.ZUIVEL),
    BUTTER(R.string.onboarding_staple_butter, Category.ZUIVEL),
    PASTA(R.string.onboarding_staple_pasta, Category.VOORRAADKAST),
    COFFEE(R.string.onboarding_staple_coffee, Category.VOORRAADKAST),
    PRODUCE(R.string.onboarding_staple_produce, Category.GROENTE_FRUIT),
}

class OnboardingViewModel(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val _selectedStaples = MutableStateFlow<Set<StapleId>>(emptySet())
    val selectedStaples: StateFlow<Set<StapleId>> = _selectedStaples

    // Not household-scoped state, just this composition's bookkeeping — the synthetic barcode
    // each tap created, so tapping the same chip again can undo exactly that inventory row
    // rather than guessing which one it was.
    private val barcodesByStaple = mutableMapOf<StapleId, String>()

    /** Tapping a chip seeds it into the inventory right away (no confirmation screen — these
     *  are trusted, predefined staples, not an unverified scan or AI guess); tapping it again
     *  removes that same row, so the selection stays truthful to what's actually in Voorraad. */
    fun toggleStaple(staple: StapleId, name: String) {
        val wasSelected = staple in _selectedStaples.value
        _selectedStaples.value = if (wasSelected) {
            _selectedStaples.value - staple
        } else {
            _selectedStaples.value + staple
        }
        viewModelScope.launch {
            if (wasSelected) {
                barcodesByStaple.remove(staple)?.let { barcode -> inventoryRepository.removeFromInventory(barcode) }
            } else {
                val barcode = "onboarding-${UUID.randomUUID()}"
                barcodesByStaple[staple] = barcode
                productRepository.saveManualProduct(barcode, name, staple.category)
                inventoryRepository.recordScan(barcode, 1, staple.category)
            }
        }
    }
}
