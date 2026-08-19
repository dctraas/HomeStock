package com.dtraas.homestock.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.InventoryStockStatus

/** Traffic-light color per stock status, used for the status badge on inventory items. */
val InventoryStockStatus.color: Color
    get() = when (this) {
        InventoryStockStatus.OUT_OF_STOCK -> Color(0xFFE53935)
        InventoryStockStatus.EXPIRING_SOON -> Color(0xFFFB8C00)
        InventoryStockStatus.LOW_STOCK -> Color(0xFFFDD835)
        InventoryStockStatus.SUFFICIENT -> Color(0xFF43A047)
    }

/** Legible text/icon color to place on top of [color] — the yellow/orange statuses are too
 *  light for white text to sit on, the red/green ones are dark enough that white reads fine. */
val InventoryStockStatus.onColor: Color
    get() = when (this) {
        InventoryStockStatus.LOW_STOCK, InventoryStockStatus.EXPIRING_SOON -> Color(0xFF3D2C08)
        InventoryStockStatus.OUT_OF_STOCK, InventoryStockStatus.SUFFICIENT -> Color(0xFFFFFFFF)
    }

@get:StringRes
val InventoryStockStatus.labelRes: Int
    get() = when (this) {
        InventoryStockStatus.OUT_OF_STOCK -> R.string.inventory_status_out_of_stock
        InventoryStockStatus.EXPIRING_SOON -> R.string.inventory_status_expiring_soon
        InventoryStockStatus.LOW_STOCK -> R.string.inventory_status_low
        InventoryStockStatus.SUFFICIENT -> R.string.inventory_status_sufficient
    }
