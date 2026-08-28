package com.dtraas.homestock.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.model.RecipeTag
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.ui.theme.SoftCardShape
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Which of the three sections below the primary actions is showing — replaces what used to be
 *  one long scroll through ingrediënten → voeding → bereiding all at once. [NUTRITION] is the
 *  only one ever hidden (see [RecipeDetailTabRow]'s `showNutrition`), same reasoning as
 *  ProductDetailScreen's own Voeding tab: nothing to show for a recipe with no nutrition data at
 *  all (always true for a custom/AI recipe, sometimes true for a Spoonacular one). */
private enum class RecipeDetailTab { INGREDIENTS, INSTRUCTIONS, NUTRITION }

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
                    application.container.mealPlanRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val detail = uiState.detail
    var selectedTab by remember(mealId) { mutableStateOf(RecipeDetailTab.INGREDIENTS) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
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
            else -> {
                val originalServings = detail.servings
                val targetServings = uiState.targetServings
                val scaleFactor = if (originalServings != null && originalServings > 0 && targetServings != null) {
                    targetServings.toDouble() / originalServings.toDouble()
                } else {
                    1.0
                }
                val missingIngredients = detail.ingredients.filter { it.first !in uiState.matchedIngredients }
                val hasNutrition = detail.calories != null || detail.protein != null ||
                    detail.fat != null || detail.carbohydrates != null
                val stepCount = detail.displayInstructions?.trim()?.takeIf { it.isNotBlank() }?.let { splitIntoSteps(it).size } ?: 0

                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    RecipeHeroHeader(
                        detail = detail,
                        isFavorite = uiState.isFavorite,
                        plannedDate = uiState.plannedDate,
                        onBack = onBack,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onEditClick = { onEdit(mealId) },
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(detail.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                                if (detail.ingredients.isNotEmpty()) {
                                    RecipeMatchRing(
                                        matched = detail.ingredients.size - missingIngredients.size,
                                        total = detail.ingredients.size,
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }

                            if (detail.hasTranslation) {
                                RecipeBadge(icon = Icons.Filled.Translate, label = stringResource(R.string.recipes_translated_badge))
                            } else if (detail.isAiGenerated) {
                                RecipeBadge(icon = Icons.Filled.AutoAwesome, label = stringResource(R.string.recipes_ai_generated_badge))
                            }

                            if (detail.isCustom || uiState.isFavorite) {
                                RecipeTagEditor(
                                    customTags = detail.tags.filter { RecipeTag.fromStorageKey(it) == null },
                                    onAddCustom = viewModel::addCustomTag,
                                    onRemoveCustom = viewModel::removeCustomTag,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (missingIngredients.isNotEmpty()) {
                                    Button(
                                        onClick = viewModel::addMissingIngredientsToShoppingList,
                                        enabled = !uiState.addedToShoppingList,
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                    ) {
                                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (uiState.addedToShoppingList) {
                                                stringResource(R.string.recipes_detail_added_short)
                                            } else {
                                                stringResource(R.string.recipes_detail_add_missing_format, missingIngredients.size)
                                            },
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                                if (stepCount > 1) {
                                    OutlinedButton(
                                        onClick = onStartCookMode,
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(stringResource(R.string.recipes_detail_cook_action), modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }

                        RecipeDetailTabRow(
                            selected = selectedTab,
                            showNutrition = hasNutrition,
                            onSelect = { selectedTab = it },
                            stepCount = stepCount,
                        )

                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            when (selectedTab) {
                                RecipeDetailTab.INGREDIENTS -> RecipeIngredientsTab(
                                    detail = detail,
                                    matchedIngredients = uiState.matchedIngredients,
                                    missingIngredients = missingIngredients,
                                    targetServings = targetServings,
                                    originalServings = originalServings,
                                    scaleFactor = scaleFactor,
                                    onSetTargetServings = viewModel::setTargetServings,
                                )
                                RecipeDetailTab.INSTRUCTIONS -> RecipeInstructionsTab(detail.displayInstructions?.trim())
                                RecipeDetailTab.NUTRITION -> RecipeNutritionTab(detail)
                            }
                        }

                        PlanAgainRow(onClick = viewModel::requestPlan, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }

    if (uiState.showPlanSheet) {
        RecipePlanSheet(
            isPlanning = uiState.isPlanning,
            onConfirm = viewModel::planForDate,
            onDismiss = viewModel::dismissPlanSheet,
        )
    }
}

/** The fixed (non-scrolling) hero — a photo (or, absent one, the same
 *  [Icons.Filled.RestaurantMenu] placeholder every other recipe tile falls back to) on a sage
 *  surface, back/favorite/overflow floating in white circles over it, and "EIGEN"/"[dag]
 *  GEPLAND" badges pinned to its bottom-left corner. */
@Composable
private fun RecipeHeroHeader(
    detail: RecipeDetail,
    isFavorite: Boolean,
    plannedDate: LocalDate?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
            if (detail.thumbnailUrl != null) {
                AsyncImage(
                    model = detail.thumbnailUrl,
                    contentDescription = detail.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RecipeHeroIconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeHeroIconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(if (isFavorite) R.string.recipes_favorite_remove_cd else R.string.recipes_favorite_add_cd),
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (detail.isCustom) {
                    Box {
                        RecipeHeroIconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.product_detail_overflow_cd))
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_edit_custom_cd)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { showOverflowMenu = false; onEditClick() },
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (detail.isCustom) {
                RecipeHeroBadge(stringResource(R.string.recipes_custom_badge_short))
            }
            if (plannedDate != null) {
                val dayName = plannedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
                RecipeHeroBadge(stringResource(R.string.recipes_detail_planned_badge_format, dayName))
            }
        }
    }
}

@Composable
private fun RecipeHeroIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = Modifier.size(40.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun RecipeHeroBadge(label: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** "7/8 IN HUIS" — a filled ring (0 → 1 sweep of [matched]/[total]) around the fraction itself,
 *  same "how much of this do I already have" idea as ProductDetailScreen's StockCard, just as a
 *  ring instead of a plain number since this is a recipe-wide summary rather than one product's
 *  own quantity. */
@Composable
private fun RecipeMatchRing(matched: Int, total: Int, modifier: Modifier = Modifier) {
    val ringColor = if (matched == total) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
            .clip(CircleShape)
            .background(ringColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$matched/$total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ringColor)
            Text(
                text = stringResource(R.string.recipes_detail_in_stock_ring_label),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = ringColor,
            )
        }
    }
}

@Composable
private fun RecipeDetailTabRow(selected: RecipeDetailTab, showNutrition: Boolean, stepCount: Int, onSelect: (RecipeDetailTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == RecipeDetailTab.INGREDIENTS,
            onClick = { onSelect(RecipeDetailTab.INGREDIENTS) },
            label = { Text(stringResource(R.string.recipes_detail_tab_ingredients)) },
        )
        FilterChip(
            selected = selected == RecipeDetailTab.INSTRUCTIONS,
            onClick = { onSelect(RecipeDetailTab.INSTRUCTIONS) },
            label = {
                Text(
                    if (stepCount > 0) stringResource(R.string.recipes_detail_tab_instructions_count_format, stepCount)
                    else stringResource(R.string.recipes_detail_tab_instructions),
                )
            },
        )
        if (showNutrition) {
            FilterChip(
                selected = selected == RecipeDetailTab.NUTRITION,
                onClick = { onSelect(RecipeDetailTab.NUTRITION) },
                label = { Text(stringResource(R.string.recipes_detail_tab_nutrition)) },
            )
        }
    }
}

@Composable
private fun RecipeIngredientsTab(
    detail: RecipeDetail,
    matchedIngredients: Set<String>,
    missingIngredients: List<Pair<String, String>>,
    targetServings: Int?,
    originalServings: Int?,
    scaleFactor: Double,
    onSetTargetServings: (Int) -> Unit,
) {
    Column {
        if (originalServings != null && originalServings > 0 && targetServings != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.recipes_servings_for_format, targetServings).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuantityStepper(
                    quantity = targetServings,
                    onDecrease = { onSetTargetServings(targetServings - 1) },
                    onIncrease = { onSetTargetServings(targetServings + 1) },
                    minQuantity = 1,
                    dense = true,
                )
            }
        }

        if (missingIngredients.isNotEmpty()) {
            MissingIngredientsBanner(missingIngredients.map { it.first }, modifier = Modifier.padding(top = 12.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = SoftCardShape,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                // Zipped rather than iterating displayIngredients alone: matching against
                // inventory (matchedIngredients) only works on the original English ingredient
                // names — see [RecipeDetail]'s doc — while the shown name/measure should still
                // prefer the translation.
                val zipped = detail.ingredients.zip(detail.displayIngredients)
                zipped.forEachIndexed { index, (original, display) ->
                    val haveIt = original.first in matchedIngredients
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
                                color = if (haveIt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 10.dp).widthIn(min = 64.dp),
                            )
                            Text(
                                text = display.first,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (haveIt) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        } else {
                            Text(
                                text = display.first,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (haveIt) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Je mist alleen spekblokjes" / "Je mist nog N ingrediënten" — [names] are the original
 *  (English) ingredient names, same source [RecipeIngredientsTab]'s own rows are colored coral
 *  from; shown as their un-translated form is a rare, acceptable simplification here since the
 *  common case is exactly one missing name anyway. */
@Composable
private fun MissingIngredientsBanner(names: List<String>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = SoftCardShape) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (names.size == 1) {
                    stringResource(R.string.recipes_missing_ingredients_banner_one, names.single())
                } else {
                    pluralStringResource(R.plurals.recipes_missing_ingredients_banner_many, names.size, names.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun RecipeInstructionsTab(instructions: String?) {
    if (!instructions.isNullOrBlank()) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = SoftCardShape) {
            Text(text = instructions, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
    } else {
        // Explicit rather than just an empty tab — a recipe (usually one sourced from
        // Spoonacular) can genuinely have no instructions text in its own data, and a silently
        // blank tab reads as "this app is broken" rather than "this source has a gap".
        Text(
            text = stringResource(R.string.recipes_instructions_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecipeNutritionTab(detail: RecipeDetail) {
    Column {
        Text(
            text = stringResource(R.string.recipes_nutrition_per_serving),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = SoftCardShape,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                detail.calories?.let { NutritionValueRow(stringResource(R.string.product_detail_nutrition_energy), formatRecipeKcal(it)) }
                detail.fat?.let { NutritionValueRow(stringResource(R.string.product_detail_nutrition_fat), formatRecipeGrams(it)) }
                detail.carbohydrates?.let { NutritionValueRow(stringResource(R.string.product_detail_nutrition_carbohydrates), formatRecipeGrams(it)) }
                detail.protein?.let { NutritionValueRow(stringResource(R.string.product_detail_nutrition_proteins), formatRecipeGrams(it)) }
            }
        }
    }
}

@Composable
private fun PlanAgainRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.Transparent) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.recipes_detail_plan_again_action),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Icon(Icons.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** Day (next 7, today included) + maaltijdslot picker for [RecipeDetailViewModel.planForDate] —
 *  same [PlannedMeal] shape the maaltijdplanner's own recipe picker writes, just reachable from
 *  here instead of only from Maaltijden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePlanSheet(isPlanning: Boolean, onConfirm: (LocalDate, MealSlot) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var selectedSlot by remember { mutableStateOf(MealSlot.DINNER) }
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(sheetContentPadding)) {
            SheetTitle(title = stringResource(R.string.recipes_detail_plan_sheet_title))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(7) { offset ->
                    val date = today.plusDays(offset.toLong())
                    val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    FilterChip(selected = date == selectedDate, onClick = { selectedDate = date }, label = { Text(label) })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealSlot.ORDERED.forEach { slot ->
                    FilterChip(selected = slot == selectedSlot, onClick = { selectedSlot = slot }, label = { Text(stringResource(slot.labelRes)) })
                }
            }
            Button(
                onClick = { onConfirm(selectedDate, selectedSlot) },
                enabled = !isPlanning,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (isPlanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.recipes_detail_plan_confirm_action))
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
