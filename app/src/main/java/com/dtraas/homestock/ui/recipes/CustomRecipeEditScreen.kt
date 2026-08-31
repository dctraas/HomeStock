package com.dtraas.homestock.ui.recipes

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SoftCardShape

/**
 * Create/edit form for a hand-entered recipe (see [RecipeRepository.saveCustomRecipe]) —
 * [recipeId] null means "new recipe" (form starts empty, no delete action); non-null means
 * "edit" (form is pre-filled from [CustomRecipeEditViewModel.load], delete becomes available).
 * [importId] is the third case: also a "new recipe" as far as saving/delete are concerned, but
 * pre-filled from an already-imported draft (see [RecipeRepository.importRecipeFromUrl]) instead
 * of starting empty — see [CustomRecipeEditViewModel]'s doc for how the two non-null cases differ.
 *
 * A 3-step wizard rather than one long form: (1) basisgegevens + ingrediënten, (2) bereiding,
 * (3) overzicht — "Concept bewaren" saves whatever's filled in so far from any step (there's no
 * separate draft status; it's the exact same [CustomRecipeEditViewModel.save] the final step's
 * button calls, just reachable earlier), while stepping forward/back is purely local navigation
 * state, not persisted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRecipeEditScreen(
    recipeId: String?,
    importId: String? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: CustomRecipeEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CustomRecipeEditViewModel(recipeId, importId, application.container.recipeRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val stepCount = 3

    LaunchedEffect(uiState.savedRecipeId) { uiState.savedRecipeId?.let(onSaved) }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }
    // A validation failure only ever concerns step 1's own fields (naam/ingrediënten) — jump
    // back to it so the error message that appears there is actually visible.
    LaunchedEffect(uiState.showValidationError) { if (uiState.showValidationError) step = 0 }

    // System back navigates a step at a time past the first one, same as the header's own arrow
    // below — only the very first step's back action actually leaves the screen.
    BackHandler(enabled = step > 0) { step -= 1 }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CustomRecipeEditHeader(
                title = stringResource(if (recipeId == null) R.string.custom_recipe_edit_title_new else R.string.custom_recipe_edit_title_edit),
                step = step,
                stepCount = stepCount,
                onBackStep = { step -= 1 },
                onDismiss = onBack,
                showDelete = recipeId != null,
                onDeleteClick = viewModel::requestDelete,
            )
            when {
                uiState.isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.hasLoadError -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
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
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        when (step) {
                            0 -> CustomRecipeStepBasics(uiState, viewModel)
                            1 -> CustomRecipeStepInstructions(uiState, viewModel)
                            else -> CustomRecipeStepOverview(uiState)
                        }
                    }
                    CustomRecipeBottomBar(
                        step = step,
                        stepCount = stepCount,
                        isSaving = uiState.isSaving,
                        onSaveDraft = viewModel::save,
                        onBackStep = { step -= 1 },
                        onNextStep = { step += 1 },
                        onFinish = viewModel::save,
                    )
                }
            }
        }
    }

    if (uiState.showSaveError) {
        Text(
            text = stringResource(R.string.custom_recipe_save_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text(stringResource(R.string.custom_recipe_delete_confirm_title)) },
            text = { Text(stringResource(R.string.custom_recipe_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.custom_recipe_delete_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** Green header — X (step 1) or a back arrow (later steps) on the left, title + a 3-segment
 *  progress bar with "X van 3" on the right. Delete (edit flow only) lives here too, same
 *  top-right corner the old flat app bar put it, since none of the three steps has a more
 *  natural home for it. */
@Composable
private fun CustomRecipeEditHeader(
    title: String,
    step: Int,
    stepCount: Int,
    onBackStep: () -> Unit,
    onDismiss: () -> Unit,
    showDelete: Boolean,
    onDeleteClick: () -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (step > 0) onBackStep else onDismiss) {
                Icon(
                    imageVector = if (step > 0) Icons.Filled.ArrowBack else Icons.Filled.Close,
                    contentDescription = stringResource(if (step > 0) R.string.common_back else R.string.common_close),
                    tint = contentColor,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (showDelete) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.custom_recipe_delete_action), tint = contentColor)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(stepCount) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                color = if (index <= step) Color.White else Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.custom_recipe_step_format, step + 1, stepCount),
                style = MaterialTheme.typography.labelMedium,
                color = OnTopAppBarContainerAccent,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** "Concept bewaren" (always saves whatever's filled in right now, from any step — see the
 *  screen's own doc) on the left, the step's own forward action on the right. */
@Composable
private fun CustomRecipeBottomBar(
    step: Int,
    stepCount: Int,
    isSaving: Boolean,
    onSaveDraft: () -> Unit,
    onBackStep: () -> Unit,
    onNextStep: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = if (step == 0) onSaveDraft else onBackStep,
            enabled = !isSaving,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Text(stringResource(if (step == 0) R.string.custom_recipe_save_draft_action else R.string.common_back))
        }
        Button(
            onClick = if (step < stepCount - 1) onNextStep else onFinish,
            enabled = !isSaving,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else if (step < stepCount - 1) {
                Text(
                    text = stringResource(if (step == 0) R.string.custom_recipe_next_to_instructions else R.string.custom_recipe_next_to_overview),
                )
                Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            } else {
                Text(stringResource(R.string.custom_recipe_save_action))
            }
        }
    }
}

/** Step 1 — foto, naam, klaar-in/personen, labels, and the ingredient list with its quick-add
 *  row. Everything that isn't itself a whole-line free-text field (foto, personen, labels,
 *  ingrediënten) uses a purpose-built row rather than the generic [OutlinedTextField] the old
 *  single-page form used throughout. */
@Composable
private fun CustomRecipeStepBasics(uiState: CustomRecipeEditUiState, viewModel: CustomRecipeEditViewModel) {
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::onPhotoPicked) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            CustomRecipePhotoTile(
                photoUri = uiState.photoUri,
                existingThumbnailUrl = uiState.existingThumbnailUrl,
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            Card(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = SoftCardShape,
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringResource(R.string.custom_recipe_name_label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = SoftCardShape,
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.custom_recipe_ready_minutes_label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        BasicTextField(
                            value = uiState.readyInMinutes,
                            onValueChange = viewModel::onReadyInMinutesChange,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.width(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.custom_recipe_minutes_suffix),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.custom_recipe_servings_label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QuantityStepper(
                        quantity = uiState.servings.toIntOrNull() ?: 4,
                        onDecrease = { viewModel.onServingsChange(((uiState.servings.toIntOrNull() ?: 4) - 1).coerceAtLeast(1).toString()) },
                        onIncrease = { viewModel.onServingsChange(((uiState.servings.toIntOrNull() ?: 4) + 1).toString()) },
                        minQuantity = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // Not in the mockup itself, but real, previously-existing fields (RecipeDetail.category/
        // area both feed the recipe's own listing card elsewhere) — kept here as an optional
        // pair rather than dropped, since neither step's screenshot left them an obvious home.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = uiState.category,
                onValueChange = viewModel::onCategoryChange,
                label = { Text(stringResource(R.string.custom_recipe_category_label)) },
                placeholder = { Text(stringResource(R.string.custom_recipe_category_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = uiState.area,
                onValueChange = viewModel::onAreaChange,
                label = { Text(stringResource(R.string.custom_recipe_area_label)) },
                placeholder = { Text(stringResource(R.string.custom_recipe_area_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Column {
            Text(
                text = stringResource(R.string.custom_recipe_tags_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var showAddTagDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.customTags.forEach { label ->
                    AssistChip(
                        onClick = {},
                        label = { Text(label) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.onRemoveCustomTag(label) }, modifier = Modifier.size(18.dp)) {
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
                    onClick = { showAddTagDialog = true },
                    label = { Text(stringResource(R.string.recipe_tag_add_custom_button)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
            if (showAddTagDialog) {
                var input by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showAddTagDialog = false },
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
                        TextButton(onClick = { viewModel.onAddCustomTag(input); showAddTagDialog = false }, enabled = input.isNotBlank()) {
                            Text(stringResource(R.string.recipe_tag_add_dialog_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddTagDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = SoftCardShape,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.custom_recipe_ingredients_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val inStockCount = uiState.ingredients.count { it.name.isNotBlank() && it.isInStock }
                    val totalCount = uiState.ingredients.count { it.name.isNotBlank() }
                    if (totalCount > 0) {
                        Text(
                            text = stringResource(R.string.custom_recipe_ingredients_in_stock_format, inStockCount, totalCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                uiState.ingredients.filter { it.name.isNotBlank() }.forEach { ingredient ->
                    CustomIngredientRow(
                        ingredient = ingredient,
                        onRemove = { viewModel.removeIngredientRow(ingredient.localId) },
                    )
                }
                var quickEntry by remember { mutableStateOf("") }
                CustomIngredientQuickAddRow(
                    value = quickEntry,
                    onValueChange = { quickEntry = it },
                    onSubmit = {
                        viewModel.addIngredientFromQuickEntry(quickEntry)
                        quickEntry = ""
                    },
                )
                Text(
                    text = stringResource(R.string.custom_recipe_ingredient_quick_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (uiState.showValidationError) {
                    Text(
                        text = stringResource(R.string.custom_recipe_validation_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRecipePhotoTile(photoUri: Uri?, existingThumbnailUrl: String?, onClick: () -> Unit) {
    val model = photoUri ?: existingThumbnailUrl
    Surface(
        shape = SoftCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(96.dp).clickable(onClick = onClick),
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.custom_recipe_photo_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CustomIngredientRow(ingredient: CustomIngredientInput, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = listOf(ingredient.measure, ingredient.name).filter { it.isNotBlank() }.joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        if (ingredient.isInStock) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.custom_recipe_ingredient_in_stock_cd), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.custom_recipe_remove_ingredient_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** "+ 2 el mosterd [Enter]" — a single combined field the household types a whole ingredient
 *  line into; [CustomRecipeEditViewModel.addIngredientFromQuickEntry] splits it into
 *  measure/naam on submit rather than asking for two separate fields up front. */
@Composable
private fun CustomIngredientQuickAddRow(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.custom_recipe_ingredient_quick_add_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f).padding(start = 10.dp, top = 12.dp, bottom = 12.dp),
            )
            if (value.isNotBlank()) {
                TextButton(onClick = onSubmit) { Text(stringResource(R.string.custom_recipe_ingredient_quick_add_submit)) }
            }
        }
    }
}

/** Step 2 — bereiding, a single free-text field (same as the old form's own instructions field,
 *  now with the whole step to itself instead of sharing scroll space with everything else). */
@Composable
private fun CustomRecipeStepInstructions(uiState: CustomRecipeEditUiState, viewModel: CustomRecipeEditViewModel) {
    Column {
        Text(
            text = stringResource(R.string.custom_recipe_instructions_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = uiState.instructions,
            onValueChange = viewModel::onInstructionsChange,
            placeholder = { Text(stringResource(R.string.custom_recipe_instructions_placeholder)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(320.dp),
        )
    }
}

/** Step 3 — a read-only recap of everything entered so far, right before the final save. */
@Composable
private fun CustomRecipeStepOverview(uiState: CustomRecipeEditUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = SoftCardShape,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiState.name.ifBlank { stringResource(R.string.custom_recipe_name_placeholder) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val metaParts = listOfNotNull(
                    uiState.readyInMinutes.toIntOrNull()?.let { stringResource(R.string.recipes_ready_in_minutes_format, it) },
                    uiState.servings.toIntOrNull()?.let { stringResource(R.string.recipes_servings_short_format, it) },
                )
                if (metaParts.isNotEmpty()) {
                    Text(metaParts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (uiState.customTags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        uiState.customTags.forEach { tag ->
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = SoftCardShape,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.custom_recipe_ingredients_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                uiState.ingredients.filter { it.name.isNotBlank() }.forEach { ingredient ->
                    Text(
                        text = "· " + listOf(ingredient.measure, ingredient.name).filter { it.isNotBlank() }.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (uiState.instructions.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = SoftCardShape,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.custom_recipe_instructions_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(uiState.instructions, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
