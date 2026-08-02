package com.dtraas.boodschapbeheer.data.model

import androidx.annotation.StringRes
import com.dtraas.boodschapbeheer.R

/** Kind of change recorded in the activity log, shown on the "Meldingen" screen. */
enum class ActivityType(val storageKey: String, @StringRes val labelRes: Int) {
    SCANNED("scanned", R.string.activity_type_scanned),
    QUANTITY_CHANGED("quantity_changed", R.string.activity_type_quantity_changed),
    REMOVED("removed", R.string.activity_type_removed),
    ADDED_TO_SHOPPING_LIST("added_to_shopping_list", R.string.activity_type_added_to_shopping_list);

    companion object {
        fun fromStorageKey(key: String): ActivityType =
            entries.find { it.storageKey == key } ?: QUANTITY_CHANGED
    }
}
