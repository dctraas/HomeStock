package com.dtraas.boodschapbeheer.data.remote

import com.dtraas.boodschapbeheer.data.model.Category

/**
 * Open Food Facts uses a large, multilingual category taxonomy
 * (e.g. "en:dairies", "en:fresh-vegetables"). Rather than mirror that
 * taxonomy, we bucket it into the small fixed [Category] set the app uses
 * for grouping, via keyword matching. First matching bucket wins, checked
 * in order from most to least specific.
 */
object CategoryMapper {

    private val keywordsByCategory: List<Pair<Category, List<String>>> = listOf(
        Category.ZUIVEL to listOf(
            "dairies", "dairy", "milk", "melk", "cheese", "kaas", "yogurt", "yoghurt", "boter", "butter", "cream", "room"
        ),
        Category.GROENTE_FRUIT to listOf(
            "vegetable", "groente", "fruit", "fruits", "fresh-vegetables", "fresh-fruits", "salad", "sla", "aardappel", "potato"
        ),
        Category.VLEES_VIS to listOf(
            "meat", "vlees", "poultry", "kip", "chicken", "beef", "rund", "pork", "varken", "fish", "vis", "seafood", "zeevruchten", "charcuterie"
        ),
        Category.BROOD_BAKKERIJ to listOf(
            "bread", "brood", "bakery", "bakkerij", "pastries", "gebak", "cereal", "granen", "biscuit", "koek"
        ),
        Category.DIEPVRIES to listOf(
            "frozen", "diepvries", "ijs", "ice-cream"
        ),
        Category.DRANKEN to listOf(
            "beverage", "drank", "dranken", "water", "juice", "sap", "soda", "frisdrank", "beer", "bier", "wine", "wijn", "coffee", "koffie", "tea", "thee"
        ),
        Category.SNOEP_SNACKS to listOf(
            "snack", "snoep", "candy", "chocolate", "chocolade", "chips", "crisps", "sweet", "confectionery"
        ),
        Category.HUISHOUDEN to listOf(
            "household", "huishouden", "cleaning", "schoonmaak", "detergent", "wasmiddel"
        ),
        Category.VERZORGING to listOf(
            "hygiene", "verzorging", "cosmetic", "cosmetica", "care", "shampoo", "toothpaste", "tandpasta"
        ),
        Category.VOORRAADKAST to listOf(
            "pantry", "voorraadkast", "pasta", "rice", "rijst", "canned", "conserven", "sauce", "saus", "oil", "olie", "condiment", "spread", "beleg"
        ),
    )

    /**
     * Best-effort guess based on OFF's `categories_tags`/`categories` text and,
     * as a last resort, the free-text product name. Falls back to [Category.OVERIG].
     */
    fun guessCategory(categoriesTags: List<String>?, categoriesText: String?, productName: String?): Category {
        val haystack = buildString {
            categoriesTags?.forEach { append(it).append(' ') }
            categoriesText?.let { append(it).append(' ') }
        }.lowercase()

        keywordsByCategory.forEach { (category, keywords) ->
            if (keywords.any { haystack.contains(it) }) return category
        }

        val nameHaystack = productName?.lowercase().orEmpty()
        keywordsByCategory.forEach { (category, keywords) ->
            if (keywords.any { nameHaystack.contains(it) }) return category
        }

        return Category.OVERIG
    }
}
