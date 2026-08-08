package com.dtraas.homestock.ui.airecognize

import com.dtraas.homestock.data.model.Category

/**
 * Maps one of ML Kit's on-device Image Labeling labels (a fixed, general-purpose English
 * vocabulary — things like "Food", "Produce", "Bread", "Dairy", "Bottle" — not a specific
 * product or brand name) to the closest fit in this app's fixed [Category] set, so the AI
 * recognition flow (see [AiRecognizeScreen]) can pre-select a sensible category alongside
 * the suggested name. First matching keyword wins; nothing recognized falls back to
 * [Category.OVERIG], same as any other product the categorizer can't place.
 */
private val keywordsByCategory: List<Pair<Category, List<String>>> = listOf(
    Category.ZUIVEL to listOf("dairy", "milk", "cheese", "yogurt", "yoghurt", "butter", "cream"),
    Category.GROENTE_FRUIT to listOf(
        "fruit", "vegetable", "produce", "citrus", "banana", "apple", "tomato", "potato", "berry", "leaf vegetable",
    ),
    Category.VLEES_VIS to listOf("meat", "seafood", "fish", "poultry", "beef", "pork", "chicken", "sausage", "ham"),
    Category.BROOD_BAKKERIJ to listOf("bread", "bakery", "baked goods", "pastry", "bun", "loaf", "bagel", "croissant"),
    Category.DIEPVRIES to listOf("frozen", "ice cream"),
    Category.DRANKEN to listOf(
        "drink", "beverage", "juice", "water bottle", "soda", "wine", "beer", "coffee", "tea", "soft drink",
    ),
    Category.SNOEP_SNACKS to listOf(
        "candy", "chocolate", "snack", "cookie", "biscuit", "chips", "confectionery", "dessert", "cake",
    ),
    Category.VOORRAADKAST to listOf(
        "pasta", "rice", "cereal", "grain", "canned", "soup", "sauce", "condiment", "spice", "cracker", "cooking oil",
    ),
    Category.HUISHOUDEN to listOf("cleaning", "detergent", "paper product", "tissue", "household supply"),
    Category.VERZORGING to listOf("cosmetics", "toiletry", "soap", "shampoo", "personal care", "skin care"),
)

fun suggestCategoryForLabel(label: String): Category {
    val normalized = label.lowercase()
    return keywordsByCategory.firstOrNull { (_, keywords) -> keywords.any { normalized.contains(it) } }
        ?.first
        ?: Category.OVERIG
}
