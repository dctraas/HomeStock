package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

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
        // Storage keys from when this category set was briefly split into more,
        // finer-grained ones — kept here so products/items categorized under that
        // scheme (still sitting in Firestore under their finer key, never rewritten)
        // keep resolving to a sensible bucket instead of silently dumping into OVERIG.
        // Each key redirects to the single old bucket it was originally split off of;
        // a few (maaltijden_salades, baby_kind, huisdier) had no old equivalent and
        // fall through to the OVERIG default below instead.
        private val legacyStorageKeys = mapOf(
            "kaas_vleeswaren" to VLEES_VIS,
            "ontbijt_beleg" to VOORRAADKAST,
            "pasta_rijst_wereldkeuken" to VOORRAADKAST,
            "soepen_sauzen_conserven" to VOORRAADKAST,
            "snoep_koek_chocolade" to SNOEP_SNACKS,
            "chips_zoutjes_noten" to SNOEP_SNACKS,
            "frisdrank_sappen" to DRANKEN,
            "bier_wijn" to DRANKEN,
        )

        fun fromStorageKey(key: String?): Category =
            entries.find { it.storageKey == key } ?: legacyStorageKeys[key] ?: OVERIG
    }
}
