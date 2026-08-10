package com.dtraas.homestock.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.components.HomeStockTopAppBar

/**
 * Create/edit form for a hand-entered recipe (see [RecipeRepository.saveCustomRecipe]) —
 * [recipeId] null means "new recipe" (form starts empty, no delete action); non-null means
 * "edit" (form is pre-filled from [CustomRecipeEditViewModel.load], delete becomes available).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRecipeEditScreen(
    recipeId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: CustomRecipeEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CustomRecipeEditViewModel(recipeId, application.container.recipeRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.savedRecipeId) { uiState.savedRecipeId?.let(onSaved) }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (recipeId == null) R.string.custom_recipe_edit_title_new else R.string.custom_recipe_edit_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (recipeId != null) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.custom_recipe_delete_action))
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
            uiState.hasLoadError -> Column(
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
            else -> CustomRecipeForm(padding, uiState, viewModel)
        }
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

@Composable
private fun CustomRecipeForm(padding: PaddingValues, uiState: CustomRecipeEditUiState, viewModel: CustomRecipeEditViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.custom_recipe_name_label)) },
            placeholder = { Text(stringResource(R.string.custom_recipe_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            OutlinedTextField(
                value = uiState.category,
                onValueChange = viewModel::onCategoryChange,
                label = { Text(stringResource(R.string.custom_recipe_category_label)) },
                placeholder = { Text(stringResource(R.string.custom_recipe_category_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
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
        OutlinedTextField(
            value = uiState.readyInMinutes,
            onValueChange = viewModel::onReadyInMinutesChange,
            label = { Text(stringResource(R.string.custom_recipe_ready_minutes_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Text(
            text = stringResource(R.string.custom_recipe_ingredients_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp),
        )
        uiState.ingredients.forEach { ingredient ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = ingredient.name,
                    onValueChange = { viewModel.onIngredientNameChange(ingredient.localId, it) },
                    placeholder = { Text(stringResource(R.string.custom_recipe_ingredient_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ingredient.measure,
                    onValueChange = { viewModel.onIngredientMeasureChange(ingredient.localId, it) },
                    placeholder = { Text(stringResource(R.string.custom_recipe_ingredient_measure_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.width(96.dp).padding(start = 8.dp),
                )
                IconButton(onClick = { viewModel.removeIngredientRow(ingredient.localId) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.custom_recipe_remove_ingredient_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = viewModel::addIngredientRow, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.custom_recipe_add_ingredient), modifier = Modifier.padding(start = 8.dp))
        }

        Text(
            text = stringResource(R.string.custom_recipe_instructions_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp),
        )
        OutlinedTextField(
            value = uiState.instructions,
            onValueChange = viewModel::onInstructionsChange,
            placeholder = { Text(stringResource(R.string.custom_recipe_instructions_placeholder)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(160.dp),
        )

        if (uiState.showValidationError) {
            Text(
                text = stringResource(R.string.custom_recipe_validation_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (uiState.showSaveError) {
            Text(
                text = stringResource(R.string.custom_recipe_save_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = viewModel::save,
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.custom_recipe_save_action))
            }
        }
    }
}
