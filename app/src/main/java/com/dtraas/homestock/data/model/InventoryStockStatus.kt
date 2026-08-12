package com.dtraas.homestock.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * At-a-glance stock health for an inventory item, driving the colored status dot shown
 * next to it. Priority (most urgent first): no stock at all — regardless of expiration or
 * minimum, since there's nothing to expire or replenish toward — then a set expiration
 * date within [EXPIRING_SOON_THRESHOLD_DAYS] days (or already past), then below the
 * configured minimum quantity, otherwise well-stocked.
 */
enum class InventoryStockStatus {
    OUT_OF_STOCK,
    EXPIRING_SOON,
    LOW_STOCK,
    SUFFICIENT;

    companion object {
        // Matches ExpiryCheckWorker's own notification threshold, so the dot and the
        // "bijna over datum" push notification agree on what counts as "soon".
        private const val EXPIRING_SOON_THRESHOLD_DAYS = 3L

        fun of(quantity: Int, minQuantity: Int?, expirationDate: Long?): InventoryStockStatus {
            if (quantity <= 0) return OUT_OF_STOCK
            if (isExpiringSoon(expirationDate)) return EXPIRING_SOON
            if (isLowStock(quantity, minQuantity)) return LOW_STOCK
            return SUFFICIENT
        }

        /**
         * Standalone version of the "expiring soon" check [of] folds into its single combined
         * status — exposed separately so InventoryViewModel's "Verloopt binnenkort" quick
         * filter can match on it directly, without also excluding an item that's expiring soon
         * *and* below its minimum quantity (which [of] would only ever report as one or the
         * other, by priority).
         */
        fun isExpiringSoon(expirationDate: Long?): Boolean {
            if (expirationDate == null) return false
            val date = Instant.ofEpochMilli(expirationDate).atZone(ZoneOffset.UTC).toLocalDate()
            val daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), date)
            return daysUntilExpiry <= EXPIRING_SOON_THRESHOLD_DAYS
        }

        /** Standalone version of the "low stock" check — see [isExpiringSoon]'s doc for why. */
        fun isLowStock(quantity: Int, minQuantity: Int?): Boolean =
            quantity > 0 && minQuantity != null && quantity < minQuantity
    }
}
