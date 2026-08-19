package com.dtraas.homestock.ui.recipes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.RecipeTag
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
    onRecipeClick: (String) -> Unit,
    onAddCustomRecipe: () -> Unit = {},
    onImportedRecipe: (String) -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: RecipesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecipesViewModel(application.container.recipeRepository, application.container.householdMembersRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].language
    var showGenerateDialog by remember { mutableStateOf(false) }
    var generateWish by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(RecipesViewMode.GRID) }

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

    LaunchedEffect(Unit) {
        viewModel.importedRecipeId.collect { id ->
            showImportDialog = false
            importUrl = ""
            onImportedRecipe(id)
        }
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                // Recepten is a bottom-nav tab, not a screen pushed onto the back stack — a
                // "Terug" button here had nowhere meaningful to go.
                actions = {
                    // List/grid toggle — same pattern as Voorraad's. Available on all three tabs;
                    // an image-forward grid is just as useful for Favorieten/Eigen recepten.
                    IconButton(onClick = { viewMode = viewMode.toggled() }) {
                        Icon(
                            imageVector = if (viewMode == RecipesViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                            contentDescription = stringResource(
                                if (viewMode == RecipesViewMode.LIST) R.string.recipes_show_as_grid_cd else R.string.recipes_show_as_list_cd,
                            ),
                        )
                    }
                    // Only on BROWSE — Favorieten/Eigen recepten are the household's own short
                    // lists, nothing there to "genereer met AI" against. Moved off its own FAB
                    // (which sat on top of the list) into a top-bar action instead.
                    if (uiState.tab == RecipesTab.BROWSE) {
                        IconButton(onClick = { showGenerateDialog = true }) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = stringResource(R.string.recipes_generate_ai_button),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RecipesTabRow(selected = uiState.tab, onSelect = viewModel::selectTab)

            if (uiState.tab == RecipesTab.BROWSE) {
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
                        dense = true,
                    )
                    // Icon-only rather than a labeled Button — this row already reads as
                    // "search" from the field's own placeholder/icon, so a second "Zoeken"
                    // label next to it was redundant weight.
                    IconButton(
                        onClick = viewModel::search,
                        enabled = uiState.searchQuery.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_product_action))
                    }
                    // Used to be an always-visible chip row of its own between the search bar
                    // and the list; folded into this dropdown instead so BROWSE's first
                    // screenful is mostly recipes, not filter chrome.
                    AllergenFilterMenuButton(
                        excludedAllergens = uiState.excludedAllergens,
                        onToggle = viewModel::toggleAllergen,
                    )
                }
            } else if (uiState.tab == RecipesTab.CUSTOM) {
                Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    TextButton(onClick = onAddCustomRecipe, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recipes_add_custom_button), modifier = Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { showImportDialog = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recipes_import_url_button), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Tags (see RecipeTag) only ever exist on the household's own saved recipes — never
            // on a BROWSE/search result (see RecipeSuggestion's doc) — so the filter row only
            // shows on Favorieten/Eigen recepten.
            if (uiState.tab == RecipesTab.FAVORITES || uiState.tab == RecipesTab.CUSTOM) {
                RecipeTagFilterRow(
                    selectedTags = uiState.selectedTags,
                    onToggle = viewModel::toggleTagFilter,
                    customTags = uiState.availableCustomTags,
                    selectedCustomTags = uiState.selectedCustomTags,
                    onToggleCustom = viewModel::toggleCustomTagFilter,
                )
            }

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                uiState.loadError != null -> {
                    // QUOTA_EXCEEDED gets its own icon/message — "Geen verbinding" would be
                    // actively wrong here (the device is fine; Spoonacular's own rate/point
                    // limit is what's blocking this) and would send someone off troubleshooting
                    // their wifi for nothing.
                    val (errorIcon, errorTitle, errorSubtitle) = when (uiState.loadError) {
                        RecipesLoadError.QUOTA_EXCEEDED -> Triple(
                            Icons.Filled.Timer,
                            stringResource(R.string.recipes_error_quota_title),
                            stringResource(R.string.recipes_error_quota_subtitle),
                        )
                        else -> Triple(
                            Icons.Filled.WifiOff,
                            stringResource(R.string.recipes_error_title),
                            stringResource(R.string.recipes_error_subtitle),
                        )
                    }
                    RecipesMessage(
                        modifier = Modifier.fillMaxSize(),
                        icon = errorIcon,
                        title = errorTitle,
                        subtitle = errorSubtitle,
                        retryLabel = stringResource(R.string.scan_result_retry),
                        onRetry = viewModel::search,
                    )
                }
                uiState.recipes.isEmpty() -> {
                    val (emptyTitle, emptySubtitle) = when (uiState.tab) {
                        RecipesTab.BROWSE -> stringResource(R.string.recipes_empty_title) to stringResource(R.string.recipes_empty_subtitle)
                        RecipesTab.FAVORITES -> stringResource(R.string.recipes_favorites_empty_title) to stringResource(R.string.recipes_favorites_empty_subtitle)
                        RecipesTab.CUSTOM -> stringResource(R.string.recipes_custom_empty_title) to stringResource(R.string.recipes_custom_empty_subtitle)
                    }
                    RecipesMessage(
                        modifier = Modifier.fillMaxSize(),
                        icon = if (uiState.tab == RecipesTab.FAVORITES) Icons.Filled.Favorite else Icons.Filled.RestaurantMenu,
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                    )
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    val showLoadMore = uiState.tab == RecipesTab.BROWSE && (uiState.hasMore || uiState.isLoadingMore)
                    if (viewMode == RecipesViewMode.LIST) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.recipes, key = { it.meal.id }) { recipe ->
                                RecipeRow(recipe = recipe, onClick = { onRecipeClick(recipe.meal.id) })
                            }
                            // Only BROWSE ever has more than one page — see RecipesViewModel.loadMore.
                            // A tappable "load more" row rather than infinite-scroll-on-appear: this
                            // list already renders full detail (image + name) per row, so scroll-
                            // triggered loading would fire a Spoonacular call just from fast flinging
                            // past the bottom, not necessarily genuine interest in more results.
                            if (showLoadMore) {
                                item(key = "load_more") {
                                    LoadMoreRow(isLoading = uiState.isLoadingMore, onClick = viewModel::loadMore)
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItems(uiState.recipes, key = { it.meal.id }) { recipe ->
                                RecipeGridTile(recipe = recipe, onClick = { onRecipeClick(recipe.meal.id) })
                            }
                            if (showLoadMore) {
                                item(key = "load_more", span = { GridItemSpan(maxLineSpan) }) {
                                    LoadMoreRow(isLoading = uiState.isLoadingMore, onClick = viewModel::loadMore)
                                }
                            }
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

    if (showImportDialog) {
        ImportRecipeDialog(
            url = importUrl,
            onUrlChange = { importUrl = it },
            isImporting = uiState.isImporting,
            error = uiState.importError,
            onImport = { viewModel.importRecipeFromUrl(importUrl) },
            onDismiss = {
                if (!uiState.isImporting) {
                    showImportDialog = false
                    viewModel.dismissImportError()
                }
            },
        )
    }
}

/** List/grid toggle for the recipe results — same idea as Voorraad's, purely a display choice, not persisted. */
private enum class RecipesViewMode {
    LIST,
    GRID,
    ;

    fun toggled(): RecipesViewMode = if (this == LIST) GRID else LIST
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

/** Lets the user paste a recipe page's URL for [RecipesViewModel.importRecipeFromUrl] to parse —
 *  same loading/error-inline pattern as [GenerateRecipeDialog]. Confirming navigates to
 *  CustomRecipeEditScreen's import-prefill flow (not straight to RecipeDetailScreen the way
 *  AI-generation does) so the household reviews the scraped/AI-extracted result before it's
 *  actually saved — see [RecipeRepository.importRecipeFromUrl]'s doc for why. */
@Composable
private fun ImportRecipeDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    isImporting: Boolean,
    error: ImportRecipeError?,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipes_import_url_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.recipes_import_url_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    placeholder = { Text(stringResource(R.string.recipes_import_url_placeholder)) },
                    singleLine = true,
                    enabled = !isImporting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (isImporting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.recipes_import_url_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                if (error != null) {
                    val (icon, messageRes) = when (error) {
                        ImportRecipeError.PREMIUM_REQUIRED -> Icons.Filled.WorkspacePremium to R.string.recipes_import_url_failed_premium
                        ImportRecipeError.NO_CONNECTION -> Icons.Filled.CloudOff to R.string.recipes_import_url_failed_no_connection
                        ImportRecipeError.UNKNOWN -> Icons.Filled.WifiOff to R.string.recipes_import_url_failed_unknown
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
            Button(onClick = onImport, enabled = !isImporting && url.isNotBlank()) {
                Text(stringResource(R.string.recipes_import_url_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** Switches between browsing Spoonacular, the household's favorites, and its own custom recipes — see [RecipesTab]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipesTabRow(selected: RecipesTab, onSelect: (RecipesTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tabs = listOf(
            RecipesTab.BROWSE to R.string.recipes_tab_browse,
            RecipesTab.FAVORITES to R.string.recipes_tab_favorites,
            RecipesTab.CUSTOM to R.string.recipes_tab_custom,
        )
        tabs.forEach { (tab, labelRes) ->
            FilterChip(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

/**
 * Filter icon + dropdown for the curated allergen subset (see
 * [RecipeRepository.filterableAllergens]) — a checked item means that allergen is excluded.
 * Used to be an always-visible chip row of its own between the search bar and the list; folded
 * into a dropdown instead (same pattern as Voorraad's filter menu) so BROWSE's first screenful
 * is mostly recipes, not filter chrome.
 */
@Composable
private fun AllergenFilterMenuButton(excludedAllergens: Set<Allergen>, onToggle: (Allergen) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.padding(start = 4.dp)) {
            if (excludedAllergens.isEmpty()) {
                Icon(Icons.Filled.FilterAlt, contentDescription = stringResource(R.string.recipes_allergen_filter_label))
            } else {
                val activeLabels = excludedAllergens.map { stringResource(it.labelRes) }.joinToString(", ")
                BadgedBox(badge = { Badge() }) {
                    Icon(
                        Icons.Filled.FilterAlt,
                        contentDescription = stringResource(R.string.recipes_allergen_filter_active_cd_format, activeLabels),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            RecipeRepository.filterableAllergens.forEach { allergen ->
                val selected = allergen in excludedAllergens
                DropdownMenuItem(
                    text = { Text(stringResource(allergen.labelRes)) },
                    trailingIcon = {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = { onToggle(allergen) },
                )
            }
        }
    }
}

/** Chip row for filtering Favorieten/Eigen recepten by [RecipeTag] plus any custom labels a
 *  household has typed themselves (see RecipeDetailScreen's tag editor) — an AND match against
 *  every selected chip (see [RecipesViewModel.toggleTagFilter]/[RecipesViewModel.toggleCustomTagFilter]).
 *  Only 3 fixed tags exist, so a plain always-visible row reads faster than a dropdown here,
 *  unlike [AllergenFilterMenuButton]'s much longer list; custom labels ride along in the same row. */
@Composable
private fun RecipeTagFilterRow(
    selectedTags: Set<RecipeTag>,
    onToggle: (RecipeTag) -> Unit,
    customTags: List<String>,
    selectedCustomTags: Set<String>,
    onToggleCustom: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecipeTag.entries.forEach { tag ->
            FilterChip(
                selected = tag in selectedTags,
                onClick = { onToggle(tag) },
                label = { Text(stringResource(tag.labelRes)) },
            )
        }
        customTags.forEach { label ->
            FilterChip(
                selected = label in selectedCustomTags,
                onClick = { onToggleCustom(label) },
                label = { Text(label) },
            )
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

/** Bottom-of-list row for [RecipesViewModel.loadMore] — a button while idle, a spinner while [isLoading] (a page fetch in flight). */
@Composable
private fun LoadMoreRow(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.recipes_load_more))
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

/**
 * Image-forward alternative to [RecipeRow] for [RecipesViewMode.GRID] — same underlying data
 * (matchCount/matchesArea badges), just laid over the photo instead of in a text row, since a
 * 2-column grid tile doesn't have the width for a full detail row below the name.
 */
@Composable
private fun RecipeGridTile(recipe: RecipeSuggestion, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShapeCompact,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                if (recipe.meal.thumbnailUrl != null) {
                    AsyncImage(
                        model = recipe.meal.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(SoftImageShape),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(SoftImageShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestaurantMenu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                if (recipe.matchCount != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.recipes_match_count_format, recipe.matchCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                if (recipe.matchesArea) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = stringResource(R.string.recipes_area_match_cd),
                            modifier = Modifier.padding(5.dp).size(12.dp),
                        )
                    }
                }
            }
            Text(
                text = recipe.meal.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
