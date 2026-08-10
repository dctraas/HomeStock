package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** One ingredient row in the editor — [localId] is a client-only key for Compose's `items(key = ...)`, unrelated to anything persisted. */
data class CustomIngredientInput(val localId: String = UUID.randomUUID().toString(), val name: String = "", val measure: String = "")

data class CustomRecipeEditUiState(
    /** True only while loading an *existing* recipe's detail (edit flow) — a brand new recipe starts with an already-populated, editable form, nothing to wait on. */
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasLoadError: Boolean = false,
    val name: String = "",
    val category: String = "",
    val area: String = "",
    val readyInMinutes: String = "",
    val instructions: String = "",
    val ingredients: List<CustomIngredientInput> = listOf(CustomIngredientInput()),
    val showValidationError: Boolean = false,
    val showSaveError: Boolean = false,
    /** Set once [save] succeeds — the screen navigates to RecipeDetailScreen with this id. */
    val savedRecipeId: String? = null,
    val showDeleteConfirm: Boolean = false,
    /** Set once [confirmDelete] succeeds — the screen pops back to RecipesScreen. */
    val isDeleted: Boolean = false,
)

/**
 * Backs both the "new custom recipe" and "edit custom recipe" flows — [recipeId] null means the
 * former (form starts empty), non-null means the latter (form is pre-filled by [load], and
 * delete becomes available). Either way [save] calls the same
 * [RecipeRepository.saveCustomRecipe], which creates or overwrites based on whether an id is
 * passed through.
 */
class CustomRecipeEditViewModel(
    private val recipeId: String?,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomRecipeEditUiState())
    val uiState: StateFlow<CustomRecipeEditUiState> = _uiState

    init {
        if (recipeId != null) load()
    }

    private fun load() {
        val id = recipeId ?: return
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
                            instructions = detail.instructions.orEmpty(),
                            ingredients = detail.ingredients
                                .map { (name, measure) -> CustomIngredientInput(name = name, measure = measure) }
                                .ifEmpty { listOf(CustomIngredientInput()) },
                        )
                    }
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
    fun onInstructionsChange(value: String) = _uiState.update { it.copy(instructions = value) }

    fun addIngredientRow() = _uiState.update { it.copy(ingredients = it.ingredients + CustomIngredientInput()) }

    fun removeIngredientRow(localId: String) = _uiState.update { state ->
        val updated = state.ingredients.filterNot { it.localId == localId }
        // Never down to zero rows — an empty list would just make "+ ingrediënt toevoegen" the
        // only way back in, for no benefit over always keeping one editable row available.
        state.copy(ingredients = updated.ifEmpty { listOf(CustomIngredientInput()) })
    }

    fun onIngredientNameChange(localId: String, value: String) = updateIngredient(localId) { it.copy(name = value) }
    fun onIngredientMeasureChange(localId: String, value: String) = updateIngredient(localId) { it.copy(measure = value) }

    private inline fun updateIngredient(localId: String, transform: (CustomIngredientInput) -> CustomIngredientInput) {
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients.map { if (it.localId == localId) transform(it) else it })
        }
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
                instructions = state.instructions.takeIf { it.isNotBlank() },
                ingredients = ingredients,
            )
                .onSuccess { detail -> _uiState.update { it.copy(isSaving = false, savedRecipeId = detail.id) } }
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
}
