package com.dtraas.homestock.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookModeUiState(
    val isLoading: Boolean = true,
    val detail: RecipeDetail? = null,
    val hasError: Boolean = false,
    val steps: List<String> = emptyList(),
    val currentStepIndex: Int = 0,
)

/**
 * Backs CookModeScreen — a full-screen, one-step-at-a-time walkthrough of a recipe's
 * instructions, reached from RecipeDetailScreen's "Start koken" button. Re-fetches the recipe
 * by [mealId] rather than receiving the already-loaded detail from RecipeDetailViewModel:
 * Compose Navigation only carries primitive nav args between destinations, and
 * [RecipeRepository.getRecipeDetail]'s in-memory cache (see its class doc) means this is a
 * cheap in-memory lookup, not a real second network round trip, for a recipe just viewed.
 */
class CookModeViewModel(
    private val mealId: String,
    private val languageTag: String?,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookModeUiState())
    val uiState: StateFlow<CookModeUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            recipeRepository.getRecipeDetail(mealId, languageTag)
                .onSuccess { detail ->
                    val steps = splitIntoSteps(detail.displayInstructions.orEmpty())
                    _uiState.update {
                        it.copy(isLoading = false, detail = detail, steps = steps, hasError = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                }
        }
    }

    fun nextStep() {
        _uiState.update { it.copy(currentStepIndex = (it.currentStepIndex + 1).coerceAtMost((it.steps.size - 1).coerceAtLeast(0))) }
    }

    fun previousStep() {
        _uiState.update { it.copy(currentStepIndex = (it.currentStepIndex - 1).coerceAtLeast(0)) }
    }
}

/**
 * Breaks a recipe's free-text instructions into individual steps. Prefers one-step-per-line
 * (AI-generated recipes are already formatted this way — see
 * RecipeRepository.buildRecipeGenerationPrompt/parseGeneratedRecipe — and a well-formatted
 * Spoonacular entry sometimes is too), stripping a leading "1." / "1)" numbering marker since
 * CookModeScreen shows its own "Stap X van Y" counter. Falls back to a naive sentence split on
 * the rare single-paragraph blob, which is the only reasonable guess without real NLP.
 */
internal fun splitIntoSteps(instructions: String): List<String> {
    val lines = instructions.lines().map { it.trim() }.filter { it.isNotBlank() }
    val stepLeadingNumberRegex = Regex("""^\d+[.)]\s*""")
    return if (lines.size > 1) {
        lines.map { it.replace(stepLeadingNumberRegex, "") }.filter { it.isNotBlank() }
    } else {
        instructions.split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

// Minute/hour/second words across the app's 5 locales. Order inside each alternation doesn't
// matter for correctness — \b after the whole alternative already stops "min" from matching
// inside "minuten" (the "min(?:uten|uut|ute|utes)?" alternative absorbs the longer word whole,
// or falls through to bare "min" only when nothing longer follows).
private const val durationUnitPattern =
    "uur|uren|hour|hours|std|stunde|stunden|hora|horas|heure|heures|" +
        "min(?:uten|uut|ute|utes|utos|uto)?|" +
        "sec(?:onden|onds)?|sek(?:onden)?|seg(?:undos)?|secondes?"

/** "10 minuten", "1 uur", "30 sec" (and the same in en/de/es/fr — see [durationUnitPattern]) —
 *  a best-effort scan of a single step's text for the *first* time it mentions, so
 *  CookModeScreen can offer a one-tap timer for it. Only the first match: a step almost never
 *  names more than one meaningfully "start now" duration (a second number is usually a
 *  temperature or a quantity, not a second timer to run in parallel). */
private val durationRegex = Regex(
    """(\d+)\s*($durationUnitPattern)\b""",
    RegexOption.IGNORE_CASE,
)

internal fun detectDurationSeconds(step: String): Long? {
    val match = durationRegex.find(step) ?: return null
    val amount = match.groupValues[1].toLongOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    val secondsPerUnit = when {
        unit.startsWith("uur") || unit.startsWith("uren") || unit.startsWith("hour") ||
            unit.startsWith("std") || unit.startsWith("stunde") || unit.startsWith("hora") ||
            unit.startsWith("heure") -> 3600L
        unit.startsWith("min") -> 60L
        else -> 1L
    }
    return amount * secondsPerUnit
}
