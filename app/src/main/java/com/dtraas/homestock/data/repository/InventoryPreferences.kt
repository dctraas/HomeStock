package com.dtraas.homestock.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether [InventoryRepository] should automatically re-add a product to the shopping list once
 * its quantity drops below its ingestelde minimum (see
 * [InventoryRepository.maybeRestockOnLowQuantity]) — a per-device setting (Instellingen >
 * App-instellingen), mirroring [NotificationPreferences]/[ThemePreferences]'s persistence
 * pattern. Defaults to true, matching this feature's original always-on behavior.
 */
class InventoryPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoRestockEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RESTOCK, true))
    val autoRestockEnabled: StateFlow<Boolean> = _autoRestockEnabled

    fun setAutoRestockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESTOCK, enabled).apply()
        _autoRestockEnabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "inventory_preferences"
        const val KEY_AUTO_RESTOCK = "auto_restock_enabled"
    }
}
