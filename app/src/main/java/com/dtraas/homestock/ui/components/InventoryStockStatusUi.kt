package com.dtraas.homestock.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.InventoryStockStatus
import com.dtraas.homestock.ui.theme.CoralSecondary
import com.dtraas.homestock.ui.theme.GoldTertiary
import com.dtraas.homestock.ui.theme.SageGreenPrimary

/**
 * Traffic-light color per stock status, used for the status badge on inventory items —
 * the app's own palette (coral/amber/sage) rather than Material's stock red/orange/yellow/
 * green, which didn't match the rest of the "Keukenlinnen" theme and whose yellow
 * ([InventoryStockStatus.LOW_STOCK]'s old `#FDD835`) failed 3:1 contrast against white text.
 * Kept as plain (non-`@Composable`) `Color` constants rather than reading
 * `MaterialTheme.colorScheme` — same reasoning as `TopAppBarContainer` in Color.kt: these are
 * meant to read as fixed brand colors (critical=coral, caution=amber, good=sage) regardless of
 * light/dark theme, not to shift with it.
 */
val InventoryStockStatus.color: Color
    get() = when (this) {
        InventoryStockStatus.OUT_OF_STOCK, InventoryStockStatus.EXPIRING_SOON -> CoralSecondary
        InventoryStockStatus.LOW_STOCK -> GoldTertiary
        InventoryStockStatus.SUFFICIENT -> SageGreenPrimary
    }

/** Legible text/icon color to place on top of [color] — every status color above is dark
 *  enough that white reads fine on all of them, unlike the old Material yellow/orange. */
val InventoryStockStatus.onColor: Color
    get() = Color.White

@get:StringRes
val InventoryStockStatus.labelRes: Int
    get() = when (this) {
        InventoryStockStatus.OUT_OF_STOCK -> R.string.inventory_status_out_of_stock
        InventoryStockStatus.EXPIRING_SOON -> R.string.inventory_status_expiring_soon
        InventoryStockStatus.LOW_STOCK -> R.string.inventory_status_low
        InventoryStockStatus.SUFFICIENT -> R.string.inventory_status_sufficient
    }
