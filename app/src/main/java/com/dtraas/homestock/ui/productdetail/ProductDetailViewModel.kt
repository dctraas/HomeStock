package com.dtraas.homestock.ui.productdetail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
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

/** One household member who has excluded one or more allergens (in their own profile, see
 *  [HouseholdMembersRepository.updateExcludedAllergens]) that this product actually contains —
 *  surfaced on the Voeding tab's "Let op" card so a scan doesn't quietly ignore a housemate's own
 *  restriction just because it wasn't *this* device that set it. */
data class MemberAllergenWarning(val memberName: String, val allergens: Set<Allergen>)

data class ProductDetailUiState(
    val product: ProductEntity? = null,
    val quantityInInventory: Int? = null,
    val expirationDate: Long? = null,
    val minQuantity: Int? = null,
    val note: String? = null,
    val isFavorite: Boolean = false,
    val scanCount: Int = 0,
    val avgDaysBetweenScans: Int? = null,
    val memberAllergenWarnings: List<MemberAllergenWarning> = emptyList(),
    // Every other household member's own name (this device's own member excluded) — the Gegevens
    // editor's "wijzigingen zijn direct zichtbaar voor …" banner uses this to name exactly who
    // else will see a catalog-field edit, rather than a generic "je huishouden".
    val otherMemberNames: List<String> = emptyList(),
    val isLoading: Boolean = true,
)

class ProductDetailViewModel(
    private val barcode: String,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val householdMembersRepository: HouseholdMembersRepository,
) : ViewModel() {

    val uiState: StateFlow<ProductDetailUiState> = combine(
        productRepository.observeProduct(barcode),
        inventoryRepository.observeInventoryItem(barcode),
        inventoryRepository.observeScanHistoryForBarcode(barcode),
        householdMembersRepository.observeMembers(),
    ) { product, inventoryItem, scanHistory, members ->
        // Average interval between scans, spanning the oldest to the newest recorded scan —
        // needs at least two scans to mean anything; one scan alone has no interval to show.
        val avgDaysBetweenScans = if (scanHistory.size >= 2) {
            val oldest = scanHistory.minOf { it.scannedAt }
            val newest = scanHistory.maxOf { it.scannedAt }
            val spanDays = (newest - oldest) / (24 * 60 * 60 * 1000L)
            (spanDays / (scanHistory.size - 1)).toInt().coerceAtLeast(1)
        } else {
            null
        }
        val productAllergens = product?.allergens
            ?.mapNotNullTo(mutableSetOf()) { name -> Allergen.entries.find { it.name == name } }
            .orEmpty()
        val memberAllergenWarnings = if (productAllergens.isEmpty()) {
            emptyList()
        } else {
            members.mapNotNull { member ->
                val matched = member.excludedAllergens intersect productAllergens
                val name = member.displayName?.trim()
                if (matched.isEmpty() || name.isNullOrEmpty()) null else MemberAllergenWarning(name, matched)
            }
        }
        val otherMemberNames = members
            .filterNot { it.isCurrentDevice }
            .mapNotNull { it.displayName?.trim()?.takeIf { name -> name.isNotEmpty() } }
        ProductDetailUiState(
            product = product,
            quantityInInventory = inventoryItem?.quantity,
            expirationDate = inventoryItem?.expirationDate,
            minQuantity = inventoryItem?.minQuantity,
            note = inventoryItem?.note,
            isFavorite = inventoryItem?.isFavorite ?: false,
            scanCount = scanHistory.size,
            avgDaysBetweenScans = avgDaysBetweenScans,
            memberAllergenWarnings = memberAllergenWarnings,
            otherMemberNames = otherMemberNames,
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

    /** Manually records a new price for this product, typed in directly from Voorraad — the
     *  same [ProductRepository.addPricePoint] that checking off a priced shopping list item or a
     *  receipt scan feeds, so this becomes part of the same price history either way. No store is
     *  attached, unlike the shopping list's price entry, since this field doesn't ask which one. */
    fun setPrice(price: Double) {
        viewModelScope.launch { productRepository.addPricePoint(barcode, price, store = null) }
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

    /** See [InventoryRepository.removeQuantityFromInventory] — used instead of
     *  [removeFromInventory] whenever the product's own quantity is more than 1 and the
     *  household said how many were actually used up/wasted, rather than assuming it was all
     *  of them. */
    fun removeQuantityFromInventory(amount: Int, wasted: Boolean) {
        viewModelScope.launch { inventoryRepository.removeQuantityFromInventory(barcode, amount, wasted) }
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
