package com.dtraas.homestock.ui.recipes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.RecipeTag
import com.dtraas.homestock.data.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** One ingredient row in the editor — [localId] is a client-only key for Compose's `items(key = ...)`, unrelated to anything persisted.
 *  [isInStock] is a best-effort, debounced live check against current inventory (see
 *  [CustomRecipeEditViewModel.onIngredientNameChange]) — purely a display hint on the row, never
 *  itself persisted. */
data class CustomIngredientInput(
    val localId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val measure: String = "",
    val isInStock: Boolean = false,
)

data class CustomRecipeEditUiState(
    /** True only while loading an *existing* recipe's detail (edit flow) — a brand new recipe starts with an already-populated, editable form, nothing to wait on. */
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasLoadError: Boolean = false,
    val name: String = "",
    val category: String = "",
    val area: String = "",
    val readyInMinutes: String = "",
    val servings: String = "",
    val instructions: String = "",
    val ingredients: List<CustomIngredientInput> = listOf(CustomIngredientInput()),
    /** Free-text labels the household types in themselves — same pattern as RecipeDetailScreen's
     *  tag editor, just pre-populated here for an existing recipe by [load]. The fixed preset
     *  labels (Snel/Kindvriendelijk/Restjes) this used to offer alongside these are gone; a
     *  recipe already carrying one of those old keys from before their removal just quietly
     *  drops it on next save (see [RecipeTag]'s doc). */
    val customTags: List<String> = emptyList(),
    /** A freshly picked (not yet uploaded) photo — uploaded by [save] once the recipe itself has
     *  a real id to attach it to (see [RecipeRepository.uploadCustomRecipePhoto]'s doc for why
     *  that has to happen after, not before). */
    val photoUri: Uri? = null,
    /** The recipe's already-uploaded photo, for the edit flow — shown until [photoUri] replaces
     *  it with a freshly picked one. */
    val existingThumbnailUrl: String? = null,
    val showValidationError: Boolean = false,
    val showSaveError: Boolean = false,
    /** Set once [save] succeeds — the screen navigates to RecipeDetailScreen with this id. */
    val savedRecipeId: String? = null,
    val showDeleteConfirm: Boolean = false,
    /** Set once [confirmDelete] succeeds — the screen pops back to RecipesScreen. */
    val isDeleted: Boolean = false,
)

/**
 * Backs three flows: "new custom recipe" ([recipeId] and [importId] both null, form starts
 * empty), "edit custom recipe" ([recipeId] non-null, form pre-filled by [load], delete becomes
 * available), and "review an imported recipe" ([importId] non-null — see
 * [RecipeRepository.importRecipeFromUrl] — form pre-filled from the already-cached detail
 * [load] fetches by that id, same as the edit flow, but [save] still creates a brand-new recipe
 * rather than overwriting anything, since [recipeId] stays null in this case; the temporary
 * "ai-..." id [importId] points at is only ever a [RecipeRepository] cache key, never persisted
 * itself). Either way [save] calls the same [RecipeRepository.saveCustomRecipe], which creates
 * or overwrites based on whether [recipeId] is passed through.
 */
class CustomRecipeEditViewModel(
    private val recipeId: String?,
    private val importId: String?,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomRecipeEditUiState())
    val uiState: StateFlow<CustomRecipeEditUiState> = _uiState

    init {
        (recipeId ?: importId)?.let(::load)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasLoadError = false) }
            recipeRepository.getRecipeDetail(id)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = detail.name,
                            category = detail.category.orEmpty(),
                            area = detail.area.orEmpty(),
                            readyInMinutes = detail.readyInMinutes?.toString().orEmpty(),
                            servings = detail.servings?.toString().orEmpty(),
                            instructions = detail.instructions.orEmpty(),
                            ingredients = detail.ingredients
                                .map { (name, measure) -> CustomIngredientInput(name = name, measure = measure) }
                                .ifEmpty { listOf(CustomIngredientInput()) },
                            customTags = detail.tags.filter { RecipeTag.fromStorageKey(it) == null },
                            existingThumbnailUrl = detail.thumbnailUrl,
                        )
                    }
                    checkAllIngredientsInStock()
                }
                .onFailure { _uiState.update { it.copy(isLoading = false, hasLoadError = true) } }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, showValidationError = false) }
    fun onCategoryChange(value: String) = _uiState.update { it.copy(category = value) }
    fun onAreaChange(value: String) = _uiState.update { it.copy(area = value) }
    fun onReadyInMinutesChange(value: String) {
        // Digits only — this feeds a plain text field (no numeric keyboard guarantee), so it's
        // the simplest way to keep it always parseable as an Int on save.
        if (value.all { it.isDigit() }) _uiState.update { it.copy(readyInMinutes = value) }
    }
    fun onServingsChange(value: String) {
        if (value.all { it.isDigit() }) _uiState.update { it.copy(servings = value) }
    }
    fun onInstructionsChange(value: String) = _uiState.update { it.copy(instructions = value) }

    fun onPhotoPicked(uri: Uri) = _uiState.update { it.copy(photoUri = uri) }

    /** Adds a free-text label — a no-op for a blank label, one colliding with a now-retired
     *  preset tag's own storage key (case-insensitively, so it can't silently resurrect one), or
     *  an exact duplicate (also case-insensitively) of a custom label already added. Same
     *  validation as [RecipeDetailViewModel.addCustomTag]. */
    fun onAddCustomTag(label: String) = _uiState.update { state ->
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return@update state
        if (RecipeTag.entries.any { it.storageKey.equals(trimmed, ignoreCase = true) }) return@update state
        if (state.customTags.any { it.equals(trimmed, ignoreCase = true) }) return@update state
        state.copy(customTags = state.customTags + trimmed)
    }

    fun onRemoveCustomTag(label: String) = _uiState.update { it.copy(customTags = it.customTags - label) }

    /** Parses one combined "maat naam" line (e.g. "2 el mosterd") into its own new row and
     *  appends it — the quick-add row's Enter action. A leading quantity (with an attached unit
     *  word, if any) becomes the measure; everything after it becomes the name. No recognizable
     *  leading quantity just means an empty measure, same as manually leaving that field blank. */
    fun addIngredientFromQuickEntry(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val match = quickEntryMeasureRegex.find(trimmed)
        val (measure, name) = if (match != null) {
            match.value.trim() to trimmed.substring(match.value.length).trim()
        } else {
            "" to trimmed
        }
        if (name.isEmpty()) return
        val row = CustomIngredientInput(name = name, measure = measure)
        _uiState.update { state ->
            // The form's very first row starts out blank (see the class doc) — replace it
            // instead of leaving an empty row sitting above the first real one.
            val ingredients = if (state.ingredients.size == 1 && state.ingredients.first().name.isBlank()) {
                listOf(row)
            } else {
                state.ingredients + row
            }
            state.copy(ingredients = ingredients)
        }
        checkIngredientInStock(row.localId, row.name)
    }

    fun removeIngredientRow(localId: String) = _uiState.update { state ->
        val updated = state.ingredients.filterNot { it.localId == localId }
        // Never down to zero rows — an empty list would just make "+ ingrediënt toevoegen" the
        // only way back in, for no benefit over always keeping one editable row available.
        state.copy(ingredients = updated.ifEmpty { listOf(CustomIngredientInput()) })
    }

    fun onIngredientNameChange(localId: String, value: String) {
        updateIngredient(localId) { it.copy(name = value) }
        checkIngredientInStock(localId, value)
    }
    fun onIngredientMeasureChange(localId: String, value: String) = updateIngredient(localId) { it.copy(measure = value) }

    private inline fun updateIngredient(localId: String, transform: (CustomIngredientInput) -> CustomIngredientInput) {
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients.map { if (it.localId == localId) transform(it) else it })
        }
    }

    // One in-flight stock check per row at a time — typing quickly cancels the previous check
    // for that same row rather than piling up stale ones that could resolve out of order.
    private val ingredientMatchJobs = mutableMapOf<String, Job>()

    private fun checkIngredientInStock(localId: String, name: String) {
        ingredientMatchJobs[localId]?.cancel()
        ingredientMatchJobs[localId] = viewModelScope.launch {
            delay(400)
            val inStock = recipeRepository.inventoryContainsIngredientNamed(name)
            updateIngredient(localId) { it.copy(isInStock = inStock) }
        }
    }

    /** Runs [checkIngredientInStock] for every row at once — [load]'s own pre-filled rows never
     *  went through [onIngredientNameChange], so nothing would otherwise trigger their check. */
    private fun checkAllIngredientsInStock() {
        _uiState.value.ingredients.forEach { checkIngredientInStock(it.localId, it.name) }
    }

    fun save() {
        val state = _uiState.value
        val ingredients = state.ingredients
            .map { it.name.trim() to it.measure.trim() }
            .filter { (name, _) -> name.isNotEmpty() }
        if (state.name.isBlank() || ingredients.isEmpty()) {
            _uiState.update { it.copy(showValidationError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showSaveError = false) }
            recipeRepository.saveCustomRecipe(
                id = recipeId,
                name = state.name,
                category = state.category.takeIf { it.isNotBlank() },
                area = state.area.takeIf { it.isNotBlank() },
                readyInMinutes = state.readyInMinutes.toIntOrNull(),
                servings = state.servings.toIntOrNull(),
                instructions = state.instructions.takeIf { it.isNotBlank() },
                ingredients = ingredients,
                tags = state.customTags,
            )
                .onSuccess { detail ->
                    state.photoUri?.let { uri -> recipeRepository.uploadCustomRecipePhoto(detail.id, uri) }
                    _uiState.update { it.copy(isSaving = false, savedRecipeId = detail.id) }
                }
                .onFailure { _uiState.update { it.copy(isSaving = false, showSaveError = true) } }
        }
    }

    fun requestDelete() = _uiState.update { it.copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun confirmDelete() {
        val id = recipeId ?: return
        viewModelScope.launch {
            recipeRepository.deleteCustomRecipe(id)
            _uiState.update { it.copy(showDeleteConfirm = false, isDeleted = true) }
        }
    }

    private companion object {
        // A leading number (optionally decimal, comma or dot) optionally followed by one short
        // unit word ("el", "g", "kg", "ml", "l", "tl", "stuks", …) — deliberately loose (any
        // short word, not a fixed unit list) since a household might type "snufje"/"handje" as
        // their own quantity word just as validly as a real unit.
        val quickEntryMeasureRegex = Regex("""^\d+(?:[.,]\d+)?(?:\s*[\p{L}]{1,10})?\s+""")
    }
}
