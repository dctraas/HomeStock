package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/**
 * Fixed set of grocery categories used to group inventory and shopping list
 * items. [sortOrder] drives the display order in grouped lists (rather than
 * alphabetical), so groceries appear in a natural aisle-like order.
 */
enum class Category(
    val storageKey: String,
    @StringRes val displayNameRes: Int,
    val sortOrder: Int,
    // Rough, generic shelf life once opened/bought, in days — just enough to seed a one-tap
    // houdbaarheidsdatum suggestion (see ProductDetailScreen.ExpirationRow) for whoever can't
    // be bothered to look at the actual package. Null for categories that aren't really food
    // with a "goes bad" date in the first place (household/personal care) or too broad a mix
    // to guess sensibly (Overig) — no suggestion is offered there rather than a made-up one.
    val defaultShelfLifeDays: Int? = null,
) {
    ZUIVEL("zuivel", R.string.category_zuivel, 0, defaultShelfLifeDays = 7),
    GROENTE_FRUIT("groente_fruit", R.string.category_groente_fruit, 1, defaultShelfLifeDays = 5),
    VLEES_VIS("vlees_vis", R.string.category_vlees_vis, 2, defaultShelfLifeDays = 3),
    BROOD_BAKKERIJ("brood_bakkerij", R.string.category_brood_bakkerij, 3, defaultShelfLifeDays = 4),
    // Displayed as "Kruidenierswaren" ("Dry goods"), not "Voorraadkast" — this is what kind of
    // product it is (pasta/rijst/conserven/beleg), unrelated to [Location.PANTRY]'s "Voorraadkast"
    // option for where in the house something's physically stored. storageKey stays "voorraadkast"
    // (its original, pre-rename value) so already-saved products keep resolving correctly.
    VOORRAADKAST("voorraadkast", R.string.category_voorraadkast, 4, defaultShelfLifeDays = 180),
    DIEPVRIES("diepvries", R.string.category_diepvries, 5, defaultShelfLifeDays = 90),
    DRANKEN("dranken", R.string.category_dranken, 6, defaultShelfLifeDays = 180),
    SNOEP_SNACKS("snoep_snacks", R.string.category_snoep_snacks, 7, defaultShelfLifeDays = 60),
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
