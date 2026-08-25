package com.dtraas.homestock.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.local.entity.MealCompletionStatus
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.GenerateRecipeResult
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.recipes.GenerateRecipeError
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Result of [MealPlanViewModel.addProductToShoppingList], for the screen's confirmation popup — [alreadyOnList] is true when [RecipeRepository.addIngredientsToShoppingList] skipped it as a dedup (an already-open line by that name), not a failure. */
data class ShoppingListAddResult(val name: String, val alreadyOnList: Boolean)

/** Which half of the merged "add to a slot" sheet (see [MealPickerDialog]) is showing — one
 *  sheet, not two dialogs, per the 2026-08 dialog review ("the single '+' opens this sheet
 *  directly; picking a plain product is the 'product' filter chip inside it"). */
enum class MealPickerMode { RECIPE, PRODUCT }

data class MealPlanUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: Map<MealSlot, List<PlannedMeal>> = emptyMap(),
    /** Non-null while the "add to slot" sheet is open — see [MealPlanViewModel.openPicker]. Both
     *  [MealPickerMode]s share this one slot/loading lifecycle; only which list is showing and
     *  which text field is live differs. */
    val pickerSlot: MealSlot? = null,
    val pickerMode: MealPickerMode = MealPickerMode.RECIPE,
    val manualEntryText: String = "",
    val isPickerLoading: Boolean = false,
    val pickerSuggestions: List<RecipeSuggestion> = emptyList(),
    /** Ids of [pickerSuggestions] that are also in the household's favorites — backs the sheet's
     *  "Favorieten" filter chip. Favorites not already among [pickerSuggestions] are merged in
     *  (see [openPicker]), so the filter never hides a favorite the plain suggestion list missed. */
    val pickerFavoriteIds: Set<String> = emptySet(),
    val productEntryText: String = "",
    val isProductPickerLoading: Boolean = false,
    /** The household's current voorraad, fetched fresh each time the sheet opens — both what the
     *  product-mode live-filtered list narrows down and what [addManualProduct] checks a typed
     *  name against. */
    val inventoryItems: List<InventoryItemWithProduct> = emptyList(),
    /** Non-null while [generateAiMeal] is running or has just failed — the sheet's "Bedenk een
     *  recept" footer shows a spinner/inline error off this, same pattern as RecipesScreen's own
     *  AI-generate sheet (see [com.dtraas.homestock.ui.recipes.RecipesViewModel.generateRecipe]). */
    val isGeneratingAiMeal: Boolean = false,
    val aiMealError: GenerateRecipeError? = null,
    /** The 7 days in [date]'s week (Monday-start), each with its full per-slot plan — feeds the
     *  week day-strip's dots, the header's "N van 7 avonden gepland" count, and (combined with
     *  [missingIngredientsForWeek]) the "op lijst" bottom bar. See
     *  [MealPlanRepository.fetchWeekPlan] for why this is a one-time fetch rather than a live
     *  listener for the other 6 days — the selected day's own entry is instead kept live-current
     *  from [plan] (see [MealPlanViewModel.observeCurrentDate]). */
    val weekPlan: Map<LocalDate, Map<MealSlot, List<PlannedMeal>>> = emptyMap(),
    /** Ingredient names the week's planned recipes need but the household doesn't have — see
     *  [MealPlanViewModel.loadMissingIngredientsForWeek]. Recomputed whenever [weekPlan] changes. */
    val missingIngredientsForWeek: List<String> = emptyList(),
    val isLoadingMissingIngredients: Boolean = false,
    /** Full detail (for the "25 min · 4 pers." meta line and "alles in huis" check) of the
     *  selected day's featured avondeten recipe, if one is planned — see
     *  [MealPlanViewModel.loadDinnerDetail]. Null while nothing's planned, a plain product/typed
     *  name is planned instead, or the recipe isn't a Spoonacular recipe. */
    val dinnerDetail: RecipeDetail? = null,
    val isDinnerDetailLoading: Boolean = false,
    val dinnerAllIngredientsInStock: Boolean = false,
)

class MealPlanViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState

    /** Emitted by [addProductToShoppingList] once the add actually completes — the screen shows
     *  this as a confirmation popup, since tapping that button gives no other feedback that it
     *  worked. */
    private val _shoppingListAddResult = MutableSharedFlow<ShoppingListAddResult>()
    val shoppingListAddResult: SharedFlow<ShoppingListAddResult> = _shoppingListAddResult

    // Re-launched on every date change (see changeDate) rather than a single collect over a
    // flatMapLatest-on-date flow — the date lives in plain UI state, not its own StateFlow, so
    // there's nothing to flatMapLatest from; cancelling the previous listener by hand achieves
    // the same "only one day's snapshot listener open at a time" result.
    private var planObservationJob: Job? = null

    // Re-fetched (not kept running) on every week change — see fetchWeekPlan's doc for why
    // a fresh one-time read per visible week is enough for the day strip/header.
    private var weekPlanJob: Job? = null

    // Re-launched whenever the selected day's avondeten recipe changes — see loadDinnerDetail.
    private var dinnerDetailJob: Job? = null

    init {
        observeCurrentDate()
        loadWeekPlan()
    }

    private fun observeCurrentDate() {
        planObservationJob?.cancel()
        planObservationJob = viewModelScope.launch {
            mealPlanRepository.observeMealPlan(_uiState.value.date).collect { plan ->
                // Patches just the selected day's entry into weekPlan rather than waiting for
                // the next full loadWeekPlan() — keeps the header count/day-strip dot and the
                // missing-ingredients diff current the moment a meal is added/removed today,
                // without a second Firestore round trip.
                val date = _uiState.value.date
                _uiState.update { it.copy(plan = plan, weekPlan = it.weekPlan + (date to plan)) }
                loadDinnerDetail(plan[MealSlot.DINNER].orEmpty())
                loadMissingIngredientsForWeek(_uiState.value.weekPlan)
            }
        }
    }

    /** Monday-start week containing [MealPlanUiState.date] — re-fetches only when that week
     *  actually changes (see [changeDate]'s call site), not on every single-day navigation
     *  within the same week. */
    private fun loadWeekPlan() {
        weekPlanJob?.cancel()
        val weekStart = _uiState.value.date.with(DayOfWeek.MONDAY)
        weekPlanJob = viewModelScope.launch {
            val weekPlan = mealPlanRepository.fetchWeekPlan(weekStart)
            _uiState.update { it.copy(weekPlan = weekPlan) }
            loadMissingIngredientsForWeek(weekPlan)
        }
    }

    /**
     * Diffs every recipe planned anywhere in [weekPlan] against inventory — see
     * [RecipeRepository.missingIngredients] — for the bottom bar's "N ingrediënten ontbreken
     * voor het menu van deze week" + "Op lijst" action. Fetches each distinct planned recipe's
     * full [RecipeDetail] (ingredients aren't kept on [PlannedMeal] itself); a recipe whose
     * detail fails to load (e.g. transient network error) is simply skipped rather than failing
     * the whole diff.
     */
    private suspend fun loadMissingIngredientsForWeek(weekPlan: Map<LocalDate, Map<MealSlot, List<PlannedMeal>>>) {
        _uiState.update { it.copy(isLoadingMissingIngredients = true) }
        val recipeIds = weekPlan.values.flatMap { it.values.flatten() }.mapNotNull { it.recipeId }.distinct()
        val details = recipeIds.mapNotNull { id -> recipeRepository.getRecipeDetail(id).getOrNull() }
        val missing = recipeRepository.missingIngredients(details)
        _uiState.update { it.copy(missingIngredientsForWeek = missing, isLoadingMissingIngredients = false) }
    }

    /** Adds the missing week's ingredients to the shopping list in one go — see
     *  [RecipeRepository.addIngredientsToShoppingList] for the open-item dedup. */
    fun addMissingIngredientsForWeekToShoppingList() {
        val ingredients = _uiState.value.missingIngredientsForWeek
        if (ingredients.isEmpty()) return
        viewModelScope.launch { recipeRepository.addIngredientsToShoppingList(ingredients) }
    }

    /** Loads (or clears) the featured avondeten card's [MealPlanUiState.dinnerDetail] — the
     *  first planned dinner entry that's an actual recipe, if any; a plain product or hand-typed
     *  name has no detail to show. */
    private fun loadDinnerDetail(dinnerMeals: List<PlannedMeal>) {
        val recipeId = dinnerMeals.firstOrNull { it.recipeId != null }?.recipeId
        if (recipeId == _uiState.value.dinnerDetail?.id) return
        dinnerDetailJob?.cancel()
        if (recipeId == null) {
            _uiState.update { it.copy(dinnerDetail = null, isDinnerDetailLoading = false, dinnerAllIngredientsInStock = false) }
            return
        }
        dinnerDetailJob = viewModelScope.launch {
            _uiState.update { it.copy(isDinnerDetailLoading = true) }
            val detail = recipeRepository.getRecipeDetail(recipeId).getOrNull()
            val allInStock = detail != null &&
                detail.ingredients.isNotEmpty() &&
                recipeRepository.matchedIngredients(detail).size >= detail.ingredients.size
            _uiState.update {
                it.copy(dinnerDetail = detail, isDinnerDetailLoading = false, dinnerAllIngredientsInStock = allInStock)
            }
        }
    }

    fun goToPreviousWeek() = changeDate(_uiState.value.date.minusWeeks(1))

    fun goToNextWeek() = changeDate(_uiState.value.date.plusWeeks(1))

    /** Jumps straight to [date] — called from the week day-strip's day cards. */
    fun selectDate(date: LocalDate) = changeDate(date)

    private fun changeDate(date: LocalDate) {
        val previousWeekStart = _uiState.value.date.with(DayOfWeek.MONDAY)
        val newWeekStart = date.with(DayOfWeek.MONDAY)
        _uiState.update { it.copy(date = date, plan = emptyMap()) }
        observeCurrentDate()
        if (newWeekStart != previousWeekStart) loadWeekPlan()
    }

    /**
     * Opens the merged "add to slot" sheet for [slot], in [MealPickerMode.RECIPE] — recipe
     * suggestions, favorites, and voorraad all load fresh every time (any of the three may have
     * changed since it was last opened), so switching to the "Product" filter chip mid-sheet
     * never needs a second fetch. Favorites are fetched first, then merged into whatever
     * [RecipeRepository.suggestRecipes] returns — sequential rather than parallel so there's no
     * race between the two updates landing in [MealPlanUiState.pickerSuggestions] in either
     * order (see the merge below).
     */
    fun openPicker(slot: MealSlot) {
        _uiState.update {
            it.copy(
                pickerSlot = slot,
                pickerMode = MealPickerMode.RECIPE,
                manualEntryText = "",
                productEntryText = "",
                isPickerLoading = true,
                isProductPickerLoading = true,
                pickerSuggestions = emptyList(),
                pickerFavoriteIds = emptySet(),
                inventoryItems = emptyList(),
                aiMealError = null,
            )
        }
        viewModelScope.launch {
            val favorites = runCatching { recipeRepository.observeFavoriteRecipes().first() }.getOrDefault(emptyList())
            val favoriteIds = favorites.map { it.meal.id }.toSet()
            recipeRepository.suggestRecipes()
                .onSuccess { suggestions ->
                    val merged = LinkedHashMap<String, RecipeSuggestion>()
                    suggestions.forEach { merged[it.meal.id] = it }
                    favorites.forEach { fav -> merged.putIfAbsent(fav.meal.id, fav) }
                    _uiState.update {
                        it.copy(isPickerLoading = false, pickerSuggestions = merged.values.toList(), pickerFavoriteIds = favoriteIds)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = favorites, pickerFavoriteIds = favoriteIds) }
                }
        }
        viewModelScope.launch {
            val items = inventoryRepository.observeInventoryWithProduct().first()
            _uiState.update { it.copy(isProductPickerLoading = false, inventoryItems = items) }
        }
    }

    fun setPickerMode(mode: MealPickerMode) {
        _uiState.update { it.copy(pickerMode = mode) }
    }

    fun dismissPicker() {
        _uiState.update {
            it.copy(
                pickerSlot = null,
                pickerMode = MealPickerMode.RECIPE,
                manualEntryText = "",
                isPickerLoading = false,
                pickerSuggestions = emptyList(),
                pickerFavoriteIds = emptySet(),
                productEntryText = "",
                isProductPickerLoading = false,
                inventoryItems = emptyList(),
                isGeneratingAiMeal = false,
                aiMealError = null,
            )
        }
    }

    fun onManualEntryTextChange(text: String) {
        _uiState.update { it.copy(manualEntryText = text) }
    }

    fun pickMeal(suggestion: RecipeSuggestion) {
        val slot = _uiState.value.pickerSlot ?: return
        val date = _uiState.value.date
        val meal = PlannedMeal(
            id = suggestion.meal.id,
            name = suggestion.meal.name,
            thumbnailUrl = suggestion.meal.thumbnailUrl,
            recipeId = suggestion.meal.id,
        )
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
        dismissPicker()
    }

    /** Adds the currently-typed [MealPlanUiState.manualEntryText] as a plain (non-recipe) meal — a no-op if it's blank. */
    fun addManualMeal() {
        val slot = _uiState.value.pickerSlot ?: return
        val name = _uiState.value.manualEntryText.trim()
        if (name.isEmpty()) return
        val date = _uiState.value.date
        val meal = PlannedMeal(id = UUID.randomUUID().toString(), name = name)
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
        dismissPicker()
    }

    fun removeMeal(slot: MealSlot, meal: PlannedMeal) {
        val date = _uiState.value.date
        viewModelScope.launch { mealPlanRepository.removeMeal(date, slot, meal) }
    }

    /** Marks a planned product [meal] opgebruikt — records that on the meal entry itself, and,
     *  when it matched a voorraad item ([PlannedMeal.productBarcode] set), consumes one unit of
     *  it from inventory (logged as ordinary consumption, not waste). */
    fun markMealEaten(slot: MealSlot, meal: PlannedMeal) {
        val date = _uiState.value.date
        viewModelScope.launch {
            mealPlanRepository.setMealStatus(date, slot, meal, MealCompletionStatus.EATEN)
            meal.productBarcode?.let { inventoryRepository.consumeOneFromMeal(it, wasted = false) }
        }
    }

    /** Same as [markMealEaten], but for weggegooid — the inventory-side consumption (when
     *  applicable) is logged as waste instead, so it counts toward the household's
     *  voedselverspilling stats/notifications. */
    fun markMealWasted(slot: MealSlot, meal: PlannedMeal) {
        val date = _uiState.value.date
        viewModelScope.launch {
            mealPlanRepository.setMealStatus(date, slot, meal, MealCompletionStatus.WASTED)
            meal.productBarcode?.let { inventoryRepository.consumeOneFromMeal(it, wasted = true) }
        }
    }

    /** Undo for [removeMeal] — re-adds the exact same [meal] (same id, thumbnail, recipe/product
     *  link) rather than building a fresh one, so undoing genuinely restores what was removed. */
    fun restoreMeal(slot: MealSlot, meal: PlannedMeal) {
        val date = _uiState.value.date
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
    }

    fun onProductEntryTextChange(text: String) {
        _uiState.update { it.copy(productEntryText = text) }
    }

    /** Adds [item] — already confirmed to exist in voorraad, since it came from [MealPlanUiState.inventoryItems] — as a planned product. */
    fun pickProduct(item: InventoryItemWithProduct) {
        val slot = _uiState.value.pickerSlot ?: return
        val date = _uiState.value.date
        val meal = PlannedMeal(
            id = UUID.randomUUID().toString(),
            name = item.name,
            thumbnailUrl = item.imageUrl,
            productBarcode = item.barcode,
            isProduct = true,
        )
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
        dismissPicker()
    }

    /**
     * Adds the currently-typed [MealPlanUiState.productEntryText] as a planned product — a
     * no-op if it's blank. Checked (case-insensitively) against [MealPlanUiState.inventoryItems]
     * first: a name match is treated exactly like [pickProduct] (same barcode/thumbnail, so it's
     * still recognized as "in voorraad" and opens the real product detail screen on tap); no
     * match still adds it — plans shouldn't block on typos or products the household simply
     * hasn't scanned in yet — but without [PlannedMeal.productBarcode], so the row shows a
     * "toevoegen aan boodschappenlijst" button instead (see [addProductToShoppingList]) rather
     * than silently deciding on the household's behalf whether it belongs there.
     */
    fun addManualProduct() {
        val slot = _uiState.value.pickerSlot ?: return
        val name = _uiState.value.productEntryText.trim()
        if (name.isEmpty()) return
        val match = _uiState.value.inventoryItems.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (match != null) {
            pickProduct(match)
            return
        }
        val date = _uiState.value.date
        val meal = PlannedMeal(id = UUID.randomUUID().toString(), name = name, isProduct = true)
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
        dismissPicker()
    }

    /**
     * "Bedenk een recept" — asks Claude (via [RecipeRepository.generateRecipe], the same call
     * RecipesScreen's own AI-generate sheet uses) to invent one recipe from the household's
     * current inventory, then plans the result straight into [MealPlanUiState.pickerSlot] and
     * closes the sheet — no intermediate "here's what I made, add it?" step, since the sheet was
     * already mid-picking-a-meal-for-this-slot when this was tapped. A failure leaves the sheet
     * open with [MealPlanUiState.aiMealError] set instead, so the household can just try again
     * or fall back to a suggestion/manual entry without losing their place.
     */
    fun generateAiMeal(languageTag: String?) {
        val slot = _uiState.value.pickerSlot ?: return
        val date = _uiState.value.date
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingAiMeal = true, aiMealError = null) }
            when (val result = recipeRepository.generateRecipe(wish = null, languageTag = languageTag)) {
                is GenerateRecipeResult.Success -> {
                    val meal = PlannedMeal(
                        id = result.detail.id,
                        name = result.detail.name,
                        thumbnailUrl = result.detail.thumbnailUrl,
                        recipeId = result.detail.id,
                    )
                    mealPlanRepository.addMeal(date, slot, meal)
                    dismissPicker()
                }
                GenerateRecipeResult.PremiumRequired ->
                    _uiState.update { it.copy(isGeneratingAiMeal = false, aiMealError = GenerateRecipeError.PREMIUM_REQUIRED) }
                GenerateRecipeResult.NoConnection ->
                    _uiState.update { it.copy(isGeneratingAiMeal = false, aiMealError = GenerateRecipeError.NO_CONNECTION) }
                GenerateRecipeResult.Failed ->
                    _uiState.update { it.copy(isGeneratingAiMeal = false, aiMealError = GenerateRecipeError.UNKNOWN) }
            }
        }
    }

    /** Called from [CompactPlannedRow]'s persistent "toevoegen aan boodschappenlijst" button — only
     *  shown for a planned product not matched to voorraad, but available at any point after
     *  it's added (not just right away), so this can't rely on picker state the way the rest of
     *  this ViewModel's actions do; [name] comes straight from the row's own [PlannedMeal].
     *  Reuses the same dedup-by-open-name logic as a recipe's "voeg ontbrekende toe aan lijst",
     *  and reports which of the two happened via [shoppingListAddResult] — tapping the button
     *  otherwise gives no feedback that anything happened. */
    fun addProductToShoppingList(name: String) {
        viewModelScope.launch {
            val addedIds = recipeRepository.addIngredientsToShoppingList(listOf(name))
            _shoppingListAddResult.emit(ShoppingListAddResult(name, alreadyOnList = addedIds.isEmpty()))
        }
    }
}
