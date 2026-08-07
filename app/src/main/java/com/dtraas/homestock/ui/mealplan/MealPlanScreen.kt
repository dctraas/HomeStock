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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Instellingen > Beta > Weekmenu — a repeating weekly plan (one recipe per weekday, not tied
 * to calendar dates; see [com.dtraas.homestock.data.repository.MealPlanRepository]) with a
 * "Genereer boodschappenlijst" action that adds every planned meal's missing ingredients to
 * the shopping list in one combined, deduplicated batch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val viewModel: MealPlanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MealPlanViewModel(application.container.mealPlanRepository, application.container.recipeRepository)
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val locale: Locale = LocalConfiguration.current.locales[0]

    val snackbarHostState = remember { SnackbarHostState() }
    val generateErrorMessage = stringResource(R.string.meal_plan_generate_error)

    LaunchedEffect(uiState.generatedCount) {
        val count = uiState.generatedCount ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(R.string.meal_plan_generate_success_format, count))
        viewModel.consumeGeneratedCount()
    }
    LaunchedEffect(uiState.hasGenerateError) {
        if (uiState.hasGenerateError) snackbarHostState.showSnackbar(generateErrorMessage)
    }

    fun dayLabel(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.meal_plan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Button(
                onClick = viewModel::generateShoppingList,
                enabled = !uiState.isGenerating && uiState.plan.values.any { it != null },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.meal_plan_generate_button))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                DayRow(
                    label = dayLabel(day),
                    planned = uiState.plan[day],
                    onClick = { viewModel.openPicker(day) },
                    onClear = { viewModel.clearDay(day) },
                )
            }
        }
    }

    val pickerDay = uiState.pickerDay
    if (pickerDay != null) {
        MealPickerDialog(
            titleText = stringResource(R.string.meal_plan_picker_title_format, dayLabel(pickerDay)),
            isLoading = uiState.isPickerLoading,
            suggestions = uiState.pickerSuggestions,
            onSelect = viewModel::pickMeal,
            onDismiss = viewModel::dismissPicker,
        )
    }
}

@Composable
private fun DayRow(label: String, planned: PlannedMeal?, onClick: () -> Unit, onClear: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(88.dp),
            )
            if (planned != null) {
                if (planned.thumbnailUrl != null) {
                    AsyncImage(
                        model = planned.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(SoftImageShape),
                    )
                }
                Text(
                    text = planned.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.meal_plan_clear_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.meal_plan_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MealPickerDialog(
    titleText: String,
    isLoading: Boolean,
    suggestions: List<RecipeSuggestion>,
    onSelect: (RecipeSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
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
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(suggestions, key = { it.meal.id }) { suggestion ->
                        PickerRow(suggestion = suggestion, onClick = { onSelect(suggestion) })
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
