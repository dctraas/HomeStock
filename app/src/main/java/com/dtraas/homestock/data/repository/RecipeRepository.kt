package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.remote.TheMealDbApi
import com.dtraas.homestock.data.remote.dto.MealDbDetail
import com.dtraas.homestock.data.remote.dto.MealDbSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * A [meal] suggestion. [matchCount] is how many of the searched-for inventory ingredients it
 * actually uses — null when the list it came from wasn't built from inventory in the first
 * place (see [RecipeRepository.browseAllRecipes]/[RecipeRepository.searchRecipesByName]), as
 * opposed to zero, which would wrongly claim "matches nothing". [matchesArea] is whether it's
 * from the cuisine/region tied to the app's current language.
 */
data class RecipeSuggestion(val meal: MealDbSummary, val matchCount: Int? = null, val matchesArea: Boolean = false)

/**
 * Recipe suggestions based on what's currently in the household's inventory —
 * a Beta feature (see MoreScreen). TheMealDB (the recipe source) only speaks
 * English, while inventory item names are typically Dutch grocery-brand names,
 * so [dutchToEnglishIngredient] is a small, best-effort keyword dictionary
 * bridging the two — not a real translation, just enough overlap to turn
 * "kipfilet" into a "Chicken" search term. Matching is inherently approximate.
 */
class RecipeRepository(
    private val api: TheMealDbApi,
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
) {
    /**
     * Looks at what's in inventory, picks a handful of recognized ingredient terms from it, and
     * returns recipes that use any of them — deduplicated and ranked by how many of those
     * terms each recipe actually uses, most matches first. TheMealDB's free API can only filter
     * by one ingredient per request (no "AND" query), so this approximates "what can I actually
     * cook right now" by counting, per recipe, how many of the separate per-ingredient searches
     * it turned up in — a real ingredient-overlap count, not just "found first". Never throws.
     *
     * [languageTag] (an app-language code like "nl") is mapped to a TheMealDB cuisine/region —
     * see [languageToArea] — and recipes from it are folded into the same candidate pool with a
     * one-point ranking boost (same weight as one ingredient match) and [RecipeSuggestion.matchesArea]
     * set, so recipes matching the household's language surface higher without drowning out
     * genuine ingredient overlap. TheMealDB's free API has no structured allergen data, so
     * [excludedAllergens] filters by keyword-matching each candidate's ingredient names (see
     * [allergenIngredientKeywords]) — approximate, and requires fetching full ingredient details
     * per candidate, so it's capped at the top [MAX_ALLERGEN_CHECKS] ranked candidates rather
     * than checking every result.
     */
    suspend fun suggestRecipes(
        maxSeedIngredients: Int = 5,
        excludedAllergens: Set<Allergen> = emptySet(),
        languageTag: String? = null,
    ): Result<List<RecipeSuggestion>> = try {
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        val seedIngredients = matchDutchIngredients(inventoryNames).take(maxSeedIngredients)

        val meals = LinkedHashMap<String, MealDbSummary>()
        val matchCounts = HashMap<String, Int>()
        val areaMatches = HashSet<String>()
        for (ingredient in seedIngredients) {
            val response = api.filterByIngredient(ingredient)
            response.meals?.forEach { meal ->
                meals.putIfAbsent(meal.id, meal)
                matchCounts[meal.id] = (matchCounts[meal.id] ?: 0) + 1
            }
        }
        languageToArea[languageTag]?.let { area ->
            val response = api.filterByArea(area)
            response.meals?.forEach { meal ->
                meals.putIfAbsent(meal.id, meal)
                matchCounts[meal.id] = (matchCounts[meal.id] ?: 0) + 1
                areaMatches += meal.id
            }
        }

        val ranked = meals.values
            .map { meal -> RecipeSuggestion(meal, matchCounts.getValue(meal.id), meal.id in areaMatches) }
            .sortedByDescending { it.matchCount }

        Result.success(applyAllergenFilter(ranked, excludedAllergens))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Every recipe TheMealDB has (or as close to it as its API allows) — not narrowed by
     * inventory at all, unlike [suggestRecipes]. TheMealDB has no "list everything" endpoint,
     * so this enumerates every category (fetched once via [TheMealDbApi.listCategories]) and
     * fetches each in parallel, merging and deduplicating the results — TheMealDB has a few
     * hundred recipes across roughly a dozen categories, so this is a bounded, one-shot cost
     * rather than something scaling with the recipe count.
     *
     * There's no per-recipe ingredient data in these list responses (only id/name/thumbnail),
     * so unlike [suggestRecipes], [RecipeSuggestion.matchCount] is always null here — showing a
     * fabricated "0 ingredients you have" would be actively misleading. [languageTag] still
     * folds in an area-matched badge the same way (one extra, cheap request), and results are
     * sorted with area matches first, then alphabetically.
     */
    suspend fun browseAllRecipes(
        languageTag: String? = null,
        excludedAllergens: Set<Allergen> = emptySet(),
    ): Result<List<RecipeSuggestion>> = try {
        val categoryNames = api.listCategories().categories.orEmpty().map { it.name }

        val meals = LinkedHashMap<String, MealDbSummary>()
        coroutineScope {
            val perCategory = categoryNames.map { category ->
                async { runCatching { api.filterByCategory(category) }.getOrNull() }
            }
            perCategory.forEach { deferred ->
                deferred.await()?.meals?.forEach { meal -> meals.putIfAbsent(meal.id, meal) }
            }
        }

        val areaMatches = HashSet<String>()
        languageToArea[languageTag]?.let { area ->
            val response = api.filterByArea(area)
            response.meals?.forEach { meal ->
                meals.putIfAbsent(meal.id, meal)
                areaMatches += meal.id
            }
        }

        val list = meals.values
            .map { meal -> RecipeSuggestion(meal, matchCount = null, matchesArea = meal.id in areaMatches) }
            .sortedWith(compareByDescending<RecipeSuggestion> { it.matchesArea }.thenBy { it.meal.name })

        Result.success(applyAllergenFilter(list, excludedAllergens))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Free-text search by recipe name (e.g. typed into RecipesScreen's search field), independent of inventory or category. */
    suspend fun searchRecipesByName(query: String, excludedAllergens: Set<Allergen> = emptySet()): Result<List<RecipeSuggestion>> = try {
        val details = api.searchByName(query).meals.orEmpty()
        val list = details.map { detail ->
            RecipeSuggestion(MealDbSummary(detail.id, detail.name, detail.thumbnailUrl), matchCount = null)
        }
        Result.success(applyAllergenFilter(list, excludedAllergens))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * TheMealDB has no structured allergen data, so this checks each candidate's full
     * ingredient list for keyword matches (see [allergenIngredientKeywords]) — which means
     * fetching full details per candidate, so it's capped at the top [MAX_ALLERGEN_CHECKS]
     * candidates (in whatever order [candidates] is already in) rather than every result. A
     * no-op (candidates returned as-is) when [excludedAllergens] is empty.
     */
    private suspend fun applyAllergenFilter(
        candidates: List<RecipeSuggestion>,
        excludedAllergens: Set<Allergen>,
    ): List<RecipeSuggestion> {
        if (excludedAllergens.isEmpty()) return candidates
        val clean = mutableListOf<RecipeSuggestion>()
        for (suggestion in candidates.take(MAX_ALLERGEN_CHECKS)) {
            val detail = api.lookupMeal(suggestion.meal.id).meals?.firstOrNull() ?: continue
            val hasExcludedAllergen = excludedAllergens.any { allergen -> recipeContainsAllergen(detail, allergen) }
            if (!hasExcludedAllergen) clean.add(suggestion)
        }
        return clean
    }

    suspend fun getRecipeDetail(mealId: String): Result<MealDbDetail> = try {
        val response = api.lookupMeal(mealId)
        val detail = response.meals?.firstOrNull()
        if (detail == null) Result.failure(NoSuchElementException("Meal $mealId not found")) else Result.success(detail)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Which of [detail]'s ingredients look like they're already in inventory, by name. */
    suspend fun matchedIngredients(detail: MealDbDetail): Set<String> {
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        return detail.ingredients
            .map { it.first }
            .filter { ingredient -> inventoryHasIngredient(ingredient, inventoryNames) }
            .toSet()
    }

    /** Adds every ingredient of [detail] that isn't already in inventory to the shopping list. */
    suspend fun addMissingIngredientsToShoppingList(detail: MealDbDetail) {
        addIngredientsToShoppingList(missingIngredients(listOf(detail)))
    }

    /**
     * Ingredient names across all of [details] that aren't already in inventory, deduplicated
     * by name — used by [com.dtraas.homestock.data.repository.MealPlanRepository] to turn a
     * whole week's worth of planned recipes into one combined shopping list instead of one
     * per recipe (so e.g. onion needed by three different days is only listed once).
     */
    suspend fun missingIngredients(details: List<MealDbDetail>): List<String> {
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        val missing = LinkedHashSet<String>()
        for (detail in details) {
            for ((ingredient, _) in detail.ingredients) {
                if (!inventoryHasIngredient(ingredient, inventoryNames)) missing.add(ingredient)
            }
        }
        return missing.toList()
    }

    /**
     * Adds [ingredients] to the shopping list, skipping any that already have an open (unchecked)
     * line there by name — without this, re-running a weekmenu's "genereer boodschappenlijst"
     * after only changing one day would re-add every ingredient the unchanged days already put
     * on the list. Returns how many were actually added.
     */
    suspend fun addIngredientsToShoppingList(ingredients: List<String>): Int {
        val openNames = shoppingListRepository.observeShoppingList().first()
            .filterNot { it.isChecked }
            .map { it.name.lowercase() }
            .toSet()
        var added = 0
        for (ingredient in ingredients) {
            if (ingredient.lowercase() in openNames) continue
            shoppingListRepository.addItem(name = ingredient, category = Category.OVERIG, store = "", quantity = 1)
            added++
        }
        return added
    }

    private fun inventoryHasIngredient(englishIngredient: String, inventoryNames: List<String>): Boolean {
        val dutchTerm = englishToDutchIngredient[englishIngredient.lowercase()]
        return inventoryNames.any { name ->
            val words = tokenize(name)
            words.contains(englishIngredient.lowercase()) || (dutchTerm != null && words.contains(dutchTerm))
        }
    }

    /** Distinct English ingredient search terms recognized in [inventoryNames], in first-seen order. */
    private fun matchDutchIngredients(inventoryNames: List<String>): List<String> {
        val found = LinkedHashSet<String>()
        for (name in inventoryNames) {
            val words = tokenize(name)
            for ((dutchWord, englishTerm) in dutchToEnglishIngredient) {
                if (words.contains(dutchWord)) found.add(englishTerm)
            }
        }
        return found.toList()
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase().split(Regex("[^a-zà-ÿ]+")).filter { it.isNotEmpty() }.toSet()

    /** Best-effort keyword check — TheMealDB has no structured allergen data, just ingredient names. */
    private fun recipeContainsAllergen(detail: MealDbDetail, allergen: Allergen): Boolean {
        val keywords = allergenIngredientKeywords[allergen] ?: return false
        return detail.ingredients.any { (name, _) -> keywords.any { keyword -> name.lowercase().contains(keyword) } }
    }

    companion object {
        // Most common/relevant allergens for everyday recipes, out of the full 14 EU-regulated
        // ones in [Allergen] — the rarer ones (sesame, sulphites, lupin, molluscs, mustard,
        // celery) aren't worth a filter chip here and have no reliable ingredient-keyword tell
        // anyway. Public so RecipesScreen can build its filter chips from the same list.
        val filterableAllergens: List<Allergen> = listOf(
            Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS, Allergen.PEANUTS,
            Allergen.NUTS, Allergen.FISH, Allergen.CRUSTACEANS, Allergen.SOYBEANS,
        )

        private const val MAX_ALLERGEN_CHECKS = 25

        private val allergenIngredientKeywords: Map<Allergen, List<String>> = mapOf(
            Allergen.GLUTEN to listOf(
                "flour", "bread", "pasta", "spaghetti", "noodle", "wheat", "breadcrumb", "tortilla", "cracker", "barley", "oat",
            ),
            Allergen.MILK to listOf("milk", "butter", "cream", "cheese", "yogurt", "yoghurt"),
            Allergen.EGGS to listOf("egg"),
            Allergen.PEANUTS to listOf("peanut"),
            Allergen.NUTS to listOf("almond", "walnut", "pecan", "hazelnut", "cashew", "pistachio", "macadamia"),
            Allergen.FISH to listOf("fish", "salmon", "tuna", "cod", "anchov", "haddock", "trout", "mackerel", "sardine"),
            Allergen.CRUSTACEANS to listOf("shrimp", "prawn", "crab", "lobster", "crayfish"),
            Allergen.SOYBEANS to listOf("soy", "tofu", "edamame"),
        )

        // App-language code -> TheMealDB "Area" (cuisine/region). Best-effort: not every
        // language maps to an unambiguous single cuisine (English -> British, arbitrarily),
        // and TheMealDB's area list doesn't necessarily cover every one of these — an area with
        // no matches there just contributes nothing, not an error.
        private val languageToArea: Map<String, String> = mapOf(
            "nl" to "Dutch",
            "en" to "British",
            "de" to "German",
            "fr" to "French",
            "es" to "Spanish",
        )

        // Single Dutch grocery term -> the closest TheMealDB ingredient search term.
        // Deliberately small and common-staples-only; this is a Beta feature, not a
        // real NL/EN dictionary.
        val dutchToEnglishIngredient: Map<String, String> = mapOf(
            "melk" to "Milk",
            "kaas" to "Cheese",
            "boter" to "Butter",
            "yoghurt" to "Yogurt",
            "room" to "Cream",
            "ei" to "Egg",
            "eieren" to "Egg",
            "kip" to "Chicken",
            "kipfilet" to "Chicken",
            "rundvlees" to "Beef",
            "gehakt" to "Beef",
            "spek" to "Bacon",
            "vis" to "Fish",
            "zalm" to "Salmon",
            "tonijn" to "Tuna",
            "garnalen" to "Shrimp",
            "brood" to "Bread",
            "pasta" to "Pasta",
            "spaghetti" to "Spaghetti",
            "rijst" to "Rice",
            "aardappel" to "Potatoes",
            "aardappelen" to "Potatoes",
            "ui" to "Onion",
            "uien" to "Onion",
            "knoflook" to "Garlic",
            "tomaat" to "Tomato",
            "tomaten" to "Tomato",
            "paprika" to "Bell Pepper",
            "wortel" to "Carrot",
            "wortelen" to "Carrot",
            "courgette" to "Zucchini",
            "champignons" to "Mushroom",
            "spinazie" to "Spinach",
            "sla" to "Lettuce",
            "komkommer" to "Cucumber",
            "appel" to "Apple",
            "banaan" to "Banana",
            "citroen" to "Lemon",
            "sinaasappel" to "Orange",
            "chocolade" to "Chocolate",
            "suiker" to "Sugar",
            "bloem" to "Flour",
            "olijfolie" to "Olive Oil",
            "azijn" to "Vinegar",
            "honing" to "Honey",
            "basilicum" to "Basil",
            "peterselie" to "Parsley",
        )

        val englishToDutchIngredient: Map<String, String> =
            dutchToEnglishIngredient.entries.associate { (dutch, english) -> english.lowercase() to dutch }
    }
}
