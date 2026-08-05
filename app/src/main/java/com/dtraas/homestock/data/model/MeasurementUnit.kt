package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/**
 * Unit a shopping list line's quantity is expressed in, so "aantal" can mean 500 g or 1 L
 * instead of always meaning a piece count. [step] is how much the quantity stepper moves
 * per tap — incrementing grams one at a time isn't useful, so weight/volume units jump by
 * a more sensible amount. Not named `Unit` to avoid shadowing `kotlin.Unit`.
 */
enum class MeasurementUnit(
    val storageKey: String,
    @StringRes val shortLabelRes: Int,
    val step: Int,
    val spaceBeforeLabel: Boolean,
) {
    STUKS("stuks", R.string.unit_stuks, 1, spaceBeforeLabel = true),
    GRAM("gram", R.string.unit_gram, 50, spaceBeforeLabel = false),
    KILOGRAM("kilogram", R.string.unit_kilogram, 1, spaceBeforeLabel = false),
    MILLILITER("milliliter", R.string.unit_milliliter, 100, spaceBeforeLabel = false),
    LITER("liter", R.string.unit_liter, 1, spaceBeforeLabel = false);

    companion object {
        fun fromStorageKey(key: String?): MeasurementUnit = entries.find { it.storageKey == key } ?: STUKS
    }
}
