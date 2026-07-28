package com.dtraas.boodschapbeheer.data.model

/**
 * Fixed set of grocery categories used to group inventory and shopping list
 * items. [sortOrder] drives the display order in grouped lists (rather than
 * alphabetical), so groceries appear in a natural aisle-like order.
 */
enum class Category(val storageKey: String, val displayName: String, val sortOrder: Int) {
    ZUIVEL("zuivel", "Zuivel", 0),
    GROENTE_FRUIT("groente_fruit", "Groente & Fruit", 1),
    VLEES_VIS("vlees_vis", "Vlees & Vis", 2),
    BROOD_BAKKERIJ("brood_bakkerij", "Brood & Bakkerij", 3),
    VOORRAADKAST("voorraadkast", "Voorraadkast", 4),
    DIEPVRIES("diepvries", "Diepvries", 5),
    DRANKEN("dranken", "Dranken", 6),
    SNOEP_SNACKS("snoep_snacks", "Snoep & Snacks", 7),
    HUISHOUDEN("huishouden", "Huishouden", 8),
    VERZORGING("verzorging", "Verzorging", 9),
    OVERIG("overig", "Overig", 10);

    companion object {
        fun fromStorageKey(key: String?): Category =
            entries.find { it.storageKey == key } ?: OVERIG
    }
}
