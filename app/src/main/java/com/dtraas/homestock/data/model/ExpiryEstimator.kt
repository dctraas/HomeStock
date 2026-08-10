package com.dtraas.homestock.data.model

import java.util.concurrent.TimeUnit

/**
 * Suggests a default houdbaarheidsdatum for a freshly added inventory item, based on typical
 * shelf life per [Category] — this is the "automatische houdbaarheidsdatum-detectie" feature:
 * not a real read of the date actually printed on the product (that would need a vision model
 * looking at the packaging), just a best-effort category default so most items don't sit with no
 * expiry tracking at all unless the user bothers to open ProductDetailScreen and set one by hand.
 *
 * Only applied once, when [InventoryRepository.recordScan] creates a brand new inventory row —
 * never overwrites a date the user already set (or deliberately cleared) on an existing item, and
 * always stays fully editable/removable afterwards on ProductDetailScreen like any other date.
 */
object ExpiryEstimator {
    /**
     * Typical shelf life in days after purchase, per category. `null` means "too varied to
     * guess anything useful" (household/verzorging products don't really spoil in a tracked
     * sense, and "overig" is too much of a grab-bag) — those categories simply get no suggested
     * date rather than a made-up one.
     */
    private val shelfLifeDays: Map<Category, Int?> = mapOf(
        Category.ZUIVEL to 10,
        Category.GROENTE_FRUIT to 7,
        Category.VLEES_VIS to 3,
        Category.BROOD_BAKKERIJ to 5,
        Category.VOORRAADKAST to 180,
        Category.DIEPVRIES to 270,
        Category.DRANKEN to 180,
        Category.SNOEP_SNACKS to 120,
        Category.HUISHOUDEN to null,
        Category.VERZORGING to null,
        Category.OVERIG to null,
    )

    /** Suggested expiry timestamp for [category], or null when that category isn't worth guessing at. */
    fun estimate(category: Category, fromMillis: Long = System.currentTimeMillis()): Long? {
        val days = shelfLifeDays[category] ?: return null
        return fromMillis + TimeUnit.DAYS.toMillis(days.toLong())
    }
}
