package com.dtraas.boodschapbeheer.data.model

import androidx.annotation.StringRes
import com.dtraas.boodschapbeheer.R

/**
 * Fixed set of grocery categories used to group inventory and shopping list
 * items. [sortOrder] drives the display order in grouped lists (rather than
 * alphabetical), so groceries appear in a natural aisle-like order.
 */
enum class Category(val storageKey: String, @StringRes val displayNameRes: Int, val sortOrder: Int) {
    ZUIVEL("zuivel", R.string.category_zuivel, 0),
    GROENTE_FRUIT("groente_fruit", R.string.category_groente_fruit, 1),
    VLEES_VIS("vlees_vis", R.string.category_vlees_vis, 2),
    BROOD_BAKKERIJ("brood_bakkerij", R.string.category_brood_bakkerij, 3),
    VOORRAADKAST("voorraadkast", R.string.category_voorraadkast, 4),
    DIEPVRIES("diepvries", R.string.category_diepvries, 5),
    DRANKEN("dranken", R.string.category_dranken, 6),
    SNOEP_SNACKS("snoep_snacks", R.string.category_snoep_snacks, 7),
    HUISHOUDEN("huishouden", R.string.category_huishouden, 8),
    VERZORGING("verzorging", R.string.category_verzorging, 9),
    OVERIG("overig", R.string.category_overig, 10);

    companion object {
        fun fromStorageKey(key: String?): Category =
            entries.find { it.storageKey == key } ?: OVERIG
    }
}
