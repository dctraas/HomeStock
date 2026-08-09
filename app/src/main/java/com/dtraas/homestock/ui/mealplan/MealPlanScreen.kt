package com.dtraas.homestock.ui.mealplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftImageShape
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Instellingen > Beta > Maaltijdplanner — a real, date-based plan (see
 * [com.dtraas.homestock.data.repository.MealPlanRepository]): the top bar shows the selected
 * date with prev/next-day arrows, and each of the four [MealSlot]s can hold a recipe. Tapping a
 * planned recipe navigates to the existing recipe detail screen (see [onRecipeClick]), which
 * already shows matched/missing ingredients and an "add missing to shopping list" action — no
 * need to duplicate that here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(onBack: () -> Unit, onRecipeClick: (String) -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: MealPlanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MealPlanViewModel(application.container.mealPlanRepository, application.container.recipeRepository)
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val locale: Locale = LocalConfiguration.current.locales[0]

    val dateLabel = remember(uiState.date, locale) {
        val dayName = uiState.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
        val rest = uiState.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        "$dayName $rest"
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = viewModel::goToPreviousDay) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.meal_plan_previous_day_cd))
                        }
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = viewModel::goToNextDay) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.meal_plan_next_day_cd))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MealSlot.ORDERED.forEach { slot ->
                SlotCard(
                    slot = slot,
                    label = stringResource(slot.labelRes),
                    planned = uiState.plan[slot].orEmpty(),
                    onAddClick = { viewModel.openPicker(slot) },
                    onOpenClick = onRecipeClick,
                    onRemove = { meal -> viewModel.removeMeal(slot, meal) },
                )
            }
        }
    }

    val pickerSlot = uiState.pickerSlot
    if (pickerSlot != null) {
        MealPickerDialog(
            titleText = stringResource(R.string.meal_plan_picker_title_format, stringResource(pickerSlot.labelRes)),
            manualEntryText = uiState.manualEntryText,
            onManualEntryTextChange = viewModel::onManualEntryTextChange,
            onManualEntryAdd = viewModel::addManualMeal,
            isLoading = uiState.isPickerLoading,
            suggestions = uiState.pickerSuggestions,
            onSelect = viewModel::pickMeal,
            onDismiss = viewModel::dismissPicker,
        )
    }
}

/** Recognizable icon per meal slot, shown in the card header next to its label. */
private val MealSlot.icon: ImageVector
    get() = when (this) {
        MealSlot.BREAKFAST -> Icons.Filled.FreeBreakfast
        MealSlot.LUNCH -> Icons.Filled.LunchDining
        MealSlot.DINNER -> Icons.Filled.DinnerDining
        MealSlot.SNACK -> Icons.Filled.Cookie
    }

/**
 * A slot can now hold zero, one, or several planned meals — a household may want more than
 * one dish lined up for e.g. avondeten — so this renders one row per [PlannedMeal] plus a
 * trailing "add" row that's always present, rather than a single card that's either "empty"
 * or "has one recipe". Only meals with a [PlannedMeal.recipeId] (picked from a suggestion,
 * not typed by hand) are clickable through to the recipe detail screen. A thin divider
 * separates multiple meals within the same slot, so a busier day (say, two snacks) still
 * reads as clearly distinct dishes rather than a run-on list.
 */
@Composable
private fun SlotCard(
    slot: MealSlot,
    label: String,
    planned: List<PlannedMeal>,
    onAddClick: () -> Unit,
    onOpenClick: (String) -> Unit,
    onRemove: (PlannedMeal) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = slot.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            planned.forEachIndexed { index, meal ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                PlannedMealRow(
                    meal = meal,
                    onClick = { if (meal.recipeId != null) onOpenClick(meal.recipeId) },
                    onRemove = { onRemove(meal) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(SoftImageShape)
                    .clickable(onClick = onAddClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AddCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.meal_plan_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * A recipe pick always has [PlannedMeal.thumbnailUrl] (from the recipe database); a manually
 * typed meal never does. Rather than just skipping the image slot for those — which used to leave manual
 * entries looking bare and unfinished next to a recipe's photo, right when the user asked for
 * this to look nicer specifically for hand-typed meals — they get a colored fallback badge
 * with a fork-and-knife icon instead, the same "always a visual, real photo or otherwise"
 * treatment ProductImage gives products with no picture elsewhere in the app.
 *
 * Image and delete-icon sizing here deliberately match ShoppingListRow's (32dp rounded-rect
 * thumbnail, 32dp/18dp icon button) so an added meal reads as the same kind of list row as a
 * boodschappenlijst item, rather than the larger, looser spacing this used to have.
 */
@Composable
private fun PlannedMealRow(meal: PlannedMeal, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(enabled = meal.recipeId != null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (meal.thumbnailUrl != null) {
            AsyncImage(
                model = meal.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = meal.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.meal_plan_clear_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Offers both ways to add a meal to a slot: typing one by hand, or picking from recipe suggestions below it. */
@Composable
private fun MealPickerDialog(
    titleText: String,
    manualEntryText: String,
    onManualEntryTextChange: (String) -> Unit,
    onManualEntryAdd: () -> Unit,
    isLoading: Boolean,
    suggestions: List<RecipeSuggestion>,
    onSelect: (RecipeSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.meal_plan_manual_entry_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualEntryText,
                        onValueChange = onManualEntryTextChange,
                        placeholder = { Text(stringResource(R.string.meal_plan_manual_entry_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onManualEntryAdd,
                        enabled = manualEntryText.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.meal_plan_manual_entry_add))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = stringResource(R.string.meal_plan_suggestions_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    suggestions.isEmpty() -> Text(
                        text = stringResource(R.string.meal_plan_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(suggestions, key = { it.meal.id }) { suggestion ->
                            PickerRow(suggestion = suggestion, onClick = { onSelect(suggestion) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun PickerRow(suggestion: RecipeSuggestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (suggestion.meal.thumbnailUrl != null) {
            AsyncImage(
                model = suggestion.meal.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(SoftImageShape),
            )
        }
        Text(
            text = suggestion.meal.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}
