package com.dtraas.boodschapbeheer.data.model

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
            if (expirationDate != null) {
                val date = Instant.ofEpochMilli(expirationDate).atZone(ZoneOffset.UTC).toLocalDate()
                val daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), date)
                if (daysUntilExpiry <= EXPIRING_SOON_THRESHOLD_DAYS) return EXPIRING_SOON
            }
            if (minQuantity != null && quantity < minQuantity) return LOW_STOCK
            return SUFFICIENT
        }
    }
}
