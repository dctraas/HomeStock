package com.dtraas.homestock.data.repository

import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
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
 * from the cuisine/region tied to the app's current language. [tags] (see [RecipeTag]) is only
 * ever non-empty for [RecipeRepository.observeFavoriteRecipes]/[RecipeRepository.observeCustomRecipes]
 * results — carried here (rather than requiring a full detail fetch) so RecipesViewModel can
 * filter Favorites/Eigen recepten by tag without a network round trip per row.
 */
data class RecipeSuggestion(
    val meal: RecipeSummary,
    val matchCount: Int? = null,
    val matchesArea: Boolean = false,
    val tags: List<String> = emptyList(),
    // Total ingredient count (used + missed) and the full list of missing ingredient names —
    // only ever populated for [RecipeRepository.suggestRecipes]' inventory-matched results,
    // same "null/empty means not applicable here" convention as [matchCount]. Spoonacular's
    // findByIngredients endpoint (the "ingredients" mode of `searchRecipes` in Cloud Functions)
    // is the only call that returns this used/missed breakdown at all.
    val totalIngredientCount: Int? = null,
    val missingIngredients: List<String> = emptyList(),
    // Only ever known when this suggestion came with (or already had cached) a full RecipeDetail
    // — the cuisine/area-boosted half of [RecipeRepository.suggestRecipes] and
    // [RecipeRepository.observeFavoriteRecipes] both carry it, but a plain ingredient-match
    // summary (Spoonacular's findByIngredients response) doesn't include it at all. Null means
    // "unknown", not "not quick" — a "Snel (<20 min)" filter over these should exclude rather
    // than assume for a null value, same reasoning as [matchCount]'s null/zero distinction above.
    val readyInMinutes: Int? = null,
    // Same "only known when a full RecipeDetail was already at hand" caveat as [readyInMinutes].
    val servings: Int? = null,
    // Name of one inventory item close to expiring that this recipe also uses, if any — see
    // [RecipeRepository.expiringIngredientUsedIn]. Populated for [RecipeRepository.browseAllRecipes]/
    // [RecipeRepository.searchRecipesByName] results (full detail, ingredients included, is
    // already fetched for those), not for the lighter ingredient-match half of [suggestRecipes].
    val expiringIngredientUsed: String? = null,
)

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

    // Spoonacular recipe ids for which [getRecipeDetail] has already tried a live
    // recipes/{id}/information re-fetch to fill in a missing bereidingswijze this session — see
    // [needsInstructionsRefetch]'s doc for why that retry exists at all. Without this, a recipe
    // that genuinely has no instructions on Spoonacular's side would bypass the network-savings
    // detailCache check forever, re-fetching from Cloud Functions/Spoonacular on every single
    // reopen of that recipe's detail screen this session instead of just once.
    private val instructionsRefetchAttempted = ConcurrentHashMap.newKeySet<String>()

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
        // Soonest-expiring items first — a household with both "milk" and "salmon" recognized in
        // inventory should get suggestions built from whichever of the two is closer to going
        // off, not whichever happened to sort first in Firestore. Keeps this list's own "use it
        // up" framing honest, and the same ordering RecipesScreen's hero card draws its "3
        // gerechten met X, Y en Z" ingredient names from lines up with what these seed ingredients
        // actually were.
        val inventoryItems = inventoryRepository.observeInventoryWithProduct().first()
            .sortedWith(compareBy(nullsLast()) { it.expirationDate })
        val inventoryNames = inventoryItems.map { it.name }
        val seedIngredients = matchDutchIngredients(inventoryNames).take(maxSeedIngredients)

        val suggestions = LinkedHashMap<String, RecipeSuggestion>()

        if (seedIngredients.isNotEmpty()) {
            val response = callSearchRecipes(mode = "ingredients", ingredients = seedIngredients.joinToString(","), number = 20)
            parseIngredientSummaries(response).forEach { match ->
                suggestions[match.summary.id] = RecipeSuggestion(
                    meal = match.summary,
                    matchCount = match.usedCount,
                    totalIngredientCount = match.usedCount + match.missedCount,
                    missingIngredients = match.missingNames,
                )
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
                cacheSearchResult(detail)
                val existing = suggestions[detail.id]
                suggestions[detail.id] = RecipeSuggestion(
                    meal = RecipeSummary(detail.id, detail.name, detail.thumbnailUrl),
                    matchCount = existing?.matchCount,
                    matchesArea = true,
                    totalIngredientCount = existing?.totalIngredientCount,
                    missingIngredients = existing?.missingIngredients ?: emptyList(),
                    readyInMinutes = detail.readyInMinutes,
                    servings = detail.servings,
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
        plain.forEach { cacheSearchResult(it) }

        val areaIds = LinkedHashSet<String>()
        if (offset == 0) {
            languageToCuisine[languageTag]?.let { cuisine ->
                val (boosted, _) = parseSearchResults(callSearchRecipes(mode = "browse", cuisine = cuisine, number = 8, intolerances = intolerances))
                boosted.forEach { detail -> cacheSearchResult(detail); areaIds += detail.id }
            }
        }

        val merged = LinkedHashMap<String, RecipeDetail>()
        plain.forEach { merged.putIfAbsent(it.id, it) }
        areaIds.forEach { id -> detailCache[id]?.let { merged.putIfAbsent(id, it) } }

        // A whole page's worth of "N/M in huis" ratios and "gebruikt X"-badges (see
        // annotateWithInventory) cost one inventory fetch here, not one per recipe — full detail
        // (ingredients included) is already at hand for every result in [merged].
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        val expiringSoon = fetchExpiringSoon()
        val list = merged.values
            .map { detail ->
                val match = annotateWithInventory(detail, inventoryNames, expiringSoon)
                RecipeSuggestion(
                    meal = RecipeSummary(detail.id, detail.name, detail.thumbnailUrl),
                    matchCount = match.matchCount,
                    totalIngredientCount = match.totalIngredientCount,
                    matchesArea = detail.id in areaIds,
                    readyInMinutes = detail.readyInMinutes,
                    servings = detail.servings,
                    expiringIngredientUsed = match.expiringIngredientUsed,
                )
            }
            // "Gesorteerd op match" — highest ingredient-match ratio first (a recipe with no
            // ingredient info at all, e.g. an empty [RecipeDetail.ingredients], sorts as if it
            // matched nothing rather than being treated as unknown/excluded), then area-matched,
            // then name.
            .sortedWith(
                compareByDescending<RecipeSuggestion> { matchRatio(it) }
                    .thenByDescending { it.matchesArea }
                    .thenBy { it.meal.name },
            )

        Result.success(RecipePage(withTranslatedTitles(list, languageTag), hasMore))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Free-text search by recipe name (e.g. typed into RecipesScreen's search field), independent
     * of inventory or category. Pages the same way [browseAllRecipes] does — see its doc for how
     * [offset]/[RecipePage.hasMore] work and their 900-result ceiling. Also annotated with match/
     * expiring-ingredient info like [browseAllRecipes], but left in Spoonacular's own relevance
     * order rather than re-sorted by match — a household searching by name is looking for that
     * specific recipe, not the best-stocked one.
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
        details.forEach { cacheSearchResult(it) }
        val inventoryNames = inventoryRepository.observeInventoryWithProduct().first().map { it.name }
        val expiringSoon = fetchExpiringSoon()
        val suggestions = details.map { detail ->
            val match = annotateWithInventory(detail, inventoryNames, expiringSoon)
            RecipeSuggestion(
                meal = RecipeSummary(detail.id, detail.name, detail.thumbnailUrl),
                matchCount = match.matchCount,
                totalIngredientCount = match.totalIngredientCount,
                readyInMinutes = detail.readyInMinutes,
                servings = detail.servings,
                expiringIngredientUsed = match.expiringIngredientUsed,
            )
        }
        Result.success(RecipePage(withTranslatedTitles(suggestions, languageTag), hasMore))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** [browseAllRecipes]/[searchRecipesByName]'s per-recipe inventory annotation. */
    private data class InventoryMatch(val matchCount: Int?, val totalIngredientCount: Int?, val expiringIngredientUsed: String?)

    private fun annotateWithInventory(
        detail: RecipeDetail,
        inventoryNames: List<String>,
        expiringSoon: List<InventoryItemWithProduct>,
    ): InventoryMatch {
        if (detail.ingredients.isEmpty()) return InventoryMatch(null, null, null)
        val matched = detail.ingredients.map { it.first }.count { ingredient -> inventoryHasIngredient(ingredient, inventoryNames) }
        return InventoryMatch(matched, detail.ingredients.size, expiringIngredientUsedIn(detail, expiringSoon))
    }

    /** Fraction of [RecipeSuggestion.totalIngredientCount] matched — 0.0 when either is unknown,
     *  same as an actual 0/N match rather than being excluded from a match-ratio sort entirely. */
    private fun matchRatio(suggestion: RecipeSuggestion): Double {
        val total = suggestion.totalIngredientCount?.takeIf { it > 0 } ?: return 0.0
        return (suggestion.matchCount ?: 0).toDouble() / total
    }

    /**
     * Fetches one recipe's full detail. Checked in order:
     * 1. [detailCache] — a search/browse/generation already put it there — *unless*
     *    [needsInstructionsRefetch] flags it as worth a live re-fetch first (see that function's
     *    doc: Spoonacular's bulk browse/search endpoint sometimes comes back without a
     *    bereidingswijze that the dedicated per-recipe endpoint does have).
     * 2. [customRecipesCollection] when [mealId] is `custom-`-prefixed — the household's own
     *    recipe, source of truth regardless of favorite status.
     * 3. [favoriteRecipesCollection] — covers an AI-generated recipe that outlived the process
     *    (see the class doc) and saves a network call for an already-favorited Spoonacular one —
     *    same [needsInstructionsRefetch] check as step 1, since a favorite saved from an
     *    instructions-less browse/search result would otherwise stay stuck that way forever too.
     * 4. `getRecipeInformation` (always English — see that Cloud Function), for everything else,
     *    and where both of the above fall through to when they hit an instructions gap. That
     *    Cloud Function already re-fetches live from Spoonacular itself when *its own* server-
     *    side cache has no instructions either (see its doc comment) — this is the same fix,
     *    just needed again one layer up, since this client-side [detailCache]/Firestore check
     *    happens before that function is ever called at all.
     *
     * If [languageTag] isn't English, also fetches/attaches a machine translation (see
     * [translatedDetailIfNeeded]) before returning, so RecipeDetailScreen never has to juggle a
     * separate translation call itself.
     */
    suspend fun getRecipeDetail(mealId: String, languageTag: String? = null): Result<RecipeDetail> {
        detailCache[mealId]?.let { cached ->
            if (!needsInstructionsRefetch(mealId, cached)) {
                return Result.success(translatedDetailIfNeeded(cached, languageTag))
            }
        }
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
                if (!needsInstructionsRefetch(mealId, detail)) {
                    cacheDetail(detail)
                    return Result.success(translatedDetailIfNeeded(detail, languageTag))
                }
            }
        } catch (e: Exception) {
            // Falls through to the normal Spoonacular fetch below — a favorites lookup hiccup
            // shouldn't stop the recipe from loading the usual way.
        }

        // An AI-generated recipe not found above (not in [detailCache] — e.g. the app cold-
        // started since it was generated — and never favorited) has nowhere left to come from.
        // Spoonacular has never heard of this synthetic id and 404s on it every time (with a
        // genuinely bizarre old-Tomcat error page as the body, logged loudly server-side) —
        // fail cleanly here instead of spending a network round-trip finding that out again.
        // A favorited AI recipe never reaches this point at all (returned above); this only
        // affects one the household generated but didn't save anywhere durable.
        if (mealId.startsWith(AI_ID_PREFIX)) {
            return Result.failure(NoSuchElementException("AI recipe $mealId not found"))
        }

        return try {
            val requestData = hashMapOf("householdId" to householdId, "id" to mealId)
            val result = functions.getHttpsCallable("getRecipeInformation").call(requestData).await()
            val response = result.getData() as? Map<*, *> ?: return Result.failure(NoSuchElementException("Recipe $mealId not found"))
            val detail = (response["detail"] as? Map<*, *>)?.let(::mapToDetail)
                ?: return Result.failure(NoSuchElementException("Recipe $mealId not found"))
            instructionsRefetchAttempted.add(mealId)
            cacheDetail(detail)
            Result.success(translatedDetailIfNeeded(detail, languageTag))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * True when [detail] is a real Spoonacular recipe with no bereidingswijze that hasn't
     * already had a live re-fetch attempted this session. Spoonacular's bulk "browse"/"search"
     * endpoint (`complexSearch` with `addRecipeInformation=true`, what populates [detailCache]
     * and Firestore favorites long before a recipe's own detail screen is ever opened) is known
     * to sometimes come back with an empty `analyzedInstructions`/`instructions` for a recipe
     * whose dedicated `recipes/{id}/information` endpoint (only queried when this returns true)
     * actually has real steps — a genuine inconsistency between Spoonacular's two endpoints, not
     * a bug in how this app parses either response (see `toRecipeDetail` in
     * functions/src/index.ts, which already applies the same `cleanInstructions`/
     * `instructionsFromAnalyzed` fallback to both). Without this check, [getRecipeDetail] would
     * trust that first, possibly-incomplete browse/search snapshot forever and never call
     * `getRecipeInformation` at all for a recipe already seen in a list — which is every recipe
     * a household actually opens, since browsing/searching is how they get to it in the first
     * place.
     *
     * AI-generated/custom recipes are exempt: an empty instructions field there is the
     * household's own real content (or a deliberate lack of it), never something a Spoonacular
     * lookup could fill in. [instructionsRefetchAttempted] caps this to one retry per recipe per
     * session, so a recipe that genuinely has no instructions even after the live re-fetch
     * doesn't keep re-fetching on every reopen.
     */
    private fun needsInstructionsRefetch(id: String, detail: RecipeDetail): Boolean =
        detail.instructions.isNullOrBlank() &&
            !detail.isAiGenerated &&
            !detail.isCustom &&
            id !in instructionsRefetchAttempted

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
                        .map { RecipeSuggestion(RecipeSummary(it.id, it.name, it.thumbnailUrl), tags = it.tags, readyInMinutes = it.readyInMinutes) }
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
        servings: Int?,
        instructions: String?,
        ingredients: List<Pair<String, String>>,
        tags: List<String> = emptyList(),
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
            servings = servings,
            isCustom = true,
            tags = tags,
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
                .map { RecipeSuggestion(RecipeSummary(it.id, it.name, it.thumbnailUrl), tags = it.tags, readyInMinutes = it.readyInMinutes) }
        }

    /** Cheap membership check for RecipeDetailScreen's favorite toggle — avoids mapping full detail just to know one id's state. */
    fun observeFavoriteIds(): Flow<Set<String>> = observeFavoriteSnapshots().map { docs -> docs.map { it.id }.toSet() }

    /**
     * One-time reads of *full* [RecipeDetail]s (ingredients + instructions, not just the summary
     * [observeCustomRecipes]/[observeFavoriteRecipes] give a list row) — built for MoreScreen's
     * Data-overzetten CSV export, which needs everything a recipe actually contains, not just
     * enough to render a row. A plain `.get()` rather than a live listener: an export is a
     * point-in-time snapshot by nature, same reasoning as [fetchWeekPlan]-style reads elsewhere.
     */
    suspend fun fetchAllCustomRecipeDetails(): List<RecipeDetail> {
        val householdId = householdSession.householdId.value ?: return emptyList()
        return customRecipesCollection(householdId).get().await().documents
            .mapNotNull { mapFirestoreDocToDetail(it, isCustom = true) }
    }

    /** See [fetchAllCustomRecipeDetails] — same reasoning, the favorites collection instead. */
    suspend fun fetchAllFavoriteRecipeDetails(): List<RecipeDetail> {
        val householdId = householdSession.householdId.value ?: return emptyList()
        return favoriteRecipesCollection(householdId).get().await().documents
            .mapNotNull { mapFirestoreDocToDetail(it) }
    }

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
     * The heart-toggle convenience a recipe grid tile needs — it only ever has a [RecipeSuggestion]
     * (a [mealId], not a full [RecipeDetail]) to work with. Un-favoriting is just [removeFavorite];
     * favoriting fetches the full detail first (see [getRecipeDetail] — already cached from
     * whatever search/browse produced this tile in the first place, so this is a cache hit, not a
     * fresh network round trip) since [addFavorite] needs the whole snapshot to store.
     */
    suspend fun toggleFavorite(mealId: String, isCurrentlyFavorite: Boolean): Result<Unit> = try {
        if (isCurrentlyFavorite) {
            removeFavorite(mealId)
        } else {
            val detail = getRecipeDetail(mealId).getOrElse { return Result.failure(it) }
            addFavorite(detail)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Persists [tags] (RecipeTag storage keys) on [detail] by writing to whichever of its own
     * collections currently store it — its custom-recipe doc when [RecipeDetail.isCustom], its
     * favorite doc when [isFavorite] — since tags only mean anything on a recipe the household
     * actually kept a durable copy of. A plain, unfavorited Spoonacular browse result has nowhere
     * to persist them, so this is a no-op (success, unchanged) rather than an error in that case;
     * the UI is expected to simply not offer tag editing there in the first place.
     */
    suspend fun setRecipeTags(detail: RecipeDetail, tags: List<String>, isFavorite: Boolean): Result<RecipeDetail> {
        if (!detail.isCustom && !isFavorite) return Result.success(detail)
        val householdId = householdSession.householdId.value ?: return Result.failure(IllegalStateException("no_household"))
        val updated = detail.copy(tags = tags)
        return try {
            if (detail.isCustom) customRecipesCollection(householdId).document(detail.id).update("tags", tags).await()
            if (isFavorite) favoriteRecipesCollection(householdId).document(detail.id).update("tags", tags).await()
            cacheDetail(updated)
            Result.success(updated)
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

    /**
     * Imports one recipe from a household-pasted [url] (via the `importRecipeFromUrl` Cloud
     * Function — see its doc comment in `functions/src/index.ts` for the schema.org-JSON-LD-
     * first, Claude-fallback strategy). Reuses [GenerateRecipeResult]/[mapGeneratedRecipeToDetail]
     * unchanged: the function returns the exact same `{recipe}` shape as `generateRecipe`, and
     * an invalid URL or "no recipe found on that page" both simply surface as [GenerateRecipeResult.Failed]
     * here rather than needing their own result cases.
     *
     * Unlike [generateRecipe], the caller is expected to route the returned [RecipeDetail]
     * straight into [CustomRecipeEditViewModel]'s prefill flow rather than the household's own
     * "eigen recept" — a scraped/AI-extracted result can misparse a step or drop an ingredient in
     * a way an AI recipe generated fresh from a known ingredient list doesn't, so this is never
     * saved on its own; the household reviews/fixes it in the editor before it becomes a real
     * custom recipe.
     */
    suspend fun importRecipeFromUrl(url: String, languageTag: String?): GenerateRecipeResult {
        val householdId = householdSession.householdId.value ?: return GenerateRecipeResult.Failed
        val requestData = hashMapOf(
            "householdId" to householdId,
            "url" to url,
            "locale" to (languageTag ?: Locale.getDefault().language),
        )

        return try {
            val result = functions.getHttpsCallable("importRecipeFromUrl").call(requestData).await()
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

    /**
     * The name of one inventory item close to expiring (within [withinDays]) that [detail] also
     * uses as an ingredient, if any — the maaltijdplanner's "gebruikt spinazie" badge, a concrete
     * nudge that cooking tonight's planned dinner also uses up something about to go off. Same
     * fuzzy English/Dutch matching as [matchedIngredients]; first match by soonest expiry wins.
     */
    suspend fun expiringIngredientUsedIn(detail: RecipeDetail, withinDays: Long = 3): String? =
        expiringIngredientUsedIn(detail, fetchExpiringSoon(withinDays))

    /** Household items expiring within [withinDays], soonest first — split out of
     *  [expiringIngredientUsedIn] so a whole page of recipes (see [browseAllRecipes]) can fetch
     *  this once and check each recipe against it locally, instead of one inventory fetch per
     *  recipe. */
    private suspend fun fetchExpiringSoon(withinDays: Long = 3): List<InventoryItemWithProduct> {
        val now = System.currentTimeMillis()
        val cutoff = now + withinDays * 86_400_000L
        return inventoryRepository.observeInventoryWithProduct().first()
            .filter { it.expirationDate != null && it.expirationDate in now..cutoff }
            .sortedBy { it.expirationDate }
    }

    /** The already-fetched-[expiringSoon] version of [expiringIngredientUsedIn] — first match by
     *  soonest expiry wins. */
    private fun expiringIngredientUsedIn(detail: RecipeDetail, expiringSoon: List<InventoryItemWithProduct>): String? {
        for (item in expiringSoon) {
            val used = detail.ingredients.any { (ingredient, _) -> inventoryHasIngredient(ingredient, listOf(item.name)) }
            if (used) return item.name
        }
        return null
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
     * on the list. Returns the ids of the lines actually added (`.size` is how many, for callers
     * that only need the count) — RecipesScreen's "Op lijst" hero-card action needs the ids
     * themselves so its undo snackbar can remove exactly what it just added, nothing more.
     */
    suspend fun addIngredientsToShoppingList(ingredients: List<String>): List<String> {
        val openNames = shoppingListRepository.observeShoppingList().first()
            .filterNot { it.isChecked }
            .map { it.name.lowercase() }
            .toSet()
        val addedIds = mutableListOf<String>()
        for (ingredient in ingredients) {
            if (ingredient.lowercase() in openNames) continue
            shoppingListRepository.addItem(name = ingredient, category = Category.OVERIG, store = "", quantity = 1)
                ?.let { addedIds.add(it) }
        }
        return addedIds
    }

    private fun cacheDetail(detail: RecipeDetail) {
        detailCache[detail.id] = detail
    }

    /**
     * Caches a search/browse result — [suggestRecipes]/[browseAllRecipes]/[searchRecipesByName]
     * all call this instead of [cacheDetail] directly. Unlike that plain overwrite, this never
     * lets a search result regress an existing, more complete cache entry: Spoonacular's bulk
     * search/browse endpoint is known to sometimes come back with empty instructions for a
     * recipe whose dedicated detail endpoint has real ones (see [needsInstructionsRefetch]'s own
     * doc), and a search result is always English — never carries a translation. Before this
     * guard, simply browsing back to a recipe list and having it re-search in the background
     * would silently clobber whatever [getRecipeDetail] had already cached for a recipe still
     * open (or about to be reopened) on RecipeDetailScreen: its instructions could vanish, or a
     * translation already shown could revert to English — the exact bug behind "this recipe
     * showed fine, then went blank/English again after I browsed back and reopened it." A
     * recipe [detailCache] doesn't have yet, or whose existing entry has neither real
     * instructions nor a translation to lose, still caches normally.
     */
    private fun cacheSearchResult(detail: RecipeDetail) {
        val existing = detailCache[detail.id]
        val existingIsMoreComplete = existing != null &&
            (!existing.instructions.isNullOrBlank() || existing.translatedForLocale != null)
        if (existingIsMoreComplete) return
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
        if (!RECIPE_TRANSLATIONS_ENABLED) return suggestions
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
        if (!RECIPE_TRANSLATIONS_ENABLED) return detail
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

    /** One row of the "ingredients" mode of `searchRecipes` — Spoonacular's used/missed
     *  ingredient breakdown for one recipe against the seed ingredients queried. [missingNames]
     *  is the full list (see functions/src/index.ts) — RecipesScreen's "Op lijst" action needs
     *  every one of them, not just the few it displays inline. */
    private data class IngredientMatch(
        val summary: RecipeSummary,
        val usedCount: Int,
        val missedCount: Int,
        val missingNames: List<String>,
    )

    private fun parseIngredientSummaries(response: Map<*, *>): List<IngredientMatch> {
        val rawSummaries = response["summaries"] as? List<*> ?: return emptyList()
        return rawSummaries.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val id = (map["id"] as? String) ?: return@mapNotNull null
            val name = (map["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val used = (map["usedIngredientCount"] as? Number)?.toInt() ?: 0
            val missed = (map["missedIngredientCount"] as? Number)?.toInt() ?: 0
            val missingNames = (map["missedIngredients"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            IngredientMatch(RecipeSummary(id, name, map["thumbnailUrl"] as? String), used, missed, missingNames)
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
            servings = (map["servings"] as? Number)?.toInt(),
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
            servings = (data["servings"] as? Number)?.toInt(),
            readyInMinutes = (data["readyInMinutes"] as? Number)?.toInt(),
            calories = (data["calories"] as? Number)?.toDouble(),
            protein = (data["protein"] as? Number)?.toDouble(),
            fat = (data["fat"] as? Number)?.toDouble(),
            carbohydrates = (data["carbohydrates"] as? Number)?.toDouble(),
            isAiGenerated = (data["isAiGenerated"] as? Boolean) ?: false,
            isCustom = isCustom || (data["isCustom"] as? Boolean) ?: false,
            tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
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
        "servings" to detail.servings,
        "readyInMinutes" to detail.readyInMinutes,
        "calories" to detail.calories,
        "protein" to detail.protein,
        "fat" to detail.fat,
        "carbohydrates" to detail.carbohydrates,
        "isAiGenerated" to detail.isAiGenerated,
        "isCustom" to detail.isCustom,
        "tags" to detail.tags,
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
            id = "$AI_ID_PREFIX${UUID.randomUUID()}",
            name = title,
            thumbnailUrl = null,
            category = null,
            area = (map["cuisine"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            instructions = instructions,
            ingredients = ingredients,
            servings = (map["servings"] as? Number)?.toInt(),
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

        /** Id prefix for AI-generated recipes (see [mapGeneratedRecipeToDetail]) — [getRecipeDetail]
         *  uses this to recognize a synthetic id it must never pass to Spoonacular (which has never
         *  heard of it and 404s), same reasoning as [CUSTOM_ID_PREFIX]. */
        const val AI_ID_PREFIX = "ai-"

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
