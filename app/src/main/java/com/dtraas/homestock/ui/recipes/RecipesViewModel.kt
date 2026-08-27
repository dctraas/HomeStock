package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.RecipeTag
import com.dtraas.homestock.data.repository.GenerateRecipeResult
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.RecipePage
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.data.repository.ShoppingListRepository
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GenerateRecipeError { NO_CONNECTION, PREMIUM_REQUIRED, UNKNOWN }

/** Kept separate from [GenerateRecipeError] (structurally identical) rather than reused — the
 *  two dialogs read different copy ("kon geen recept genereren" vs. "kon geen recept
 *  importeren"), and this way each can evolve its own cases independently later (e.g. a
 *  distinct "geen recept gevonden op deze pagina" case for import, which generation has no
 *  equivalent of). */
enum class ImportRecipeError { NO_CONNECTION, PREMIUM_REQUIRED, UNKNOWN }

/** Which recipe source RecipesScreen is currently showing — see [RecipesViewModel.selectTab].
 *  [INVENTORY] ("Uit je voorraad") is the default tab per the design review — what used to be
 *  the "Kook wat je hebt" promo card on [BROWSE] ("Ontdekken") is now this tab's own content
 *  instead of an opt-in banner. [AI] replaces what used to be a floating action button opening
 *  a "Recept bedenken" bottom sheet — it has no recipe list of its own (see
 *  [RecipesViewModel.refreshCurrentTab]), just that same form as a persistent tab instead. */
enum class RecipesTab { INVENTORY, BROWSE, FAVORITES, CUSTOM, AI }

/** Why a browse/search load failed — [QUOTA_EXCEEDED] gets its own, more accurate message
 *  instead of being lumped in with [NO_CONNECTION] (see `spoonacularGet` in
 *  functions/src/index.ts, which is what actually tells these apart). */
enum class RecipesLoadError { NO_CONNECTION, QUOTA_EXCEEDED, UNKNOWN }

data class RecipesUiState(
    val tab: RecipesTab = RecipesTab.BROWSE,
    val isLoading: Boolean = true,
    val recipes: List<RecipeSuggestion> = emptyList(),
    val loadError: RecipesLoadError? = null,
    val excludedAllergens: Set<Allergen> = emptySet(),
    // Free-text labels households typed themselves (see RecipeDetailScreen's tag editor) — only
    // meaningful for FAVORITES/CUSTOM (see RecipesViewModel.launchLiveList); BROWSE/search results
    // never carry tags at all (see RecipeSuggestion's doc), so this filter simply isn't shown on
    // that tab. [availableCustomTags] is derived from the *unfiltered* Favorites/Custom list (see
    // launchLiveList) so picking one filter doesn't hide the others.
    val availableCustomTags: List<String> = emptyList(),
    val selectedCustomTags: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isGenerating: Boolean = false,
    val generateError: GenerateRecipeError? = null,
    val isImporting: Boolean = false,
    val importError: ImportRecipeError? = null,
    /** Whether a further [RecipesViewModel.loadMore] call would return anything — mirrors Spoonacular's own `totalResults` for the current browse/search query (see [RecipeRepository.browseAllRecipes]). Only ever true for [RecipesTab.BROWSE]: Favorites/Custom are short, fully-loaded live lists. */
    val hasMore: Boolean = false,
    /** True only while a "load more" page is in flight — distinct from [isLoading], which covers the *first* page of a fresh browse/search so the existing list can stay visible (with a small footer spinner) while more loads. */
    val isLoadingMore: Boolean = false,
    /** Every currently-favorited meal id — collected continuously (see [RecipesViewModel.load]),
     *  independent of which tab is showing, so the heart overlay on a BROWSE/CUSTOM/INVENTORY
     *  grid tile (see RecipeGridTile) reflects favorite state without that tab being FAVORITES
     *  itself. */
    val favoriteIds: Set<String> = emptySet(),
)

/**
 * [RecipesTab.BROWSE] ("Ontdekken") is the default tab — browses Spoonacular's catalog by
 * default (see [RecipeRepository.browseAllRecipes]) — [search] switches it to a name search
 * instead (see [RecipeRepository.searchRecipesByName]) when [RecipesUiState.searchQuery] is
 * non-blank. Its result is paginated (see [loadMore]) — a fresh browse/search always starts at
 * page 1, "load more" fetches the next page and appends it. [generateRecipe] is a separate,
 * AI-authored alternative (see [RecipeRepository.generateRecipe]) rather than a search at all.
 * [RecipesTab.INVENTORY] ("Uit je voorraad") is the fourth tab — [RecipeRepository.suggestRecipes]'s
 * inventory-matched results, not paginated (Spoonacular's own ranking already returns its best
 * matches in one page).
 *
 * [RecipesTab.FAVORITES]/[RecipesTab.CUSTOM] are simple live Firestore lists (see
 * [RecipeRepository.observeFavoriteRecipes]/[RecipeRepository.observeCustomRecipes]) — no search,
 * allergen filter, or language boost; those only make sense against Spoonacular's much bigger
 * catalog, not a household's own short personal list.
 */
class RecipesViewModel(
    private val recipeRepository: RecipeRepository,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val shoppingListRepository: ShoppingListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState

    // Seeded once, the first time load() runs — not kept live — from every household member's
    // own saved allergen preferences (see HouseholdSettingsScreen's "Mijn allergenen"), so
    // recipe suggestions steer clear of a housemate's allergy by default without anyone having
    // to remember to toggle it by hand every time. Once seeded, the per-session toggleAllergen
    // filter is free to add/remove on top without a housemate's later preference change (which
    // would arrive as a new emission from the same flow) silently overwriting it mid-session.
    private var hasSeededHouseholdAllergens = false

    /** Emits the newly generated recipe's id once [generateRecipe] succeeds — the screen navigates to RecipeDetailScreen with it. */
    private val _generatedRecipeId = MutableSharedFlow<String>()
    val generatedRecipeId: SharedFlow<String> = _generatedRecipeId

    /** Emits the imported draft's (temporary, cache-only) id once [importRecipeFromUrl]
     *  succeeds — the screen navigates to CustomRecipeEditScreen's importId flow with it,
     *  unlike [generatedRecipeId] which goes straight to RecipeDetailScreen (see
     *  [RecipeRepository.importRecipeFromUrl]'s doc for why an import needs review first). */
    private val _importedRecipeId = MutableSharedFlow<String>()
    val importedRecipeId: SharedFlow<String> = _importedRecipeId

    /** Emitted by [addMissingIngredientsToShoppingList] once it actually adds something — the
     *  screen surfaces this as an undo snackbar naming the recipe, [itemIds] going straight to
     *  [undoAddMissingIngredients] if tapped. */
    data class MissingIngredientsAddedEvent(val recipeName: String, val itemIds: List<String>)

    private val _missingIngredientsAdded = MutableSharedFlow<MissingIngredientsAddedEvent>()
    val missingIngredientsAdded: SharedFlow<MissingIngredientsAddedEvent> = _missingIngredientsAdded

    // Remembered from the last load() call so search()/toggleAllergen()/generateRecipe() don't
    // need the caller (RecipesScreen) to keep threading the current app language through every action.
    private var languageTag: String? = null

    // Mirrors RecipesUiState.selectedCustomTags — a separate flow (rather than deriving from
    // _uiState itself) so launchLiveList's combine() below only re-filters on an actual
    // tag-filter change, not on every unrelated uiState update (e.g. isLoading toggling).
    private val selectedCustomTags = MutableStateFlow<Set<String>>(emptySet())

    // Whichever tab's list is currently being collected — cancelled and replaced on every tab
    // switch/reload so a stale Favorites/Custom Firestore listener (or an in-flight Spoonacular
    // call) from before a switch can't race a newer one and overwrite it with older data.
    private var listJob: Job? = null

    // The `offset` a further [loadMore] call should ask for next — always a multiple of
    // [RecipeRepository.PAGE_SIZE], incremented after each successful page regardless of how
    // many suggestions that page actually rendered (page 1's cuisine-boost extras don't count,
    // since [RecipeRepository.browseAllRecipes] only ever requests them for offset 0 — see its
    // doc). Reset to 0 by every fresh browse/search in [launchBrowseOrSearch]. Only meaningful
    // for [RecipesTab.BROWSE]; Favorites/Custom don't page at all.
    private var nextOffset = 0

    /** [languageTag] (e.g. "nl") drives the cuisine/region boost in RecipeRepository — see its doc. Refreshes whichever tab is currently selected. */
    fun load(languageTag: String? = null) {
        this.languageTag = languageTag
        if (hasSeededHouseholdAllergens) {
            refreshCurrentTab()
        } else {
            hasSeededHouseholdAllergens = true
            viewModelScope.launch {
                val householdDefaults = householdMembersRepository.observeHouseholdExcludedAllergens().first()
                if (householdDefaults.isNotEmpty()) _uiState.update { it.copy(excludedAllergens = householdDefaults) }
                refreshCurrentTab()
            }
            // Own long-lived collection, not tied to listJob/refreshCurrentTab — favorite state
            // needs to keep updating no matter which tab is currently showing (see
            // RecipesUiState.favoriteIds' doc), unlike the tab-specific recipe lists that get
            // cancelled and replaced on every switch.
            viewModelScope.launch {
                recipeRepository.observeFavoriteIds().collect { ids ->
                    _uiState.update { it.copy(favoriteIds = ids) }
                }
            }
        }
    }

    /** Toggles [mealId]'s favorite state — used by the heart overlay on grid tiles across every
     *  tab (see [RecipesUiState.favoriteIds]), not just FAVORITES itself. No local optimistic
     *  update needed: [RecipesUiState.favoriteIds] already tracks Firestore live, so the heart
     *  flips back on its own the moment [RecipeRepository.toggleFavorite] resolves. */
    fun toggleFavorite(mealId: String) {
        val isCurrentlyFavorite = mealId in _uiState.value.favoriteIds
        viewModelScope.launch {
            recipeRepository.toggleFavorite(mealId, isCurrentlyFavorite)
        }
    }

    fun selectTab(tab: RecipesTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab, recipes = emptyList(), hasMore = false, isLoadingMore = false) }
        refreshCurrentTab()
    }

    private fun refreshCurrentTab() {
        listJob?.cancel()
        listJob = when (_uiState.value.tab) {
            RecipesTab.INVENTORY -> launchInventoryTab()
            RecipesTab.BROWSE -> launchBrowseOrSearch()
            RecipesTab.FAVORITES -> launchLiveList(recipeRepository::observeFavoriteRecipes)
            RecipesTab.CUSTOM -> launchLiveList(recipeRepository::observeCustomRecipes)
            // No list to load — RecipesScreen shows the "Recept bedenken" form instead, see
            // RecipesTab.AI's doc. Still clears isLoading in case a previous tab's fetch was
            // still in flight when this tab was selected.
            RecipesTab.AI -> null.also { _uiState.update { state -> state.copy(isLoading = false) } }
        }
    }

    /** Favorites/Custom are further filtered client-side by [selectedCustomTags] (an AND match —
     *  a recipe must carry every selected custom label) — small, already-loaded lists, so no
     *  need for a separate Firestore query per tag combination the way BROWSE's allergen filter
     *  needs one. [RecipesUiState.availableCustomTags] is derived here from the unfiltered
     *  [list], not the filtered result, so narrowing by one custom tag doesn't make the others
     *  disappear from the filter row. [RecipeTag.fromStorageKey] filters out any now-retired
     *  preset key (Snel/Kindvriendelijk/Restjes) a recipe tagged before their removal might still
     *  carry, so it can't resurface as a garbled custom-looking chip. */
    private fun launchLiveList(source: () -> Flow<List<RecipeSuggestion>>): Job =
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            combine(source(), selectedCustomTags) { list, customTags ->
                val available = list.flatMap { it.tags }.filter { RecipeTag.fromStorageKey(it) == null }.distinct().sorted()
                val filtered = list.filter { recipe -> customTags.all { it in recipe.tags } }
                available to filtered
            }.collect { (available, filtered) ->
                _uiState.update {
                    it.copy(isLoading = false, recipes = filtered, loadError = null, availableCustomTags = available)
                }
            }
        }

    private fun launchBrowseOrSearch(): Job = viewModelScope.launch {
        nextOffset = 0
        _uiState.update { it.copy(isLoading = true, loadError = null, hasMore = false, isLoadingMore = false) }
        fetchPage(_uiState.value, offset = 0)
            .onSuccess { page ->
                nextOffset = RecipeRepository.PAGE_SIZE
                _uiState.update { it.copy(isLoading = false, recipes = page.suggestions, loadError = null, hasMore = page.hasMore) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, recipes = emptyList(), loadError = classifyLoadError(e), hasMore = false) }
            }
    }

    /**
     * Fetches and appends the next page of the current browse/search — a no-op unless
     * [RecipesTab.BROWSE] is showing, [RecipesUiState.hasMore] is true, and nothing's already
     * loading (a fresh page-1 load or a page already in flight). Call from the list's "load
     * more"/near-the-bottom trigger in RecipesScreen.
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.tab != RecipesTab.BROWSE || !state.hasMore || state.isLoading || state.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetchPage(state, offset = nextOffset)
                .onSuccess { page ->
                    nextOffset += RecipeRepository.PAGE_SIZE
                    _uiState.update { current ->
                        val existingIds = current.recipes.map { it.meal.id }.toSet()
                        val appended = current.recipes + page.suggestions.filterNot { it.meal.id in existingIds }
                        current.copy(isLoadingMore = false, recipes = appended, hasMore = page.hasMore)
                    }
                }
                .onFailure { _uiState.update { it.copy(isLoadingMore = false) } }
        }
    }

    private suspend fun fetchPage(state: RecipesUiState, offset: Int): Result<RecipePage> =
        if (state.searchQuery.isNotBlank()) {
            recipeRepository.searchRecipesByName(state.searchQuery.trim(), state.excludedAllergens, languageTag, offset)
        } else {
            recipeRepository.browseAllRecipes(languageTag, state.excludedAllergens, offset)
        }

    /** Distinguishes "Spoonacular's quota is used up, try later" from a real connectivity
     *  problem — see `spoonacularGet`'s `resource-exhausted` throw in functions/src/index.ts,
     *  the only place this distinction is actually made. */
    private fun classifyLoadError(e: Throwable): RecipesLoadError = when {
        e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> RecipesLoadError.QUOTA_EXCEEDED
        e is FirebaseFunctionsException && (
            e.code == FirebaseFunctionsException.Code.UNAVAILABLE || e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
        ) -> RecipesLoadError.NO_CONNECTION
        e is IOException -> RecipesLoadError.NO_CONNECTION
        else -> RecipesLoadError.UNKNOWN
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Runs the currently typed search query — call on the search field's IME action, not on every keystroke. */
    fun search() {
        listJob?.cancel()
        listJob = launchBrowseOrSearch()
    }

    /** Clears the search field and immediately goes back to browsing everything. */
    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        search()
    }

    /**
     * [RecipesTab.INVENTORY]'s own load — [RecipeRepository.suggestRecipes]'s inventory-matched
     * results. Not paginated: Spoonacular's own ingredient-match ranking already returns its
     * best matches in one page, and "load more" would just ask it for the same handful of
     * inventory-seeded suggestions again.
     */
    private fun launchInventoryTab(): Job = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, loadError = null, hasMore = false, isLoadingMore = false) }
        recipeRepository.suggestRecipes(excludedAllergens = _uiState.value.excludedAllergens, languageTag = languageTag)
            .onSuccess { list -> _uiState.update { it.copy(isLoading = false, recipes = list, loadError = null) } }
            .onFailure { e -> _uiState.update { it.copy(isLoading = false, recipes = emptyList(), loadError = classifyLoadError(e)) } }
    }

    /**
     * The hero card's "Op lijst" action — adds [recipe]'s [RecipeSuggestion.missingIngredients]
     * (already known from the ingredient-match search that produced it, no extra recipe-detail
     * fetch needed) to the shopping list, then reports what happened via
     * [missingIngredientsAdded] so the screen can show an undo snackbar. A no-op if the recipe
     * has nothing missing to add — see [RecipeSuggestion.missingIngredients]'s doc for when it's
     * populated at all.
     */
    fun addMissingIngredientsToShoppingList(recipe: RecipeSuggestion) {
        if (recipe.missingIngredients.isEmpty()) return
        viewModelScope.launch {
            val addedIds = recipeRepository.addIngredientsToShoppingList(recipe.missingIngredients)
            if (addedIds.isNotEmpty()) {
                _missingIngredientsAdded.emit(MissingIngredientsAddedEvent(recipe.meal.name, addedIds))
            }
        }
    }

    /** Reverts exactly the lines [addMissingIngredientsToShoppingList] just added — called from
     *  the undo snackbar's action, using the ids [missingIngredientsAdded] emitted with. */
    fun undoAddMissingIngredients(itemIds: List<String>) {
        viewModelScope.launch {
            itemIds.forEach { shoppingListRepository.removeItem(it) }
        }
    }

    /** Toggles [label] in/out of the Favorites/Custom tag filter (see [launchLiveList]) — an AND
     *  match against every currently selected custom label. No re-fetch needed: both lists are
     *  already live-collected in full, this only changes which of them pass the filter. */
    fun toggleCustomTagFilter(label: String) {
        val current = _uiState.value.selectedCustomTags
        val updated = if (label in current) current - label else current + label
        _uiState.update { it.copy(selectedCustomTags = updated) }
        selectedCustomTags.value = updated
    }

    /** Toggles [allergen] in/out of the exclusion filter and re-fetches. */
    fun toggleAllergen(allergen: Allergen) {
        _uiState.update {
            val updated = if (allergen in it.excludedAllergens) it.excludedAllergens - allergen else it.excludedAllergens + allergen
            it.copy(excludedAllergens = updated)
        }
        search()
    }

    /** Asks Claude to invent one recipe from the household's current inventory, optionally steered by [wish]. */
    fun generateRecipe(wish: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generateError = null) }
            when (val result = recipeRepository.generateRecipe(wish.takeIf { it.isNotBlank() }, languageTag)) {
                is GenerateRecipeResult.Success -> {
                    _uiState.update { it.copy(isGenerating = false) }
                    _generatedRecipeId.emit(result.detail.id)
                }
                GenerateRecipeResult.PremiumRequired ->
                    _uiState.update { it.copy(isGenerating = false, generateError = GenerateRecipeError.PREMIUM_REQUIRED) }
                GenerateRecipeResult.NoConnection ->
                    _uiState.update { it.copy(isGenerating = false, generateError = GenerateRecipeError.NO_CONNECTION) }
                GenerateRecipeResult.Failed ->
                    _uiState.update { it.copy(isGenerating = false, generateError = GenerateRecipeError.UNKNOWN) }
            }
        }
    }

    fun dismissGenerateError() {
        _uiState.update { it.copy(generateError = null) }
    }

    /** Imports one recipe from a household-pasted [url] — see [RecipeRepository.importRecipeFromUrl]. */
    fun importRecipeFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            when (val result = recipeRepository.importRecipeFromUrl(url, languageTag)) {
                is GenerateRecipeResult.Success -> {
                    _uiState.update { it.copy(isImporting = false) }
                    _importedRecipeId.emit(result.detail.id)
                }
                GenerateRecipeResult.PremiumRequired ->
                    _uiState.update { it.copy(isImporting = false, importError = ImportRecipeError.PREMIUM_REQUIRED) }
                GenerateRecipeResult.NoConnection ->
                    _uiState.update { it.copy(isImporting = false, importError = ImportRecipeError.NO_CONNECTION) }
                GenerateRecipeResult.Failed ->
                    _uiState.update { it.copy(isImporting = false, importError = ImportRecipeError.UNKNOWN) }
            }
        }
    }

    fun dismissImportError() {
        _uiState.update { it.copy(importError = null) }
    }
}
