package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/**
 * Where in the house a product is stored — a fixed set of three, replacing what used to be a
 * free-text field: in practice households converge on the same handful of spots anyway, and a
 * fixed set groups/filters consistently (see InventoryViewModel's groupedByLocation/filter)
 * instead of "Vriezer" and "vriezer" silently becoming two different buckets.
 *
 * [storageKey] persists locale-independent, the same convention as [Category]/[Allergen]/etc.
 * [ProductRepository.updateLocation] still accepts a plain nullable String rather than a
 * [Location] directly, so a value stored before this change (free text, from the original
 * text-field version of this feature) keeps displaying as typed — see [fromStorageKey] returning
 * null for anything that doesn't match one of these three keys, with callers falling back to the
 * raw stored string instead of silently blanking it.
 */
enum class Location(val storageKey: String, @StringRes val labelRes: Int) {
    PANTRY("voorraadkast", R.string.location_pantry),
    CELLAR("kelder", R.string.location_cellar),
    FREEZER("vriezer", R.string.location_freezer);

    companion object {
        fun fromStorageKey(key: String?): Location? = entries.find { it.storageKey == key }
    }
}
