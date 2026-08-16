package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/** Kind of change recorded in the activity log, shown on the "Meldingen" screen. */
enum class ActivityType(val storageKey: String, @StringRes val labelRes: Int) {
    SCANNED("scanned", R.string.activity_type_scanned),
    QUANTITY_CHANGED("quantity_changed", R.string.activity_type_quantity_changed),
    REMOVED("removed", R.string.activity_type_removed),
    ADDED_TO_SHOPPING_LIST("added_to_shopping_list", R.string.activity_type_added_to_shopping_list),
    // A removal the household flagged as food waste rather than "used up" — see
    // ProductDetailScreen's delete-confirm dialog, which only asks the question at all when
    // the product is already expired/near-expiry. Distinct from REMOVED so Statistics can
    // tell "verspild" apart from ordinary consumption.
    WASTED("wasted", R.string.activity_type_wasted);

    companion object {
        fun fromStorageKey(key: String): ActivityType =
            entries.find { it.storageKey == key } ?: QUANTITY_CHANGED
    }
}
