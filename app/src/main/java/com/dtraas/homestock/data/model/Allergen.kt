package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/**
 * The 14 EU-regulated allergens, matched against Open Food Facts'
 * `allergens_tags` (e.g. "en:gluten"). Anything OFF reports outside this
 * fixed list is ignored — the taxonomy is stable enough that a whitelist is
 * simpler and safer to translate than formatting arbitrary tag slugs.
 */
enum class Allergen(val tag: String, @StringRes val labelRes: Int) {
    GLUTEN("en:gluten", R.string.allergen_gluten),
    CRUSTACEANS("en:crustaceans", R.string.allergen_crustaceans),
    EGGS("en:eggs", R.string.allergen_eggs),
    FISH("en:fish", R.string.allergen_fish),
    PEANUTS("en:peanuts", R.string.allergen_peanuts),
    SOYBEANS("en:soybeans", R.string.allergen_soybeans),
    MILK("en:milk", R.string.allergen_milk),
    NUTS("en:nuts", R.string.allergen_nuts),
    CELERY("en:celery", R.string.allergen_celery),
    MUSTARD("en:mustard", R.string.allergen_mustard),
    SESAME("en:sesame-seeds", R.string.allergen_sesame),
    SULPHITES("en:sulphur-dioxide-and-sulphites", R.string.allergen_sulphites),
    LUPIN("en:lupin", R.string.allergen_lupin),
    MOLLUSCS("en:molluscs", R.string.allergen_molluscs);

    companion object {
        fun fromTags(tags: List<String>): List<Allergen> {
            val tagSet = tags.toSet()
            return entries.filter { it.tag in tagSet }
        }
    }
}
