package com.dtraas.homestock.ui.recipes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape

/**
 * Browses Spoonacular's recipe catalog by default (see RecipeRepository.browseAllRecipes) — not
 * narrowed to what's in inventory, though a recipe from the household's language/cuisine still
 * gets a badge (see [RecipeRow]). The search field switches to a name search instead, and
 * "Genereer recept met AI" is a separate, AI-authored alternative to either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    onRecipeClick: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: RecipesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecipesViewModel(application.container.recipeRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].language
    var showGenerateDialog by remember { mutableStateOf(false) }
    var generateWish by remember { mutableStateOf("") }

    // languageTag as the key rather than Unit: an in-app language switch (Instellingen >
    // Algemeen > Taal) recreates the whole activity, but keying here means this also behaves
    // correctly if that ever changes — refetches with the new cuisine/region boost instead of
    // silently keeping stale results for the old language.
    LaunchedEffect(languageTag) { viewModel.load(languageTag) }

    LaunchedEffect(Unit) {
        viewModel.generatedRecipeId.collect { id ->
            showGenerateDialog = false
            generateWish = ""
            onRecipeClick(id)
        }
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { query ->
                        viewModel.onSearchQueryChange(query)
                        if (query.isEmpty()) viewModel.clearSearch()
                    },
                    placeholder = stringResource(R.string.recipes_search_placeholder),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = viewModel::search,
                    enabled = uiState.searchQuery.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.search_product_action))
                }
            }
            TextButton(
                onClick = { showGenerateDialog = true },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.recipes_generate_ai_button), modifier = Modifier.padding(start = 8.dp))
            }
            AllergenFilterRow(
                excludedAllergens = uiState.excludedAllergens,
                onToggle = viewModel::toggleAllergen,
            )
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                uiState.hasError -> RecipesMessage(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.WifiOff,
                    title = stringResource(R.string.recipes_error_title),
                    subtitle = stringResource(R.string.recipes_error_subtitle),
                    retryLabel = stringResource(R.string.scan_result_retry),
                    onRetry = viewModel::search,
                )
                uiState.recipes.isEmpty() -> RecipesMessage(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.RestaurantMenu,
                    title = stringResource(R.string.recipes_empty_title),
                    subtitle = stringResource(R.string.recipes_empty_subtitle),
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.recipes_beta_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.recipes, key = { it.meal.id }) { recipe ->
                            RecipeRow(recipe = recipe, onClick = { onRecipeClick(recipe.meal.id) })
                        }
                    }
                }
            }
        }
    }

    if (showGenerateDialog) {
        GenerateRecipeDialog(
            wish = generateWish,
            onWishChange = { generateWish = it },
            isGenerating = uiState.isGenerating,
            error = uiState.generateError,
            onGenerate = { viewModel.generateRecipe(generateWish) },
            onDismiss = {
                if (!uiState.isGenerating) {
                    showGenerateDialog = false
                    viewModel.dismissGenerateError()
                }
            },
        )
    }
}

/** Lets the user optionally steer [RecipesViewModel.generateRecipe] with a free-text wish (e.g. "iets met kip en rijst"), then shows its loading/error state inline instead of navigating away before it's done. */
@Composable
private fun GenerateRecipeDialog(
    wish: String,
    onWishChange: (String) -> Unit,
    isGenerating: Boolean,
    error: GenerateRecipeError?,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipes_generate_ai_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.recipes_generate_ai_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = wish,
                    onValueChange = onWishChange,
                    placeholder = { Text(stringResource(R.string.recipes_generate_ai_placeholder)) },
                    singleLine = true,
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.recipes_generate_ai_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                if (error != null) {
                    val (icon, messageRes) = when (error) {
                        GenerateRecipeError.PREMIUM_REQUIRED -> Icons.Filled.WorkspacePremium to R.string.recipes_generate_ai_failed_premium
                        GenerateRecipeError.NO_CONNECTION -> Icons.Filled.CloudOff to R.string.recipes_generate_ai_failed_no_connection
                        GenerateRecipeError.UNKNOWN -> Icons.Filled.WifiOff to R.string.recipes_generate_ai_failed_unknown
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onGenerate, enabled = !isGenerating) {
                Text(stringResource(R.string.recipes_generate_ai_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** Toggleable chips for the curated allergen subset (see RecipeRepository.filterableAllergens) — a selected chip means that allergen is excluded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllergenFilterRow(excludedAllergens: Set<Allergen>, onToggle: (Allergen) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.recipes_allergen_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecipeRepository.filterableAllergens.forEach { allergen ->
                val selected = allergen in excludedAllergens
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(allergen) },
                    label = { Text(stringResource(allergen.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun RecipesMessage(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (retryLabel != null && onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
                Text(retryLabel)
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: RecipeSuggestion, onClick: () -> Unit) {
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
            if (recipe.meal.thumbnailUrl != null) {
                AsyncImage(
                    model = recipe.meal.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(SoftImageShape),
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = recipe.meal.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // matchCount is only non-null for inventory-based results (see
                // RecipeRepository.suggestRecipes) — browsing everything or searching by name
                // doesn't have a per-recipe ingredient count to show without fetching full
                // details for every result, so this row is skipped entirely there unless the
                // recipe at least matches the household's language/cuisine.
                if (recipe.matchCount != null || recipe.matchesArea) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        if (recipe.matchCount != null) {
                            Text(
                                text = stringResource(R.string.recipes_match_count_format, recipe.matchCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (recipe.matchesArea) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = stringResource(R.string.recipes_area_match_cd),
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 6.dp).size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
