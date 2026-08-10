package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.Category
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** A [meal] suggestion. [matchCount] is how many of the searched-for inventory ingredients it
 * actually uses — null when the list it came from wasn't built from inventory in the first
 * place (see [RecipeRepository.browseAllRecipes]/[RecipeRepository.searchRecipesByName]), as
 * opposed to zero, which would wrongly claim "matches nothing". [matchesArea] is whether it's
 * from the cuisine/region tied to the app's current language.
 */
data class RecipeSuggestion(val meal: RecipeSummary, val matchCount: Int? = null, val matchesArea: Boolean = false)

sealed interface GenerateRecipeResult {
    data class Success(val detail: RecipeDetail) : GenerateRecipeResult

    /** Server re-checked and this household isn't premium (e.g. subscription lapsed mid-session). */
    data object PremiumRequired : GenerateRecipeResult
    data object NoConnection : GenerateRecipeResult
    data object Failed : GenerateRecipeResult
}

/**
 * Recipe search, browsing, and AI generation — a premium feature (see MoreScreen). Backed by
 * Cloud Functions (see `functions/src/index.ts`), all of which keep their respective API key
 * server-side and re-check premium status themselves:
 *
 * - `searchRecipes`/`getRecipeInformation` proxy the Spoonacular recipe database — this used to
 *   call TheMealDB directly from the device (a small, English-only, keyless database); moving
 *   it behind a Cloud Function let it switch to a much larger, keyed database without shipping
 *   that key in the APK, and its `intolerances` param replaces what used to be an approximate
 *   client-side ingredient-keyword allergen filter. Always returns English content.
 * - `generateRecipe` asks Claude Haiku 4.5 to invent a recipe from the household's current
 *   inventory (+ an optional free-text wish) — a complement to the real, tested Spoonacular
 *   recipes above, for "I have no idea what to make with this" moments a fixed database might
 *   not have a good match for. See [RecipeDetail.isAiGenerated].
 * - `translateRecipe` machine-translates Spoonacular's English content into the household's app
 *   language (see [withTranslatedTitles]/[translatedDetailIfNeeded]) — Spoonacular itself is
 *   English-only, unlike TheMealDB-era KitchenPal-style localized databases.
 *
 * Inventory item names are typically Dutch grocery-brand names while Spoonacular (like TheMealDB
 * before it) only really understands English ingredient terms, so [dutchToEnglishIngredient] is
 * still a small, best-effort keyword dictionary bridging the two for [suggestRecipes] — not a
 * real translation, just enough overlap to turn "kipfilet" into a "Chicken" search term.
 * [generateRecipe] doesn't need this: Claude is given the raw (Dutch) inventory names directly.
 */
class RecipeRepository(
    private val functions: FirebaseFunctions,
    private val householdSession: HouseholdSession,
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
) {
    // Recipes already fetched with full detail (from a search/browse result, a direct detail
    // fetch, or an AI generation) are kept here so opening one doesn't need a second network
    // call/Spoonacular point spend — see [getRecipeDetail].
    private val detailCache = ConcurrentHashMap<String, RecipeDetail>()

    /**
     * Looks at what's in inventory, picks a handful of recognized ingredient terms from it, and
     * returns recipes that use any of them (Spoonacular's own used/missed-ingredient ranking),
     * boosted with a handful of recipes from the household's language/cuisine (see
     * [languageToCuisine]) the same way [browseAllRecipes] does. Never throws.
     */
    suspend fun suggestRecipes(
        maxSeedIngredients: Int = 5,
        excludedAllergens: Set<Allergen> = emptySet(),
        languageTag: String? = null,
    ): Result<List<RecipeSuggestion>> = try {
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        val seedIngredients = matchDutchIngredients(inventoryNames).take(maxSeedIngredients)

        val suggestions = LinkedHashMap<String, RecipeSuggestion>()

        if (seedIngredients.isNotEmpty()) {
            val response = callSearchRecipes(mode = "ingredients", ingredients = seedIngredients.joinToString(","), number = 20)
            parseIngredientSummaries(response).forEach { (summary, usedCount) ->
                suggestions[summary.id] = RecipeSuggestion(summary, matchCount = usedCount)
            }
        }

        languageToCuisine[languageTag]?.let { cuisine ->
            val response = callSearchRecipes(
                mode = "browse",
                cuisine = cuisine,
                number = 8,
                intolerances = spoonacularIntolerances(excludedAllergens),
            )
            parseDetails(response).forEach { detail ->
                cacheDetail(detail)
                val existing = suggestions[detail.id]
                suggestions[detail.id] = RecipeSuggestion(
                    meal = RecipeSummary(detail.id, detail.name, detail.thumbnailUrl),
                    matchCount = existing?.matchCount,
                    matchesArea = true,
                )
            }
        }

        val sorted = suggestions.values.sortedByDescending { it.matchCount ?: 0 }
        Result.success(withTranslatedTitles(sorted, languageTag))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Browses Spoonacular's catalog by popularity — not narrowed to inventory at all, unlike
     * [suggestRecipes]. A second, small cuisine-filtered call (see [languageToCuisine]) is
     * merged in as an area-matched badge (see [RecipeSuggestion.matchesArea]) the same way
     * [suggestRecipes] does, rather than hard-filtering the whole list to one cuisine.
     */
    suspend fun browseAllRecipes(
        languageTag: String? = null,
        excludedAllergens: Set<Allergen> = emptySet(),
    ): Result<List<RecipeSuggestion>> = try {
        val intolerances = spoonacularIntolerances(excludedAllergens)
        val plain = parseDetails(callSearchRecipes(mode = "browse", number = 24, intolerances = intolerances))
        plain.forEach { cacheDetail(it) }

        val areaIds = LinkedHashSet<String>()
        languageToCuisine[languageTag]?.let { cuisine ->
            val boosted = parseDetails(callSearchRecipes(mode = "browse", cuisine = cuisine, number = 8, intolerances = intolerances))
            boosted.forEach { detail -> cacheDetail(detail); areaIds += detail.id }
        }

        val merged = LinkedHashMap<String, RecipeDetail>()
        plain.forEach { merged.putIfAbsent(it.id, it) }
        areaIds.forEach { id -> detailCache[id]?.let { merged.putIfAbsent(id, it) } }

        val list = merged.values
            .map { detail -> RecipeSuggestion(RecipeSummary(detail.id, detail.name, detail.thumbnailUrl), matchCount = null, matchesArea = detail.id in areaIds) }
            .sortedWith(compareByDescending<RecipeSuggestion> { it.matchesArea }.thenBy { it.meal.name })

        Result.success(withTranslatedTitles(list, languageTag))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Free-text search by recipe name (e.g. typed into RecipesScreen's search field), independent of inventory or category. */
    suspend fun searchRecipesByName(
        query: String,
        excludedAllergens: Set<Allergen> = emptySet(),
        languageTag: String? = null,
    ): Result<List<RecipeSuggestion>> = try {
        val details = parseDetails(callSearchRecipes(mode = "query", query = query, number = 24, intolerances = spoonacularIntolerances(excludedAllergens)))
        details.forEach { cacheDetail(it) }
        val suggestions = details.map { RecipeSuggestion(RecipeSummary(it.id, it.name, it.thumbnailUrl), matchCount = null) }
        Result.success(withTranslatedTitles(suggestions, languageTag))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Fetches one recipe's full detail — from cache if a search/browse/generation already put it
     * there, otherwise via `getRecipeInformation` (always English — see that Cloud Function). If
     * [languageTag] isn't English, also fetches/attaches a machine translation (see
     * [translatedDetailIfNeeded]) before returning, so RecipeDetailScreen never has to juggle a
     * separate translation call itself.
     */
    suspend fun getRecipeDetail(mealId: String, languageTag: String? = null): Result<RecipeDetail> {
        detailCache[mealId]?.let { return Result.success(translatedDetailIfNeeded(it, languageTag)) }
        return try {
            val householdId = householdSession.householdId.value ?: return Result.failure(IllegalStateException("no_household"))
            val requestData = hashMapOf("householdId" to householdId, "id" to mealId)
            val result = functions.getHttpsCallable("getRecipeInformation").call(requestData).await()
            val response = result.getData() as? Map<*, *> ?: return Result.failure(NoSuchElementException("Recipe $mealId not found"))
            val detail = (response["detail"] as? Map<*, *>)?.let(::mapToDetail)
                ?: return Result.failure(NoSuchElementException("Recipe $mealId not found"))
            cacheDetail(detail)
            Result.success(translatedDetailIfNeeded(detail, languageTag))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Asks Claude Haiku 4.5 (via the `generateRecipe` Cloud Function) to invent a recipe using
     * what's currently in the household's inventory, optionally steered by a free-text [wish]
     * (e.g. "iets met kip en rijst", "Italiaans, vegetarisch"). Unlike the Spoonacular-backed
     * functions above this sends raw (Dutch) inventory names straight to the model — no
     * ingredient-dictionary translation needed, since Claude reads any language directly.
     */
    suspend fun generateRecipe(wish: String?, languageTag: String?): GenerateRecipeResult {
        val householdId = householdSession.householdId.value ?: return GenerateRecipeResult.Failed
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        val requestData = hashMapOf(
            "householdId" to householdId,
            "availableIngredients" to inventoryNames,
            "wish" to wish,
            "locale" to (languageTag ?: Locale.getDefault().language),
        )

        return try {
            val result = functions.getHttpsCallable("generateRecipe").call(requestData).await()
            val response = result.getData() as? Map<*, *> ?: return GenerateRecipeResult.Failed
            val recipe = response["recipe"] as? Map<*, *> ?: return GenerateRecipeResult.Failed
            val detail = mapGeneratedRecipeToDetail(recipe) ?: return GenerateRecipeResult.Failed
            cacheDetail(detail)
            GenerateRecipeResult.Success(detail)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> GenerateRecipeResult.PremiumRequired
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                -> GenerateRecipeResult.NoConnection
                else -> GenerateRecipeResult.Failed
            }
        } catch (e: IOException) {
            GenerateRecipeResult.NoConnection
        } catch (e: Exception) {
            GenerateRecipeResult.Failed
        }
    }

    /** Which of [detail]'s ingredients look like they're already in inventory, by name. */
    suspend fun matchedIngredients(detail: RecipeDetail): Set<String> {
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        return detail.ingredients
            .map { it.first }
            .filter { ingredient -> inventoryHasIngredient(ingredient, inventoryNames) }
            .toSet()
    }

    /** Adds every ingredient of [detail] that isn't already in inventory to the shopping list. */
    suspend fun addMissingIngredientsToShoppingList(detail: RecipeDetail) {
        addIngredientsToShoppingList(missingIngredients(listOf(detail)))
    }

    /**
     * Ingredient names across all of [details] that aren't already in inventory, deduplicated
     * by name — used by the maaltijdplanner to turn a whole week's worth of planned recipes into
     * one combined shopping list instead of one per recipe.
     */
    suspend fun missingIngredients(details: List<RecipeDetail>): List<String> {
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

    private fun cacheDetail(detail: RecipeDetail) {
        detailCache[detail.id] = detail
    }

    /**
     * Translates [suggestions]' titles into [languageTag] via the `translateRecipe` Cloud
     * Function ("titles" mode) — a title-only pass rather than translating full detail, since
     * most list rows never get opened (see `translateRecipe`'s own doc comment). Unlike
     * [RecipeDetail]'s parallel `translatedX` fields, [RecipeSummary.name] is overwritten
     * directly: summaries don't feed the ingredient-matching logic, so there's nothing to keep
     * in English for. Best-effort — returns [suggestions] unchanged on any failure or when
     * [languageTag] is null/English/there's nothing to translate.
     */
    private suspend fun withTranslatedTitles(
        suggestions: List<RecipeSuggestion>,
        languageTag: String?,
    ): List<RecipeSuggestion> {
        if (languageTag == null || languageTag == "en" || suggestions.isEmpty()) return suggestions
        return try {
            val householdId = householdSession.householdId.value ?: return suggestions
            val items = suggestions.map { mapOf("id" to it.meal.id, "name" to it.meal.name) }
            val requestData = hashMapOf<String, Any?>(
                "householdId" to householdId,
                "locale" to languageTag,
                "mode" to "titles",
                "items" to items,
            )
            val result = functions.getHttpsCallable("translateRecipe").call(requestData).await()
            val response = result.getData() as? Map<*, *> ?: return suggestions
            val rawItems = response["items"] as? List<*> ?: return suggestions
            val translatedNameById = rawItems.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"] as? String ?: return@mapNotNull null
                val name = (map["name"] as? String)?.trim().orEmpty()
                if (name.isEmpty()) null else id to name
            }.toMap()
            if (translatedNameById.isEmpty()) return suggestions
            suggestions.map { suggestion ->
                translatedNameById[suggestion.meal.id]?.let { translatedName ->
                    suggestion.copy(meal = suggestion.meal.copy(name = translatedName))
                } ?: suggestion
            }
        } catch (e: Exception) {
            suggestions
        }
    }

    /**
     * Attaches a machine translation of [detail] into [languageTag] (the parallel `translatedX`
     * fields on [RecipeDetail] — never the plain English fields, which
     * [matchedIngredients]/[missingIngredients] depend on) via the `translateRecipe` Cloud
     * Function ("detail" mode), re-caching the translated result so re-opening the same recipe
     * doesn't re-translate it. Returns [detail] unchanged when no translation is needed: no/
     * English [languageTag], an [RecipeDetail.isAiGenerated] recipe (Claude already generates
     * those directly in the target language), a translation already cached for this exact
     * locale, or a translation attempt that failed — English content is still useful.
     */
    private suspend fun translatedDetailIfNeeded(detail: RecipeDetail, languageTag: String?): RecipeDetail {
        if (languageTag == null || languageTag == "en") return detail
        if (detail.isAiGenerated) return detail
        if (detail.translatedForLocale == languageTag) return detail
        return try {
            val householdId = householdSession.householdId.value ?: return detail
            val requestData = hashMapOf<String, Any?>(
                "householdId" to householdId,
                "locale" to languageTag,
                "mode" to "detail",
                "name" to detail.name,
                "category" to detail.category,
                "area" to detail.area,
                "instructions" to detail.instructions,
                "ingredients" to detail.ingredients.map { (name, measure) -> mapOf("name" to name, "measure" to measure) },
            )
            val result = functions.getHttpsCallable("translateRecipe").call(requestData).await()
            val response = result.getData() as? Map<*, *> ?: return detail
            val translated = response["detail"] as? Map<*, *> ?: return detail
            val translatedIngredients = (translated["ingredients"] as? List<*>)?.mapNotNull { entry ->
                val ingredientMap = entry as? Map<*, *> ?: return@mapNotNull null
                val ingredientName = (ingredientMap["name"] as? String)?.trim().orEmpty()
                if (ingredientName.isEmpty()) return@mapNotNull null
                ingredientName to ((ingredientMap["measure"] as? String)?.trim().orEmpty())
            }
            // RecipeDetailScreen zips [RecipeDetail.ingredients] (for inventory matching) with
            // [RecipeDetail.displayIngredients] (for display) by index — only accept the
            // translation if it kept the same ingredient count, so that pairing stays aligned.
            val alignedTranslatedIngredients = translatedIngredients
                ?.takeIf { it.isNotEmpty() && it.size == detail.ingredients.size }
            val translatedDetail = detail.copy(
                translatedForLocale = languageTag,
                translatedName = (translated["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                translatedCategory = (translated["category"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                translatedArea = (translated["area"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                translatedInstructions = (translated["instructions"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                translatedIngredients = alignedTranslatedIngredients,
            )
            cacheDetail(translatedDetail)
            translatedDetail
        } catch (e: Exception) {
            detail
        }
    }

    private suspend fun callSearchRecipes(
        mode: String,
        query: String? = null,
        ingredients: String? = null,
        cuisine: String? = null,
        intolerances: List<String> = emptyList(),
        number: Int,
    ): Map<*, *> {
        val householdId = householdSession.householdId.value ?: return emptyMap<String, Any?>()
        val requestData = hashMapOf<String, Any?>(
            "householdId" to householdId,
            "mode" to mode,
            "number" to number,
        )
        query?.let { requestData["query"] = it }
        ingredients?.let { requestData["ingredients"] = it }
        cuisine?.let { requestData["cuisine"] = it }
        if (intolerances.isNotEmpty()) requestData["intolerances"] = intolerances

        val result = functions.getHttpsCallable("searchRecipes").call(requestData).await()
        return result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
    }

    private fun parseDetails(response: Map<*, *>): List<RecipeDetail> {
        val rawDetails = response["details"] as? List<*> ?: return emptyList()
        return rawDetails.mapNotNull { (it as? Map<*, *>)?.let(::mapToDetail) }
    }

    /** [Pair.second] is Spoonacular's `usedIngredientCount` for that summary — see the "ingredients" mode of `searchRecipes`. */
    private fun parseIngredientSummaries(response: Map<*, *>): List<Pair<RecipeSummary, Int>> {
        val rawSummaries = response["summaries"] as? List<*> ?: return emptyList()
        return rawSummaries.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val id = (map["id"] as? String) ?: return@mapNotNull null
            val name = (map["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val used = (map["usedIngredientCount"] as? Number)?.toInt() ?: 0
            RecipeSummary(id, name, map["thumbnailUrl"] as? String) to used
        }
    }

    private fun mapToDetail(map: Map<*, *>): RecipeDetail? {
        val id = (map["id"] as? String) ?: return null
        val name = (map["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return null
        val rawIngredients = map["ingredients"] as? List<*> ?: emptyList<Any?>()
        val ingredients = rawIngredients.mapNotNull { entry ->
            val ingredientMap = entry as? Map<*, *> ?: return@mapNotNull null
            val ingredientName = (ingredientMap["name"] as? String)?.trim().orEmpty()
            if (ingredientName.isEmpty()) return@mapNotNull null
            ingredientName to ((ingredientMap["measure"] as? String)?.trim().orEmpty())
        }
        return RecipeDetail(
            id = id,
            name = name,
            thumbnailUrl = map["thumbnailUrl"] as? String,
            category = map["category"] as? String,
            area = map["area"] as? String,
            instructions = map["instructions"] as? String,
            ingredients = ingredients,
            readyInMinutes = (map["readyInMinutes"] as? Number)?.toInt(),
        )
    }

    /** Maps `generateRecipe`'s {title, cuisine, estimatedMinutes, ingredients, instructions} shape into the same [RecipeDetail] the rest of the app already knows how to render. */
    private fun mapGeneratedRecipeToDetail(map: Map<*, *>): RecipeDetail? {
        val title = (map["title"] as? String)?.trim().orEmpty()
        if (title.isEmpty()) return null
        val rawIngredients = map["ingredients"] as? List<*> ?: emptyList<Any?>()
        val ingredients = rawIngredients.mapNotNull { entry ->
            val ingredientMap = entry as? Map<*, *> ?: return@mapNotNull null
            val ingredientName = (ingredientMap["name"] as? String)?.trim().orEmpty()
            if (ingredientName.isEmpty()) return@mapNotNull null
            ingredientName to ((ingredientMap["amount"] as? String)?.trim().orEmpty())
        }
        val steps = (map["instructions"] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }.orEmpty()
        val instructions = steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n").takeIf { it.isNotBlank() }
        return RecipeDetail(
            id = "ai-${UUID.randomUUID()}",
            name = title,
            thumbnailUrl = null,
            category = null,
            area = (map["cuisine"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            instructions = instructions,
            ingredients = ingredients,
            readyInMinutes = (map["estimatedMinutes"] as? Number)?.toInt(),
            isAiGenerated = true,
        )
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

    /** Maps the curated allergen subset to Spoonacular's own `intolerances` query values. */
    private fun spoonacularIntolerances(excludedAllergens: Set<Allergen>): List<String> =
        excludedAllergens.mapNotNull { allergenToSpoonacularIntolerance[it] }

    companion object {
        // Most common/relevant allergens for everyday recipes, out of the full 14 EU-regulated
        // ones in [Allergen] — the rarer ones (sesame, sulphites, lupin, molluscs, mustard,
        // celery) aren't worth a filter chip here and don't map cleanly onto Spoonacular's
        // intolerance list anyway. Public so RecipesScreen can build its filter chips from the
        // same list.
        val filterableAllergens: List<Allergen> = listOf(
            Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS, Allergen.PEANUTS,
            Allergen.NUTS, Allergen.FISH, Allergen.CRUSTACEANS, Allergen.SOYBEANS,
        )

        private val allergenToSpoonacularIntolerance: Map<Allergen, String> = mapOf(
            Allergen.GLUTEN to "Gluten",
            Allergen.MILK to "Dairy",
            Allergen.EGGS to "Egg",
            Allergen.PEANUTS to "Peanut",
            Allergen.NUTS to "Tree Nut",
            Allergen.FISH to "Seafood",
            Allergen.CRUSTACEANS to "Shellfish",
            Allergen.SOYBEANS to "Soy",
        )

        // App-language code -> Spoonacular "cuisine" value. Best-effort: Spoonacular's cuisine
        // list doesn't include a dedicated "Dutch" entry, so "nl" falls back to the closest
        // broad match; not every language maps to an unambiguous single cuisine either way.
        private val languageToCuisine: Map<String, String> = mapOf(
            "nl" to "European",
            "en" to "British",
            "de" to "German",
            "fr" to "French",
            "es" to "Spanish",
        )

        // Single Dutch grocery term -> the closest English ingredient search term, used to seed
        // [suggestRecipes]'s "what can I cook" search. Deliberately small and common-staples-
        // only; this is a Beta feature, not a real NL/EN dictionary.
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
