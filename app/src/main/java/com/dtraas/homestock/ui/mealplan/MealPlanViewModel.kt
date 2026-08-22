package com.dtraas.homestock.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
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

data class MealPlanUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: Map<MealSlot, List<PlannedMeal>> = emptyMap(),
    /** Non-null while the "Maaltijd toevoegen" dialog is open — offers both a recipe picker and manual entry. */
    val pickerSlot: MealSlot? = null,
    val manualEntryText: String = "",
    val isPickerLoading: Boolean = false,
    val pickerSuggestions: List<RecipeSuggestion> = emptyList(),
    /** Non-null while the "Product toevoegen" dialog is open — see [MealPlanViewModel.openProductPicker]. */
    val productPickerSlot: MealSlot? = null,
    val productEntryText: String = "",
    val isProductPickerLoading: Boolean = false,
    /** The household's current voorraad, fetched fresh each time the dialog opens — both what
     *  the picker's live-filtered suggestion list narrows down and what [addManualProduct]
     *  checks a typed name against. */
    val inventoryItems: List<InventoryItemWithProduct> = emptyList(),
    /** Which of the 7 days in [date]'s week (Monday-start) have at least one planned meal —
     *  feeds the week-status strip's dots. See [MealPlanRepository.fetchWeekHasPlans] for why
     *  this is a one-time fetch rather than a live listener. */
    val weekStatus: Map<LocalDate, Boolean> = emptyMap(),
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

    // Re-fetched (not kept running) on every week change — see fetchWeekHasPlans's doc for why
    // a fresh one-time read per visible week is enough for a status-dot strip.
    private var weekStatusJob: Job? = null

    init {
        observeCurrentDate()
        loadWeekStatus()
    }

    private fun observeCurrentDate() {
        planObservationJob?.cancel()
        planObservationJob = viewModelScope.launch {
            mealPlanRepository.observeMealPlan(_uiState.value.date).collect { plan ->
                _uiState.update { it.copy(plan = plan) }
            }
        }
    }

    /** Monday-start week containing [MealPlanUiState.date] — re-fetches only when that week
     *  actually changes (see [changeDate]'s call site), not on every single-day navigation
     *  within the same week. */
    private fun loadWeekStatus() {
        weekStatusJob?.cancel()
        val weekStart = _uiState.value.date.with(DayOfWeek.MONDAY)
        weekStatusJob = viewModelScope.launch {
            val status = mealPlanRepository.fetchWeekHasPlans(weekStart)
            _uiState.update { it.copy(weekStatus = status) }
        }
    }

    fun goToPreviousDay() = changeDate(_uiState.value.date.minusDays(1))

    fun goToNextDay() = changeDate(_uiState.value.date.plusDays(1))

    /** Jumps straight to [date] — called from the week-status strip's day chips. */
    fun selectDate(date: LocalDate) = changeDate(date)

    private fun changeDate(date: LocalDate) {
        val previousWeekStart = _uiState.value.date.with(DayOfWeek.MONDAY)
        val newWeekStart = date.with(DayOfWeek.MONDAY)
        _uiState.update { it.copy(date = date, plan = emptyMap()) }
        observeCurrentDate()
        if (newWeekStart != previousWeekStart) loadWeekStatus()
    }

    /** Opens the "add a meal" dialog for [slot] — recipe suggestions load fresh every time, inventory may have changed since it was last opened. */
    fun openPicker(slot: MealSlot) {
        _uiState.update {
            it.copy(pickerSlot = slot, manualEntryText = "", isPickerLoading = true, pickerSuggestions = emptyList())
        }
        viewModelScope.launch {
            recipeRepository.suggestRecipes()
                .onSuccess { suggestions -> _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = suggestions) } }
                .onFailure { _uiState.update { it.copy(isPickerLoading = false, pickerSuggestions = emptyList()) } }
        }
    }

    fun dismissPicker() {
        _uiState.update { it.copy(pickerSlot = null, manualEntryText = "", isPickerLoading = false, pickerSuggestions = emptyList()) }
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

    /** Undo for [removeMeal] — re-adds the exact same [meal] (same id, thumbnail, recipe/product
     *  link) rather than building a fresh one, so undoing genuinely restores what was removed. */
    fun restoreMeal(slot: MealSlot, meal: PlannedMeal) {
        val date = _uiState.value.date
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
    }

    /** Opens the "Product toevoegen" dialog for [slot] — voorraad is fetched fresh every time,
     *  same reasoning as [openPicker]'s recipe suggestions: it may have changed since it was
     *  last opened. */
    fun openProductPicker(slot: MealSlot) {
        _uiState.update {
            it.copy(productPickerSlot = slot, productEntryText = "", isProductPickerLoading = true, inventoryItems = emptyList())
        }
        viewModelScope.launch {
            val items = inventoryRepository.observeInventoryWithProduct().first()
            _uiState.update { it.copy(isProductPickerLoading = false, inventoryItems = items) }
        }
    }

    fun dismissProductPicker() {
        _uiState.update {
            it.copy(productPickerSlot = null, productEntryText = "", isProductPickerLoading = false, inventoryItems = emptyList())
        }
    }

    fun onProductEntryTextChange(text: String) {
        _uiState.update { it.copy(productEntryText = text) }
    }

    /** Adds [item] — already confirmed to exist in voorraad, since it came from [MealPlanUiState.inventoryItems] — as a planned product. */
    fun pickProduct(item: InventoryItemWithProduct) {
        val slot = _uiState.value.productPickerSlot ?: return
        val date = _uiState.value.date
        val meal = PlannedMeal(
            id = UUID.randomUUID().toString(),
            name = item.name,
            thumbnailUrl = item.imageUrl,
            productBarcode = item.barcode,
            isProduct = true,
        )
        viewModelScope.launch { mealPlanRepository.addMeal(date, slot, meal) }
        dismissProductPicker()
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
        val slot = _uiState.value.productPickerSlot ?: return
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
        dismissProductPicker()
    }

    /** Called from [PlannedMealRow]'s persistent "toevoegen aan boodschappenlijst" button — only
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
