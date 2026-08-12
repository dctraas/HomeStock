package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.remote.observeSnapshots
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** A [meal] suggestion. [matchCount] is how many of the searched-for inventory ingredients it
 * actually uses — null when the list it came from wasn't built from inventory in the first
 * place (see [RecipeRepository.browseAllRecipes]/[RecipeRepository.searchRecipesByName]), as
 * opposed to zero, which would wrongly claim "matches nothing". [matchesArea] is whether it's
 * from the cuisine/region tied to the app's current language.
 */
data class RecipeSuggestion(val meal: RecipeSummary, val matchCount: Int? = null, val matchesArea: Boolean = false)

/** One page of [RecipeRepository.browseAllRecipes]/[RecipeRepository.searchRecipesByName] — [hasMore] mirrors Spoonacular's own `totalResults` for that exact query, so the caller knows whether a further [loadMore]-style call (same params, next `offset`) is worth making. */
data class RecipePage(val suggestions: List<RecipeSuggestion>, val hasMore: Boolean)

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
 * Two more recipe sources live entirely in this household's own Firestore, no Cloud Function
 * involved (nothing to keep server-side, no AI/API cost):
 * - Custom recipes ([saveCustomRecipe]/[observeCustomRecipes]) — hand-entered by the household,
 *   `custom-`-prefixed ids, stored in full so they don't depend on any external database.
 * - Favorites ([addFavorite]/[observeFavoriteRecipes]) — a bookmark on *any* recipe (Spoonacular,
 *   AI-generated, or custom), storing a full [RecipeDetail] snapshot rather than just an id. That
 *   matters most for AI-generated recipes, whose only other home is [detailCache] — gone the
 *   moment the process dies — and it also means opening a favorited Spoonacular recipe never
 *   needs a network round trip either.
 *
 * Inventory item names are typically Dutch grocery-brand names while Spoonacular (like TheMealDB
 * before it) only really understands English ingredient terms, so [dutchToEnglishIngredient] is
 * still a small, best-effort keyword dictionary bridging the two for [suggestRecipes] — not a
 * real translation, just enough overlap to turn "kipfilet" into a "Chicken" search term.
 * [generateRecipe] doesn't need this: Claude is given the raw (Dutch) inventory names directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val householdSession: HouseholdSession,
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
) {
    // Recipes already fetched with full detail (from a search/browse result, a direct detail
    // fetch, or an AI generation) are kept here so opening one doesn't need a second network
    // call/Spoonacular point spend — see [getRecipeDetail]. Deliberately process-lifetime only,
    // not persisted: for real Spoonacular recipes, getRecipeInformation/translateRecipe already
    // persist across app restarts *and* across households on the server side (see
    // functions/src/index.ts's recipeDetailCache/recipeTranslations), which is strictly better
    // than a second, per-household-only local copy — favorites/custom recipes get their own
    // Firestore persistence (see [observeFavoriteRecipes]/[observeCustomRecipes]) for the same
    // reason a plain in-memory cache alone wouldn't survive a restart.
    private val detailCache = ConcurrentHashMap<String, RecipeDetail>()

    private fun customRecipesCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("customRecipes")

    private fun favoriteRecipesCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("favoriteRecipes")

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
     * [suggestRecipes] does, rather than hard-filtering the whole list to one cuisine — only on
     * the first page ([offset] 0): it's a first-glance boost, not something worth re-fetching
     * (and re-deduping against) on every subsequent page.
     *
     * [offset] pages through Spoonacular's own result ordering (see `searchRecipes` in
     * functions/src/index.ts) — call again with `offset = <recipes shown so far>` for a "load
     * more" action, and stop once [RecipePage.hasMore] is false. Spoonacular caps `offset` at
     * 900 itself, so that's the deepest any single filter combination can ever page — there's no
     * way to browse "everything", only progressively further into one popularity-sorted list.
     */
    suspend fun browseAllRecipes(
        languageTag: String? = null,
        excludedAllergens: Set<Allergen> = emptySet(),
        offset: Int = 0,
    ): Result<RecipePage> = try {
        val intolerances = spoonacularIntolerances(excludedAllergens)
        val (plain, hasMore) = parseSearchResults(callSearchRecipes(mode = "browse", number = PAGE_SIZE, offset = offset, intolerances = intolerances))
        plain.forEach { cacheDetail(it) }

        val areaIds = LinkedHashSet<String>()
        if (offset == 0) {
            languageToCuisine[languageTag]?.let { cuisine ->
                val (boosted, _) = parseSearchResults(callSearchRecipes(mode = "browse", cuisine = cuisine, number = 8, intolerances = intolerances))
                boosted.forEach { detail -> cacheDetail(detail); areaIds += detail.id }
            }
        }

        val merged = LinkedHashMap<String, RecipeDetail>()
        plain.forEach { merged.putIfAbsent(it.id, it) }
        areaIds.forEach { id -> detailCache[id]?.let { merged.putIfAbsent(id, it) } }

        val list = merged.values
            .map { detail -> RecipeSuggestion(RecipeSummary(detail.id, detail.name, detail.thumbnailUrl), matchCount = null, matchesArea = detail.id in areaIds) }
            .sortedWith(compareByDescending<RecipeSuggestion> { it.matchesArea }.thenBy { it.meal.name })

        Result.success(RecipePage(withTranslatedTitles(list, languageTag), hasMore))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Free-text search by recipe name (e.g. typed into RecipesScreen's search field), independent
     * of inventory or category. Pages the same way [browseAllRecipes] does — see its doc for how
     * [offset]/[RecipePage.hasMore] work and their 900-result ceiling.
     */
    suspend fun searchRecipesByName(
        query: String,
        excludedAllergens: Set<Allergen> = emptySet(),
        languageTag: String? = null,
        offset: Int = 0,
    ): Result<RecipePage> = try {
        val (details, hasMore) = parseSearchResults(
            callSearchRecipes(mode = "query", query = query, number = PAGE_SIZE, offset = offset, intolerances = spoonacularIntolerances(excludedAllergens)),
        )
        details.forEach { cacheDetail(it) }
        val suggestions = details.map { RecipeSuggestion(RecipeSummary(it.id, it.name, it.thumbnailUrl), matchCount = null) }
        Result.success(RecipePage(withTranslatedTitles(suggestions, languageTag), hasMore))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Fetches one recipe's full detail. Checked in order:
     * 1. [detailCache] — a search/browse/generation already put it there.
     * 2. [customRecipesCollection] when [mealId] is `custom-`-prefixed — the household's own
     *    recipe, source of truth regardless of favorite status.
     * 3. [favoriteRecipesCollection] — covers an AI-generated recipe that outlived the process
     *    (see the class doc) and saves a network call for an already-favorited Spoonacular one.
     * 4. `getRecipeInformation` (always English — see that Cloud Function), for everything else.
     *
     * If [languageTag] isn't English, also fetches/attaches a machine translation (see
     * [translatedDetailIfNeeded]) before returning, so RecipeDetailScreen never has to juggle a
     * separate translation call itself.
     */
    suspend fun getRecipeDetail(mealId: String, languageTag: String? = null): Result<RecipeDetail> {
        detailCache[mealId]?.let { return Result.success(translatedDetailIfNeeded(it, languageTag)) }
        val householdId = householdSession.householdId.value ?: return Result.failure(IllegalStateException("no_household"))

        if (mealId.startsWith(CUSTOM_ID_PREFIX)) {
            return try {
                val snapshot = customRecipesCollection(householdId).document(mealId).get().await()
                val detail = mapFirestoreDocToDetail(snapshot, isCustom = true)
                    ?: return Result.failure(NoSuchElementException("Recipe $mealId not found"))
                cacheDetail(detail)
                Result.success(detail)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        try {
            val favoriteSnapshot = favoriteRecipesCollection(householdId).document(mealId).get().await()
            mapFirestoreDocToDetail(favoriteSnapshot)?.let { detail ->
                cacheDetail(detail)
                return Result.success(translatedDetailIfNeeded(detail, languageTag))
            }
        } catch (e: Exception) {
            // Falls through to the normal Spoonacular fetch below — a favorites lookup hiccup
            // shouldn't stop the recipe from loading the usual way.
        }

        return try {
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

    /** The household's own hand-entered recipes (see [saveCustomRecipe]), alphabetical by name. */
    fun observeCustomRecipes(): Flow<List<RecipeSuggestion>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                customRecipesCollection(householdId).observeSnapshots().map { snapshot ->
                    snapshot.documents
                        .mapNotNull { mapFirestoreDocToDetail(it, isCustom = true) }
                        .sortedBy { it.name.lowercase() }
                        .map { RecipeSuggestion(RecipeSummary(it.id, it.name, it.thumbnailUrl)) }
                }
            }
        }

    /**
     * Creates a new hand-entered recipe when [id] is null, or overwrites an existing one when
     * it isn't (RecipeDetailScreen's edit flow) — either way the result is already in
     * [detailCache], ready for immediate navigation to RecipeDetailScreen without a re-fetch.
     * No thumbnail: this app has no image-upload flow anywhere, so custom recipes simply don't
     * get one rather than needing one built just for this.
     */
    suspend fun saveCustomRecipe(
        id: String?,
        name: String,
        category: String?,
        area: String?,
        readyInMinutes: Int?,
        instructions: String?,
        ingredients: List<Pair<String, String>>,
    ): Result<RecipeDetail> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return Result.failure(IllegalArgumentException("name is required"))
        val householdId = householdSession.householdId.value ?: return Result.failure(IllegalStateException("no_household"))
        val recipeId = id ?: "$CUSTOM_ID_PREFIX${UUID.randomUUID()}"
        val detail = RecipeDetail(
            id = recipeId,
            name = trimmedName,
            thumbnailUrl = null,
            category = category?.trim()?.takeIf { it.isNotEmpty() },
            area = area?.trim()?.takeIf { it.isNotEmpty() },
            instructions = instructions?.trim()?.takeIf { it.isNotEmpty() },
            ingredients = ingredients,
            readyInMinutes = readyInMinutes,
            isCustom = true,
        )
        return try {
            customRecipesCollection(householdId).document(recipeId).set(detailToFirestoreMap(detail)).await()
            // Keeps an already-favorited custom recipe's bookmark showing the freshly edited
            // content instead of a stale snapshot from before this save.
            if (favoriteRecipesCollection(householdId).document(recipeId).get().await().exists()) {
                favoriteRecipesCollection(householdId).document(recipeId).set(detailToFirestoreMap(detail)).await()
            }
            cacheDetail(detail)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomRecipe(id: String) {
        val householdId = householdSession.householdId.value ?: return
        customRecipesCollection(householdId).document(id).delete().await()
        favoriteRecipesCollection(householdId).document(id).delete().await()
        detailCache.remove(id)
    }

    /** Bookmarked recipes (any source — Spoonacular, AI-generated, or custom), alphabetical by name. */
    fun observeFavoriteRecipes(): Flow<List<RecipeSuggestion>> =
        observeFavoriteSnapshots().map { docs ->
            docs.mapNotNull { mapFirestoreDocToDetail(it) }
                .sortedBy { it.name.lowercase() }
                .map { RecipeSuggestion(RecipeSummary(it.id, it.name, it.thumbnailUrl)) }
        }

    /** Cheap membership check for RecipeDetailScreen's favorite toggle — avoids mapping full detail just to know one id's state. */
    fun observeFavoriteIds(): Flow<Set<String>> = observeFavoriteSnapshots().map { docs -> docs.map { it.id }.toSet() }

    private fun observeFavoriteSnapshots(): Flow<List<DocumentSnapshot>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                favoriteRecipesCollection(householdId).observeSnapshots().map { it.documents }
            }
        }

    /** Stores a full snapshot of [detail] as a favorite — see the class doc for why a snapshot rather than just the id. */
    suspend fun addFavorite(detail: RecipeDetail) {
        val householdId = householdSession.householdId.value ?: return
        favoriteRecipesCollection(householdId).document(detail.id).set(detailToFirestoreMap(detail)).await()
    }

    suspend fun removeFavorite(id: String) {
        val householdId = householdSession.householdId.value ?: return
        favoriteRecipesCollection(householdId).document(id).delete().await()
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
     * English [languageTag], an [RecipeDetail.isAiGenerated] or [RecipeDetail.isCustom] recipe
     * (both already in whatever language the household typed/generated them in), a translation
     * already cached for this exact locale, or a translation attempt that failed — English
     * content is still useful.
     */
    private suspend fun translatedDetailIfNeeded(detail: RecipeDetail, languageTag: String?): RecipeDetail {
        if (languageTag == null || languageTag == "en") return detail
        if (detail.isAiGenerated || detail.isCustom) return detail
        if (detail.translatedForLocale == languageTag) return detail
        return try {
            val householdId = householdSession.householdId.value ?: return detail
            val requestData = hashMapOf<String, Any?>(
                "householdId" to householdId,
                "locale" to languageTag,
                "mode" to "detail",
                // Lets the Cloud Function serve this from its cross-household translation cache
                // when another household already translated this exact recipe — see
                // translateRecipe's doc comment in functions/src/index.ts. Omitted entirely for
                // AI-generated/custom recipes since detail.id then starts with "ai-"/"custom-",
                // which the function already treats as "don't cache", but passing null here
                // instead of the id is clearer about intent than relying on that prefix check twice.
                "id" to detail.id,
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
        offset: Int = 0,
    ): Map<*, *> {
        val householdId = householdSession.householdId.value ?: return emptyMap<String, Any?>()
        val requestData = hashMapOf<String, Any?>(
            "householdId" to householdId,
            "mode" to mode,
            "number" to number,
            "offset" to offset,
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

    /** Like [parseDetails], plus the server's `hasMore` flag for [browseAllRecipes]/[searchRecipesByName]'s pagination. */
    private fun parseSearchResults(response: Map<*, *>): Pair<List<RecipeDetail>, Boolean> {
        val details = parseDetails(response)
        val hasMore = (response["hasMore"] as? Boolean) ?: false
        return details to hasMore
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
            calories = (map["calories"] as? Number)?.toDouble(),
            protein = (map["protein"] as? Number)?.toDouble(),
            fat = (map["fat"] as? Number)?.toDouble(),
            carbohydrates = (map["carbohydrates"] as? Number)?.toDouble(),
        )
    }

    /**
     * Maps a `customRecipes`/`favoriteRecipes` Firestore doc back into a [RecipeDetail] — both
     * collections store the same field shape (see [detailToFirestoreMap]), so one mapper covers
     * both. [isCustom] is forced true for reads from `customRecipesCollection`, since a favorited
     * custom recipe's own doc there is the one place that still needs the flag stamped on read
     * rather than trusted from the stored data (a favorite of a *non*-custom recipe correctly
     * carries `isCustom = false` in its own doc already).
     */
    private fun mapFirestoreDocToDetail(doc: DocumentSnapshot, isCustom: Boolean = false): RecipeDetail? {
        val data = doc.data ?: return null
        val name = (data["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return null
        val rawIngredients = data["ingredients"] as? List<*> ?: emptyList<Any?>()
        val ingredients = rawIngredients.mapNotNull { entry ->
            val ingredientMap = entry as? Map<*, *> ?: return@mapNotNull null
            val ingredientName = (ingredientMap["name"] as? String)?.trim().orEmpty()
            if (ingredientName.isEmpty()) return@mapNotNull null
            ingredientName to ((ingredientMap["measure"] as? String)?.trim().orEmpty())
        }
        return RecipeDetail(
            id = doc.id,
            name = name,
            thumbnailUrl = data["thumbnailUrl"] as? String,
            category = data["category"] as? String,
            area = data["area"] as? String,
            instructions = data["instructions"] as? String,
            ingredients = ingredients,
            readyInMinutes = (data["readyInMinutes"] as? Number)?.toInt(),
            calories = (data["calories"] as? Number)?.toDouble(),
            protein = (data["protein"] as? Number)?.toDouble(),
            fat = (data["fat"] as? Number)?.toDouble(),
            carbohydrates = (data["carbohydrates"] as? Number)?.toDouble(),
            isAiGenerated = (data["isAiGenerated"] as? Boolean) ?: false,
            isCustom = isCustom || (data["isCustom"] as? Boolean) ?: false,
        )
    }

    /** Inverse of [mapFirestoreDocToDetail] — only the plain English/original fields, never `translatedX`: a favorite should re-translate fresh next time it's opened, same as any other cached detail. */
    private fun detailToFirestoreMap(detail: RecipeDetail): Map<String, Any?> = mapOf(
        "name" to detail.name,
        "thumbnailUrl" to detail.thumbnailUrl,
        "category" to detail.category,
        "area" to detail.area,
        "instructions" to detail.instructions,
        "ingredients" to detail.ingredients.map { (name, measure) -> mapOf("name" to name, "measure" to measure) },
        "readyInMinutes" to detail.readyInMinutes,
        "calories" to detail.calories,
        "protein" to detail.protein,
        "fat" to detail.fat,
        "carbohydrates" to detail.carbohydrates,
        "isAiGenerated" to detail.isAiGenerated,
        "isCustom" to detail.isCustom,
    )

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
        /** Id prefix for hand-entered recipes (see [saveCustomRecipe]) — lets [getRecipeDetail] route straight to Firestore instead of guessing from a failed Spoonacular lookup. */
        const val CUSTOM_ID_PREFIX = "custom-"

        /** Recipes per page for [browseAllRecipes]/[searchRecipesByName] — also what each "load more" call's `offset` advances by. */
        const val PAGE_SIZE = 20

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
