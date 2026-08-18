package com.dtraas.homestock.data.local.entity

/**
 * One "we paid this much for this" observation for a product — per unit, not a line total.
 * Stored newest-first in [ProductEntity.priceHistory], capped at
 * [com.dtraas.homestock.data.repository.ProductRepository.MAX_PRICE_HISTORY] entries. Populated
 * two ways: a shopping list item's own [ShoppingListItemEntity.price], applied the moment it's
 * checked off (see `ShoppingListRepository.setChecked`), or a receipt scan's read-off price (see
 * `ReceiptScanViewModel.confirmAndSave`) — both funnel through
 * `ProductRepository.addPricePoint`, so [ProductEntity.lastPrice] and this history can never
 * drift apart from having two separate write paths.
 */
data class PricePoint(
    val price: Double,
    // The shopping list item's store name at the moment it was checked off, or null when it
    // came from a receipt scan (which doesn't currently read off which store the receipt is
    // from) or had no store set. Plain text, same convention as
    // [ShoppingListItemEntity.store] — not a [com.dtraas.homestock.data.repository.StoreRepository]
    // reference, so a later-deleted custom store never orphans a price entry.
    val store: String?,
    val date: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf("price" to price, "store" to store, "date" to date)

    companion object {
        fun fromMap(map: Map<*, *>): PricePoint? {
            val price = (map["price"] as? Number)?.toDouble() ?: return null
            return PricePoint(
                price = price,
                store = map["store"] as? String,
                date = (map["date"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}
