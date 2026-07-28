package com.dtraas.boodschapbeheer.data.model

/** Kind of change recorded in the activity log, shown on the "Wijzigingen" screen. */
enum class ActivityType(val storageKey: String, val label: String) {
    SCANNED("scanned", "Gescand"),
    QUANTITY_CHANGED("quantity_changed", "Aantal aangepast"),
    REMOVED("removed", "Verwijderd uit voorraad"),
    ADDED_TO_SHOPPING_LIST("added_to_shopping_list", "Toegevoegd aan boodschappenlijst");

    companion object {
        fun fromStorageKey(key: String): ActivityType =
            entries.find { it.storageKey == key } ?: QUANTITY_CHANGED
    }
}
