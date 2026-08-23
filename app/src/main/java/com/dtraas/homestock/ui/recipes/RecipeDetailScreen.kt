package com.dtraas.homestock.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.RecipeTag
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.theme.SoftCardShape
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    mealId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    onStartCookMode: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val languageTag = LocalConfiguration.current.locales[0].language
    val viewModel: RecipeDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RecipeDetailViewModel(
                    mealId,
                    languageTag,
                    application.container.recipeRepository,
                    application.container.householdMembersRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val detail = uiState.detail

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(detail?.displayName ?: stringResource(R.string.recipes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (uiState.isFavorite) R.string.recipes_favorite_remove_cd else R.string.recipes_favorite_add_cd,
                                ),
                                tint = if (uiState.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (detail.isCustom) {
                            IconButton(onClick = { onEdit(mealId) }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.recipes_edit_custom_cd))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            uiState.hasError || detail == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.recipes_detail_error),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                detail.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = detail.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                }

                val subtitle = listOfNotNull(
                    detail.displayCategory,
                    detail.displayArea,
                    detail.readyInMinutes?.let { stringResource(R.string.recipes_ready_in_minutes_format, it) },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (detail.isAiGenerated) {
                    RecipeBadge(icon = Icons.Filled.AutoAwesome, label = stringResource(R.string.recipes_ai_generated_badge))
                } else if (detail.isCustom) {
                    RecipeBadge(icon = Icons.Filled.Edit, label = stringResource(R.string.recipes_custom_badge))
                } else if (detail.translatedForLocale != null) {
                    RecipeBadge(icon = Icons.Filled.Translate, label = stringResource(R.string.recipes_translated_badge))
                }

                // Editable only for recipes the household actually kept a durable copy of — see
                // RecipeRepository.setRecipeTags' doc for why a plain unfavorited Spoonacular
                // browse result has no tag editor at all rather than a dead-end one.
                if (detail.isCustom || uiState.isFavorite) {
                    RecipeTagEditor(
                        customTags = detail.tags.filter { RecipeTag.fromStorageKey(it) == null },
                        onAddCustom = viewModel::addCustomTag,
                        onRemoveCustom = viewModel::removeCustomTag,
                    )
                }

                // Portion scaling — only offered when the recipe actually has a serving count
                // (see RecipeDetail.servings' doc); [scaleFactor] stays 1.0 (a no-op through
                // scaleMeasure) whenever it doesn't, so the ingredient list below always renders
                // correctly whether or not scaling is available.
                val originalServings = detail.servings
                val targetServings = uiState.targetServings
                val scaleFactor = if (originalServings != null && originalServings > 0 && targetServings != null) {
                    targetServings.toDouble() / originalServings.toDouble()
                } else {
                    1.0
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.recipes_ingredients_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (originalServings != null && originalServings > 0 && targetServings != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.recipes_servings_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            QuantityStepper(
                                quantity = targetServings,
                                onDecrease = { viewModel.setTargetServings(targetServings - 1) },
                                onIncrease = { viewModel.setTargetServings(targetServings + 1) },
                                minQuantity = 1,
                                dense = true,
                            )
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = SoftCardShape,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        // Zipped rather than iterating displayIngredients alone: matching against
                        // inventory (uiState.matchedIngredients) only works on the original
                        // English ingredient names — see [RecipeDetail]'s doc — while the shown
                        // name/measure should still prefer the translation.
                        val zipped = detail.ingredients.zip(detail.displayIngredients)
                        zipped.forEachIndexed { index, (original, display) ->
                            val haveIt = uiState.matchedIngredients.contains(original.first)
                            val measure = scaleMeasure(display.second, scaleFactor)
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (haveIt) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.recipes_ingredient_in_inventory_cd),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Box(modifier = Modifier.size(18.dp))
                                }
                                if (measure.isNotBlank()) {
                                    Text(
                                        text = measure,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 10.dp).widthIn(min = 64.dp),
                                    )
                                    Text(
                                        text = display.first,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                } else {
                                    Text(
                                        text = display.first,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Per serving (Spoonacular's own breakdown, see RecipeDetail's doc) — absent
                // entirely for AI-generated/custom recipes and any Spoonacular recipe that
                // simply has no nutrition data, so the whole section is skipped rather than
                // showing a card of dashes.
                val hasNutrition = detail.calories != null || detail.protein != null ||
                    detail.fat != null || detail.carbohydrates != null
                if (hasNutrition) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.product_detail_nutrition_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.recipes_nutrition_per_serving),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 1.dp),
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = SoftCardShape,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            detail.calories?.let {
                                NutritionValueRow(stringResource(R.string.product_detail_nutrition_energy), formatRecipeKcal(it))
                            }
                            detail.fat?.let {
                                NutritionValueRow(stringResource(R.string.product_detail_nutrition_fat), formatRecipeGrams(it))
                            }
                            detail.carbohydrates?.let {
                                NutritionValueRow(stringResource(R.string.product_detail_nutrition_carbohydrates), formatRecipeGrams(it))
                            }
                            detail.protein?.let {
                                NutritionValueRow(stringResource(R.string.product_detail_nutrition_proteins), formatRecipeGrams(it))
                            }
                        }
                    }
                }

                if (uiState.addedToShoppingList) {
                    Text(
                        text = stringResource(R.string.recipes_added_to_shopping_list),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    // Kleiner en rechts uitgelijnd op de regel in plaats van een volle-breedte
                    // knop — per de design review. Icon-only blijft: de knop se eigen vorm/
                    // kleur leest al als "tap me", en de actie is nog steeds beschikbaar voor
                    // screenreaders via de icon's contentDescription.
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        FilledIconButton(
                            onClick = viewModel::addMissingIngredientsToShoppingList,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Filled.PlaylistAdd,
                                contentDescription = stringResource(R.string.recipes_add_missing_to_shopping_list),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                val instructions = detail.displayInstructions?.trim()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.recipes_instructions_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // Only worth its own mode once there's more than one step to walk
                    // through — a single-paragraph recipe has nothing for "Volgende" to do.
                    if (!instructions.isNullOrBlank() && splitIntoSteps(instructions).size > 1) {
                        TextButton(onClick = onStartCookMode) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.cook_mode_start), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                if (!instructions.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = SoftCardShape,
                    ) {
                        Text(
                            text = instructions,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    // Explicit rather than just omitting the whole section — a recipe (usually
                    // one sourced from Spoonacular) can genuinely have no instructions text in
                    // its own data, and a silently missing section reads as "this app is
                    // broken" rather than "this particular recipe's source has a gap".
                    Text(
                        text = stringResource(R.string.recipes_instructions_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** One label/value line in the nutrition card — mirrors ProductDetailScreen's NutritionRow, just without the indented-subtotal styling that only applies there (saturated fat/sugars aren't part of Spoonacular's per-serving breakdown). */
@Composable
private fun NutritionValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRecipeKcal(value: Double): String = String.format(Locale.getDefault(), "%.0f kcal", value)
private fun formatRecipeGrams(value: Double): String = String.format(Locale.getDefault(), "%.1f g", value)

/** Matches a measure string's leading quantity: a plain number ("300", "2.5", "1,5") optionally
 *  followed by a simple "/denominator" fraction ("1/2"), then whatever's left (unit, free text
 *  like "snufje naar smaak"). Anchored to the very start via [matchEntire] in [scaleMeasure].*/
private val leadingQuantityRegex = Regex("""^(\d+(?:[.,]\d+)?)(?:\s*/\s*(\d+))?\s*(.*)$""")

/**
 * Scales a recipe ingredient's measure string by [factor] (target servings ÷ the recipe's own
 * servings), leaving everything after the leading number untouched — "300 g" at 1.5x becomes
 * "450 g", "bloem" (no leading number at all, nothing safe to multiply) is returned as-is. Also
 * understands a simple "1/2 tsp" fraction, which AI-generated recipes occasionally produce as
 * free text (Spoonacular's own amounts are always plain decimals by the time they get here).
 */
internal fun scaleMeasure(measure: String, factor: Double): String {
    if (measure.isBlank() || factor == 1.0) return measure
    val match = leadingQuantityRegex.matchEntire(measure.trim()) ?: return measure
    val (wholeText, fractionText, rest) = match.destructured
    val whole = wholeText.replace(',', '.').toDoubleOrNull() ?: return measure
    val amount = if (fractionText.isNotEmpty()) {
        val denominator = fractionText.toDoubleOrNull()
        if (denominator == null || denominator == 0.0) whole else whole / denominator
    } else {
        whole
    }
    val scaledText = formatScaledQuantity(amount * factor)
    return if (rest.isBlank()) scaledText else "$scaledText $rest"
}

/** Renders a scaled amount as a whole number when it rounds cleanly to one ("4", not "4.0"),
 *  otherwise with up to 2 decimals and no trailing zeros ("1.5", not "1.50"). */
private fun formatScaledQuantity(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}

/** Editable chip row of free-text labels the household typed themselves — shown under
 *  RecipeDetailScreen's badges for a recipe the household has saved (custom or favorited), where
 *  tags actually have somewhere to persist. The 3 fixed preset labels (Snel/Kindvriendelijk/
 *  Restjes) this used to also offer are gone, per explicit request — only per-recipe custom
 *  labels remain. */
@Composable
private fun RecipeTagEditor(
    customTags: List<String>,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        customTags.forEach { label ->
            // onClick is a no-op — only the trailing X (its own IconButton, below) removes the
            // label, so tapping the chip's body/text doesn't delete it by surprise.
            AssistChip(
                onClick = {},
                label = { Text(label) },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemoveCustom(label) },
                        modifier = Modifier.size(18.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.recipe_tag_remove_custom_cd),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                },
            )
        }
        AssistChip(
            onClick = { showAddDialog = true },
            label = { Text(stringResource(R.string.recipe_tag_add_custom_button)) },
            leadingIcon = {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )
    }

    if (showAddDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.recipe_tag_add_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.recipe_tag_add_dialog_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCustom(input)
                        showAddDialog = false
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text(stringResource(R.string.recipe_tag_add_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** Small pill badge (AI-generated / AI-translated) shown under RecipeDetailScreen's subtitle. */
@Composable
private fun RecipeBadge(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
