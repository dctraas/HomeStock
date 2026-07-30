package com.dtraas.boodschapbeheer.data.model

import androidx.annotation.StringRes
import com.dtraas.boodschapbeheer.R

/** The store a shopping list item should be bought at, used to group the list. */
enum class Store(val storageKey: String, @StringRes val displayNameRes: Int, val sortOrder: Int) {
    AH("ah", R.string.store_ah, 0),
    JUMBO("jumbo", R.string.store_jumbo, 1),
    NETTORAMA("nettorama", R.string.store_nettorama, 2),
    KRUIDVAT("kruidvat", R.string.store_kruidvat, 3),
    HEMA("hema", R.string.store_hema, 4),
    ETOS("etos", R.string.store_etos, 5),
    ACTION("action", R.string.store_action, 6),
    GEEN("geen", R.string.store_geen, 7);

    companion object {
        fun fromStorageKey(key: String?): Store = entries.find { it.storageKey == key } ?: GEEN
    }
}
