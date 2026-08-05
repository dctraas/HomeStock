package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/**
 * Fixed set of grocery categories used to group inventory and shopping list
 * items — modeled after the aisle breakdown a typical Dutch supermarket app
 * (e.g. Albert Heijn, Jumbo) shows, rather than a small generic bucket list.
 * [sortOrder] drives the display order in grouped lists (rather than
 * alphabetical), so groceries appear in a natural aisle-like order: fresh
 * perimeter first (produce, deli, meat/fish, dairy), then bakery, then
 * center-store aisles (breakfast, pantry, snacks, drinks), then frozen, then
 * non-food last.
 */
enum class Category(val storageKey: String, @StringRes val displayNameRes: Int, val sortOrder: Int) {
    GROENTE_FRUIT("groente_fruit", R.string.category_groente_fruit, 0),
    MAALTIJDEN_SALADES("maaltijden_salades", R.string.category_maaltijden_salades, 1),
    KAAS_VLEESWAREN("kaas_vleeswaren", R.string.category_kaas_vleeswaren, 2),
    VLEES_VIS("vlees_vis", R.string.category_vlees_vis, 3),
    ZUIVEL("zuivel", R.string.category_zuivel, 4),
    BROOD_BAKKERIJ("brood_bakkerij", R.string.category_brood_bakkerij, 5),
    ONTBIJT_BELEG("ontbijt_beleg", R.string.category_ontbijt_beleg, 6),
    PASTA_RIJST_WERELDKEUKEN("pasta_rijst_wereldkeuken", R.string.category_pasta_rijst_wereldkeuken, 7),
    SOEPEN_SAUZEN_CONSERVEN("soepen_sauzen_conserven", R.string.category_soepen_sauzen_conserven, 8),
    SNOEP_KOEK_CHOCOLADE("snoep_koek_chocolade", R.string.category_snoep_koek_chocolade, 9),
    CHIPS_ZOUTJES_NOTEN("chips_zoutjes_noten", R.string.category_chips_zoutjes_noten, 10),
    FRISDRANK_SAPPEN("frisdrank_sappen", R.string.category_frisdrank_sappen, 11),
    BIER_WIJN("bier_wijn", R.string.category_bier_wijn, 12),
    DIEPVRIES("diepvries", R.string.category_diepvries, 13),
    BABY_KIND("baby_kind", R.string.category_baby_kind, 14),
    VERZORGING("verzorging", R.string.category_verzorging, 15),
    HUISHOUDEN("huishouden", R.string.category_huishouden, 16),
    HUISDIER("huisdier", R.string.category_huisdier, 17),
    OVERIG("overig", R.string.category_overig, 18);

    companion object {
        // Storage keys from before this category set was split into more, finer-grained
        // ones — kept here so products/items categorized under the old scheme (still
        // sitting in Firestore under their original key, never rewritten) keep resolving
        // to a sensible bucket instead of silently dumping into OVERIG. The three
        // renamed/merged buckets ("voorraadkast" split into pasta/wereldkeuken, soepen &
        // conserven, and ontbijt & beleg; "dranken" split into frisdrank/koffie/thee and
        // bier/wijn; "snoep_snacks" split into snoep/koek/chocolade and chips/zoutjes/noten)
        // each redirect to the single most representative new category — some previously
        // grouped items may need a manual re-categorization afterward, but nothing is lost.
        private val legacyStorageKeys = mapOf(
            "voorraadkast" to PASTA_RIJST_WERELDKEUKEN,
            "dranken" to FRISDRANK_SAPPEN,
            "snoep_snacks" to SNOEP_KOEK_CHOCOLADE,
        )

        fun fromStorageKey(key: String?): Category =
            entries.find { it.storageKey == key } ?: legacyStorageKeys[key] ?: OVERIG
    }
}
