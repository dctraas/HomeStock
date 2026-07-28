package com.dtraas.boodschapbeheer.data.model

/** The store a shopping list item should be bought at, used to group the list. */
enum class Store(val storageKey: String, val displayName: String, val sortOrder: Int) {
    AH("ah", "Albert Heijn", 0),
    JUMBO("jumbo", "Jumbo", 1),
    NETTORAMA("nettorama", "Nettorama", 2),
    KRUIDVAT("kruidvat", "Kruidvat", 3),
    HEMA("hema", "Hema", 4),
    ETOS("etos", "Etos", 5),
    ACTION("action", "Action", 6),
    GEEN("geen", "Geen winkel", 7);

    companion object {
        fun fromStorageKey(key: String?): Store = entries.find { it.storageKey == key } ?: GEEN
    }
}
