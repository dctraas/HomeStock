package com.dtraas.homestock.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.InventoryStockStatus

/** Traffic-light color per stock status, used for the small status dot on inventory items. */
val InventoryStockStatus.color: Color
    get() = when (this) {
        InventoryStockStatus.OUT_OF_STOCK -> Color(0xFFE53935)
        InventoryStockStatus.EXPIRING_SOON -> Color(0xFFFB8C00)
        InventoryStockStatus.LOW_STOCK -> Color(0xFFFDD835)
        InventoryStockStatus.SUFFICIENT -> Color(0xFF43A047)
    }

@get:StringRes
val InventoryStockStatus.labelRes: Int
    get() = when (this) {
        InventoryStockStatus.OUT_OF_STOCK -> R.string.inventory_status_out_of_stock
        InventoryStockStatus.EXPIRING_SOON -> R.string.inventory_status_expiring_soon
        InventoryStockStatus.LOW_STOCK -> R.string.inventory_status_low
        InventoryStockStatus.SUFFICIENT -> R.string.inventory_status_sufficient
    }
