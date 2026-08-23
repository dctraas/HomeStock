package com.dtraas.homestock.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestaurantMenu
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftCardShape
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
            initializer {
                RecipesViewModel(
                    application.container.recipeRepository,
                    application.container.householdMembersRepository,
                    application.container.shoppingListRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].language
    var showGenerateDialog by remember { mutableStateOf(false) }
    var generateWish by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(RecipesViewMode.GRID) }
    val snackbarHostState = remember { SnackbarHostState() }
    val addedToListMessage = stringResource(R.string.recipes_added_to_shopping_list)
    val undoLabel = stringResource(R.string.common_undo)

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

    // Hero card's "Op lijst" undo — same generic message every time (which recipe it came from
    // doesn't change what got added), the item ids ride along for [RecipesViewModel.undoAddMissingIngredients].
    LaunchedEffect(Unit) {
        viewModel.missingIngredientsAdded.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = addedToListMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoAddMissingIngredients(event.itemIds)
        }
    }

    Scaffold(
        // RecipesHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Icon-only — "De knop 'AI-recept' hoeft geen tekst op het label te bevatten."
            FloatingActionButton(
                onClick = { showGenerateDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.recipes_generate_fab))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Zoekbalk zit nu altijd zichtbaar in de header zelf (BROWSE-tab), niet meer achter
            // een tap-to-reveal zoek-icoon; de allergenfilter en de lijst/rooster-toggle zijn
            // samengevoegd achter één "Meer opties"-knop helemaal rechtsboven in de header.
            RecipesHeader(
                tab = uiState.tab,
                subtitleCount = if (uiState.tab == RecipesTab.INVENTORY && !uiState.isLoading && uiState.recipes.isNotEmpty()) {
                    uiState.recipes.size
                } else {
                    null
                },
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { query ->
                    viewModel.onSearchQueryChange(query)
                    if (query.isEmpty()) viewModel.clearSearch()
                },
                onSearch = viewModel::search,
                viewMode = viewMode,
                onToggleViewMode = { viewMode = viewMode.toggled() },
                excludedAllergens = uiState.excludedAllergens,
                onToggleAllergen = viewModel::toggleAllergen,
            )

            RecipesTabRow(selected = uiState.tab, onSelect = viewModel::selectTab)

            if (uiState.tab == RecipesTab.CUSTOM) {
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

            // Custom labels (see RecipeDetailScreen's tag editor) only ever exist on the
            // household's own saved recipes — never on a BROWSE/search result (see
            // RecipeSuggestion's doc) — so the filter row only shows on Favorieten/Eigen recepten.
            if (uiState.tab == RecipesTab.FAVORITES || uiState.tab == RecipesTab.CUSTOM) {
                RecipeTagFilterRow(
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
                        RecipesTab.INVENTORY -> stringResource(R.string.recipes_inventory_empty_title) to stringResource(R.string.recipes_inventory_empty_subtitle)
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
                    if (uiState.tab == RecipesTab.INVENTORY) {
                        // Fixed hero + 2-column grid, not the plain list/grid toggle the other
                        // tabs offer — the hero (spanning both columns) is what used to be the
                        // separate "Kook wat je hebt" promo card's destination, now this tab's
                        // own top result instead of a second tap away.
                        val hero = uiState.recipes.first()
                        val rest = uiState.recipes.drop(1)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = hero.meal.id, span = { GridItemSpan(maxLineSpan) }) {
                                HeroRecipeCard(
                                    recipe = hero,
                                    onClick = { onRecipeClick(hero.meal.id) },
                                    onAddMissingToList = { viewModel.addMissingIngredientsToShoppingList(hero) },
                                )
                            }
                            gridItems(rest, key = { it.meal.id }) { recipe ->
                                RecipeGridTile(recipe = recipe, onClick = { onRecipeClick(recipe.meal.id) })
                            }
                        }
                    } else if (viewMode == RecipesViewMode.LIST) {
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

/** Switches between the household's inventory-matched picks, browsing Spoonacular, its
 *  favorites, and its own custom recipes — see [RecipesTab]. */
@Composable
private fun RecipesTabRow(selected: RecipesTab, onSelect: (RecipesTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Ontdekken, Favorieten, Mijn recepten, Uit je voorraad — Uit je voorraad verhuisd naar
        // plek 4, op uitdrukkelijk verzoek.
        val tabs = listOf(
            RecipesTab.BROWSE to R.string.recipes_tab_browse,
            RecipesTab.FAVORITES to R.string.recipes_tab_favorites,
            RecipesTab.CUSTOM to R.string.recipes_tab_custom,
            RecipesTab.INVENTORY to R.string.recipes_tab_inventory,
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
 * The top pick on Uit je voorraad — spans both grid columns, photo-forward, with the same
 * used/total ingredient ratio as [RecipeGridTile]'s pill (just spelled out inline here) plus,
 * when something's missing, a chip naming it and a direct "Op lijst" shortcut so adding what's
 * missing doesn't require opening the recipe first.
 */
@Composable
private fun HeroRecipeCard(
    recipe: RecipeSuggestion,
    onClick: () -> Unit,
    onAddMissingToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                if (recipe.meal.thumbnailUrl != null) {
                    AsyncImage(
                        model = recipe.meal.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(SoftImageShape),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(SoftImageShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestaurantMenu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                if (recipe.matchCount != null && recipe.totalIngredientCount != null) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.recipes_match_ratio_format, recipe.matchCount, recipe.totalIngredientCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recipe.meal.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe.missingIngredients.isNotEmpty()) {
                    Surface(
                        shape = SoftCardShapeCompact,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.recipes_hero_missing_format, recipe.missingIngredients.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Button(
                        onClick = onAddMissingToList,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recipes_add_missing_short), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/**
 * Green gradient header (same Keukenlinnen pattern as Voorraad/Productdetail/Boodschappenlijst/
 * Maaltijdplanner/Instellingen/Statistieken/Premium/Activiteiten this round) — replaces the flat
 * HomeStockTopAppBar. Title (+ item-count subtitle on Uit je voorraad) sits on the first line
 * with one "Meer opties" button pinned at the very top-right, per the design review ("Deze knop
 * staat helemaal rechtsboven in de header"). On Ontdekken, a persistent search bar shows below
 * instead of the old tap-to-reveal search icon ("De zoekknop mag als zoekbalk verschijnen in de
 * Header zelf"). Everything that isn't the search itself — the allergenfilter and the list/
 * rooster-toggle — lives behind that one Meer-opties menu instead of its own icon
 * ("De overige knoppen mogen achter een Meer opties knop geplaatst worden").
 */
@Composable
private fun RecipesHeader(
    tab: RecipesTab,
    subtitleCount: Int?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    viewMode: RecipesViewMode,
    onToggleViewMode: () -> Unit,
    excludedAllergens: Set<Allergen>,
    onToggleAllergen: (Allergen) -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recipes_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                )
                // Only on Uit je voorraad — the other tabs don't have a single natural "N of
                // something" count to summarize (Ontdekken's page size isn't meaningful,
                // Favorieten/Eigen already show their own count as the list length at a glance).
                if (subtitleCount != null) {
                    Text(
                        text = pluralStringResource(R.plurals.recipes_inventory_subtitle, subtitleCount, subtitleCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnTopAppBarContainerAccent,
                    )
                }
            }
            Box {
                val hasActiveAllergenFilter = tab == RecipesTab.BROWSE && excludedAllergens.isNotEmpty()
                IconButton(onClick = { menuExpanded = true }) {
                    if (hasActiveAllergenFilter) {
                        BadgedBox(badge = { Badge() }) {
                            Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.recipes_more_options_cd), tint = contentColor)
                        }
                    } else {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.recipes_more_options_cd), tint = contentColor)
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    // Vast rooster op Uit je voorraad (hero + 2-koloms grid) — geen toggle nodig.
                    if (tab != RecipesTab.INVENTORY) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (viewMode == RecipesViewMode.LIST) R.string.recipes_show_as_grid_cd else R.string.recipes_show_as_list_cd,
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (viewMode == RecipesViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onToggleViewMode()
                                menuExpanded = false
                            },
                        )
                    }
                    // Allergenen zijn alleen zinvol tegen Spoonacular's brede catalogus op
                    // Ontdekken — Favorieten/Eigen/Uit je voorraad zijn al beperkt tot wat het
                    // huishouden zelf al heeft opgeslagen of in voorraad heeft. Een scheiding
                    // en een eigen kopje maken duidelijk dat dit een apart blok is, los van de
                    // lijst/rooster-toggle erboven.
                    if (tab == RecipesTab.BROWSE) {
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.recipes_allergen_menu_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        RecipeRepository.filterableAllergens.forEach { allergen ->
                            val selected = allergen in excludedAllergens
                            DropdownMenuItem(
                                text = { Text(stringResource(allergen.labelRes)) },
                                trailingIcon = {
                                    if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                onClick = { onToggleAllergen(allergen) },
                            )
                        }
                    }
                }
            }
        }
        if (tab == RecipesTab.BROWSE) {
            SearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.recipes_search_placeholder),
                dense = true,
                onSearch = onSearch,
                // A white pill instead of the default outline styling, which would barely read
                // against the green gradient — same white-on-green pairing as Voorraad's header
                // search field.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = SageGreenPrimary,
                    unfocusedTextColor = SageGreenPrimary,
                    focusedLeadingIconColor = SageGreenPrimary,
                    unfocusedLeadingIconColor = SageGreenPrimary,
                    focusedTrailingIconColor = SageGreenPrimary,
                    unfocusedTrailingIconColor = SageGreenPrimary,
                    cursorColor = SageGreenPrimary,
                    focusedPlaceholderColor = SageGreenPrimary.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = SageGreenPrimary.copy(alpha = 0.6f),
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** Chip row for filtering Favorieten/Eigen recepten by the household's own custom labels (see
 *  RecipeDetailScreen's tag editor) — an AND match against every selected chip (see
 *  [RecipesViewModel.toggleCustomTagFilter]). The 3 fixed preset chips (Snel/Kindvriendelijk/
 *  Restjes) this row used to also offer are gone, per explicit request — only per-recipe custom
 *  labels remain. */
@Composable
private fun RecipeTagFilterRow(
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
                // matchCount/totalIngredientCount are only set for inventory-based results (see
                // RecipeRepository.suggestRecipes) — browsing everything or searching by name
                // doesn't have a per-recipe ingredient count to show without fetching full
                // details for every result, so this row is skipped entirely there unless the
                // recipe at least matches the household's language/cuisine.
                if (recipe.matchCount != null || recipe.matchesArea) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        if (recipe.matchCount != null && recipe.totalIngredientCount != null) {
                            Text(
                                text = stringResource(R.string.recipes_match_ratio_format, recipe.matchCount, recipe.totalIngredientCount),
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
                if (recipe.matchCount != null && recipe.totalIngredientCount != null) {
                    // Bottom-left of the image, per the design review — top-start is where the
                    // "sluit aan bij jouw taal/regio" globe badge lives instead (see below).
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.recipes_match_ratio_format, recipe.matchCount, recipe.totalIngredientCount),
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
