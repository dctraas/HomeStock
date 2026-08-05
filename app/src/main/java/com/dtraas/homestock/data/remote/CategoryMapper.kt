package com.dtraas.homestock.data.remote

import com.dtraas.homestock.data.model.Category

/**
 * Open Food Facts uses a large, multilingual category taxonomy
 * (e.g. "en:dairies", "en:fresh-vegetables"). Rather than mirror that
 * taxonomy, we bucket it into the small fixed [Category] set the app uses
 * for grouping, via keyword matching. First matching bucket wins, checked
 * in order from most to least specific — broader, catch-all-ish buckets
 * (world cuisine, soups & sauces) are checked last so a more specific
 * category doesn't lose to one of their more generic keywords.
 */
object CategoryMapper {

    private val keywordsByCategory: List<Pair<Category, List<String>>> = listOf(
        Category.KAAS_VLEESWAREN to listOf(
            "cheese", "kaas", "charcuterie", "vleeswaren", "ham", "salami", "worst", "paté", "pate", "tapas"
        ),
        Category.ZUIVEL to listOf(
            "dairies", "dairy", "milk", "melk", "yogurt", "yoghurt", "boter", "butter", "cream", "room", "egg", "eggs", "ei", "eieren"
        ),
        Category.GROENTE_FRUIT to listOf(
            "vegetable", "groente", "fruit", "fruits", "fresh-vegetables", "fresh-fruits", "sla", "aardappel", "potato"
        ),
        Category.VLEES_VIS to listOf(
            "meat", "vlees", "poultry", "kip", "chicken", "beef", "rund", "pork", "varken", "fish", "vis", "seafood", "zeevruchten"
        ),
        Category.BROOD_BAKKERIJ to listOf(
            "bread", "brood", "bakery", "bakkerij", "pastries", "gebak", "banket", "croissant"
        ),
        Category.ONTBIJT_BELEG to listOf(
            "cereal", "granen", "muesli", "ontbijt", "breakfast", "beleg", "jam", "honing", "honey", "peanut-butter", "pindakaas"
        ),
        Category.DIEPVRIES to listOf(
            "frozen", "diepvries", "ice-cream"
        ),
        Category.BIER_WIJN to listOf(
            "beer", "bier", "wine", "wijn", "aperitief", "aperitif", "spirits", "alcohol"
        ),
        Category.FRISDRANK_SAPPEN to listOf(
            "beverage", "water", "juice", "sap", "soda", "frisdrank", "coffee", "koffie", "tea", "thee"
        ),
        Category.SNOEP_KOEK_CHOCOLADE to listOf(
            "candy", "snoep", "chocolate", "chocolade", "sweet", "confectionery", "koek", "biscuit"
        ),
        Category.CHIPS_ZOUTJES_NOTEN to listOf(
            "chips", "crisps", "snack", "zoutjes", "nuts", "noten", "pinda", "peanut"
        ),
        Category.BABY_KIND to listOf(
            "baby", "babyvoeding", "luier", "diaper"
        ),
        Category.HUISDIER to listOf(
            "pet-food", "huisdier", "dog-food", "hondenvoer", "cat-food", "kattenvoer"
        ),
        Category.HUISHOUDEN to listOf(
            "household", "huishouden", "cleaning", "schoonmaak", "detergent", "wasmiddel"
        ),
        Category.VERZORGING to listOf(
            "hygiene", "verzorging", "cosmetic", "cosmetica", "care", "shampoo", "toothpaste", "tandpasta"
        ),
        Category.MAALTIJDEN_SALADES to listOf(
            "salad", "salade", "ready-meal", "maaltijd", "meal", "sandwich", "wrap", "sushi", "pizza"
        ),
        Category.SOEPEN_SAUZEN_CONSERVEN to listOf(
            "soup", "soep", "sauce", "saus", "canned", "conserven", "ketchup", "mayonnaise", "mayonaise"
        ),
        Category.PASTA_RIJST_WERELDKEUKEN to listOf(
            "pasta", "rice", "rijst", "noodle", "noedel", "spices", "kruiden", "oil", "olie", "vinegar", "azijn"
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
