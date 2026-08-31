package com.dtraas.homestock.ui.recipes

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.repository.DietPreference
import com.dtraas.homestock.data.repository.MatchThreshold
import com.dtraas.homestock.data.repository.MealType
import com.dtraas.homestock.data.repository.RecipeFilters
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.components.SheetChip
import com.dtraas.homestock.ui.components.dashedBorder
import com.dtraas.homestock.ui.components.SheetEyebrow
import com.dtraas.homestock.ui.components.SheetPrimaryButton
import com.dtraas.homestock.ui.components.SheetRemovableChip
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import com.dtraas.homestock.ui.theme.TopAppBarContainerGradientEnd
import java.util.Locale
import kotlin.math.roundToInt

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
    onSavedImportedRecipe: (String) -> Unit = {},
    prefillImportUrl: String? = null,
    onPrefillImportUrlConsumed: () -> Unit = {},
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
    // Only actually needed while the AI tab is showing (see its call site below) — kept
    // subscribed at screen level regardless since [RecipesScreen] already recomposes on
    // household changes and this avoids a subscribe/unsubscribe blip every time that tab opens.
    val inventoryItems by application.container.inventoryRepository.observeInventoryWithProduct().collectAsState(initial = emptyList())
    val hintCardPreferences = application.container.hintCardPreferences
    val cookWithWhatYouHaveCollapsed by hintCardPreferences.recipesCookWithWhatYouHaveCollapsed.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].language
    val nowMillis = remember { System.currentTimeMillis() }
    val inventoryWishChips = remember(inventoryItems, nowMillis) {
        inventoryItems.map { item ->
            val days = item.expirationDate?.let { expiry -> ((expiry - nowMillis) / 86_400_000L).toInt() }
            InventoryWishChip(item.name, days)
        }
    }
    // Backs the persistent "Kook wat je hebt" hero card on Ontdek (see CookWithWhatYouHaveCard) —
    // same ≤3-days-out window AiRecipeTabContent's own expiring chips use, just summarized to a
    // count + up to 3 names instead of the full chip row.
    val expiringSoonChips = remember(inventoryWishChips) {
        inventoryWishChips.filter { it.daysUntilExpiry != null && it.daysUntilExpiry <= 3 }.sortedBy { it.daysUntilExpiry }
    }
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

    // "Opslaan bij mijn recepten" on the import preview — the other of its two exits, straight
    // to the saved recipe's own detail screen instead of [onImportedRecipe]'s editor.
    LaunchedEffect(Unit) {
        viewModel.savedImportedRecipeId.collect { id ->
            showImportDialog = false
            importUrl = ""
            onSavedImportedRecipe(id)
        }
    }

    // Arrived here via another app's share sheet (see MainActivity.sharedRecipeUrlFromIntent) —
    // open the import screen already fetching, same as pasting the link by hand would.
    LaunchedEffect(prefillImportUrl) {
        prefillImportUrl?.let { url ->
            importUrl = url
            showImportDialog = true
            viewModel.importRecipeFromUrl(url)
            onPrefillImportUrlConsumed()
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
                mineSearchQuery = uiState.mineSearchQuery,
                onMineSearchQueryChange = viewModel::onMineSearchQueryChange,
                viewMode = viewMode,
                onToggleViewMode = { viewMode = viewMode.toggled() },
                activeFilterCount = uiState.filters.activeCount,
                onOpenFilters = viewModel::openFilterSheet,
            )

            RecipesTabRow(selected = uiState.tab, onSelect = viewModel::selectTab)

            if (uiState.tab == RecipesTab.AI) {
                AiRecipeTabContent(
                    wish = generateWish,
                    onWishChange = { generateWish = it },
                    inventoryChips = inventoryWishChips,
                    isGenerating = uiState.isGenerating,
                    error = uiState.generateError,
                    onGenerate = { composedWish -> viewModel.generateRecipe(composedWish) },
                    modifier = Modifier.weight(1f),
                )
                return@Column
            }

            // Persistent, not an opt-in banner — replaces the old "Kook wat je hebt" promo card
            // that used to only show conditionally; now it's always here atop Ontdek, and tapping
            // it is the doorway into RecipesTab.INVENTORY's own full result set.
            if (uiState.tab == RecipesTab.BROWSE) {
                CookWithWhatYouHaveCard(
                    expiringSoonCount = expiringSoonChips.size,
                    expiringSoonNames = expiringSoonChips.take(3).map { it.name },
                    collapsed = cookWithWhatYouHaveCollapsed,
                    onToggleCollapsed = {
                        hintCardPreferences.setRecipesCookWithWhatYouHaveCollapsed(!cookWithWhatYouHaveCollapsed)
                    },
                    onClick = { viewModel.selectTab(RecipesTab.INVENTORY) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // "Zelf schrijven"/"Van een link" used to be a text-button row pinned above the list
            // — now folded into the AddRecipeTile that's always the grid's first item instead
            // (see the MINE branch of the `else` case below), so there's nothing to show here.
            if (uiState.tab == RecipesTab.MINE) {
                MineFilterRow(
                    counts = uiState.mineCounts,
                    selected = uiState.mineFilter,
                    onSelect = viewModel::setMineFilter,
                    sortOrder = uiState.mineSortOrder,
                    onToggleSort = viewModel::toggleMineSortOrder,
                )
                // Custom labels (see RecipeDetailScreen's tag editor) only ever exist on the
                // household's own saved/favorited recipes — never on a plain BROWSE/search result
                // (see RecipeSuggestion's doc) — so this filter row only ever shows here.
                if (uiState.availableCustomTags.isNotEmpty()) {
                    RecipeTagFilterRow(
                        customTags = uiState.availableCustomTags,
                        selectedCustomTags = uiState.selectedCustomTags,
                        onToggleCustom = viewModel::toggleCustomTagFilter,
                    )
                }
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
                // MINE never hits this branch — its grid always has at least the AddRecipeTile
                // (see the `else` case below), so an empty merged favorites+custom list still has
                // something to show instead of a dead-end "niets gevonden" message.
                uiState.recipes.isEmpty() && uiState.tab != RecipesTab.MINE -> {
                    val (emptyTitle, emptySubtitle) = when (uiState.tab) {
                        RecipesTab.INVENTORY -> stringResource(R.string.recipes_inventory_empty_title) to stringResource(R.string.recipes_inventory_empty_subtitle)
                        RecipesTab.BROWSE -> stringResource(R.string.recipes_empty_title) to stringResource(R.string.recipes_empty_subtitle)
                        // Unreachable — MINE/AI both return before this `when` is ever evaluated
                        // (MINE via the guard above, AI via its own early `return@Column`) — only
                        // kept here so this stays an exhaustive `when`.
                        RecipesTab.MINE, RecipesTab.AI -> stringResource(R.string.recipes_empty_title) to stringResource(R.string.recipes_empty_subtitle)
                    }
                    RecipesMessage(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.RestaurantMenu,
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                    )
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    val showLoadMore = uiState.tab == RecipesTab.BROWSE && (uiState.hasMore || uiState.isLoadingMore)
                    // Only Ontdek's plain browse is actually sorted by match ratio (see
                    // RecipeRepository.browseAllRecipes) — a name search deliberately keeps
                    // Spoonacular's own relevance order instead (see searchRecipesByName's doc),
                    // so this label would be misleading while a query is active.
                    if (uiState.tab == RecipesTab.BROWSE && uiState.searchQuery.isBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.recipes_browse_sorted_header),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.recipes_browse_sorted_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (uiState.tab == RecipesTab.MINE) {
                        // Fixed 2-column grid, no list/grid toggle (same reasoning as Uit je
                        // voorraad below) — AddRecipeTile is always the first item, so this grid
                        // is never actually empty even when the merged favorites+custom list is.
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "add_recipe") {
                                AddRecipeTile(onWriteOwnRecipe = onAddCustomRecipe, onImportFromUrl = { showImportDialog = true })
                            }
                            gridItemsIndexed(uiState.recipes, key = { _, recipe -> recipe.meal.id }) { index, recipe ->
                                MineRecipeTile(
                                    recipe = recipe,
                                    index = index,
                                    isFavorite = recipe.meal.id in uiState.favoriteIds,
                                    onClick = { onRecipeClick(recipe.meal.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(recipe.meal.id) },
                                )
                            }
                        }
                    } else if (uiState.tab == RecipesTab.INVENTORY) {
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
                                RecipeGridTile(
                                    recipe = recipe,
                                    isFavorite = recipe.meal.id in uiState.favoriteIds,
                                    onClick = { onRecipeClick(recipe.meal.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(recipe.meal.id) },
                                )
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
                                RecipeGridTile(
                                    recipe = recipe,
                                    isFavorite = recipe.meal.id in uiState.favoriteIds,
                                    onClick = { onRecipeClick(recipe.meal.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(recipe.meal.id) },
                                )
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

    if (showImportDialog) {
        ImportRecipeScreen(
            url = importUrl,
            onUrlChange = { importUrl = it },
            isImporting = uiState.isImporting,
            error = uiState.importError,
            preview = uiState.importPreview,
            onImport = { viewModel.importRecipeFromUrl(importUrl) },
            onChangeUrl = viewModel::dismissImportPreview,
            onSave = viewModel::saveImportedRecipe,
            onReview = viewModel::reviewImportedRecipe,
            onDismiss = {
                if (!uiState.isImporting) {
                    showImportDialog = false
                    importUrl = ""
                    viewModel.dismissImportError()
                    viewModel.dismissImportPreview()
                }
            },
        )
    }

    if (uiState.showFilterSheet) {
        RecipesFilterSheet(
            draftFilters = uiState.draftFilters,
            allergenOwners = uiState.allergenOwners,
            resultCount = uiState.filterPreviewCount,
            isLoadingCount = uiState.isLoadingFilterPreview,
            onMatchThresholdChange = viewModel::updateDraftMatchThreshold,
            onReadyMinutesChange = viewModel::updateDraftReadyMinutes,
            onMealTypeChange = viewModel::updateDraftMealType,
            onDietPreferenceChange = viewModel::updateDraftDietPreference,
            onToggleAllergen = viewModel::toggleDraftAllergen,
            onClearAll = viewModel::clearDraftFilters,
            onApply = viewModel::applyFilters,
            onDismiss = viewModel::dismissFilterSheet,
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

/** One row of the "HIERMEE GAAT AI AAN DE SLAG" card — an inventory item [name] and, when it has
 *  a houdbaarheidsdatum set, [daysUntilExpiry] (negative once already past it, still shown so an
 *  overdue item isn't silently dropped from the "use this soon" nudge). */
private data class InventoryWishChip(val name: String, val daysUntilExpiry: Int?)

/** The six preset "waar heb je zin in" chips — their label text doubles as the phrase sent to
 *  Claude when selected (see [AiRecipeTabContent]'s composeWish), so there's no separate
 *  internal-only prompt string to keep in sync with the display label. */
private enum class RecipeWishPreset(@StringRes val labelRes: Int) {
    FAST(R.string.recipes_generate_wish_preset_fast),
    VEGETARIAN(R.string.recipes_generate_wish_preset_vegetarian),
    COMFORT(R.string.recipes_generate_wish_preset_comfort),
    BAKE(R.string.recipes_generate_wish_preset_bake),
    ASIAN(R.string.recipes_generate_wish_preset_asian),
    TWO_PEOPLE(R.string.recipes_generate_wish_preset_two),
}

/**
 * The "AI" tab's content — used to be a bottom sheet opened from a floating action button, now a
 * persistent tab (per the design review: "De knop rechtsonder moet weg, wordt vervangen door
 * AI") with the same content laid out inline instead of in a bottom sheet. Shows exactly
 * what [RecipesViewModel.generateRecipe] is about to send instead of leaving it implicit: the
 * inventory ingredients (expiring-soonest first), a switch to bias the prompt toward those, and
 * six one-tap wish presets above the free-text field. [onGenerate] receives the fully composed
 * wish string (presets + free text + the expiring-items hint, each optional), not just the raw
 * text field.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AiRecipeTabContent(
    wish: String,
    onWishChange: (String) -> Unit,
    inventoryChips: List<InventoryWishChip>,
    isGenerating: Boolean,
    error: GenerateRecipeError?,
    onGenerate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var useExpiringSoon by remember { mutableStateOf(true) }
    val selectedPresets = remember { mutableStateListOf<RecipeWishPreset>() }

    val expiringChips = remember(inventoryChips) {
        inventoryChips.filter { it.daysUntilExpiry != null }.sortedBy { it.daysUntilExpiry }
    }
    val plainChips = remember(inventoryChips) { inventoryChips.filter { it.daysUntilExpiry == null } }
    val orderedChips = expiringChips + plainChips
    val shownChips = orderedChips.take(8)
    val moreCount = orderedChips.size - shownChips.size

    val presetLabels = mapOf(
        RecipeWishPreset.FAST to stringResource(RecipeWishPreset.FAST.labelRes),
        RecipeWishPreset.VEGETARIAN to stringResource(RecipeWishPreset.VEGETARIAN.labelRes),
        RecipeWishPreset.COMFORT to stringResource(RecipeWishPreset.COMFORT.labelRes),
        RecipeWishPreset.BAKE to stringResource(RecipeWishPreset.BAKE.labelRes),
        RecipeWishPreset.ASIAN to stringResource(RecipeWishPreset.ASIAN.labelRes),
        RecipeWishPreset.TWO_PEOPLE to stringResource(RecipeWishPreset.TWO_PEOPLE.labelRes),
    )
    val expiringHint = stringResource(
        R.string.recipes_generate_ai_expiring_hint_format,
        expiringChips.take(5).joinToString(", ") { it.name },
    )

    fun composeWish(): String {
        val parts = mutableListOf<String>()
        selectedPresets.mapNotNullTo(parts) { presetLabels[it] }
        wish.trim().takeIf { it.isNotEmpty() }?.let(parts::add)
        if (useExpiringSoon && expiringChips.isNotEmpty()) parts += expiringHint
        return parts.joinToString(". ")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            SheetTitle(title = stringResource(R.string.recipes_generate_ai_title))
        }

        if (inventoryChips.isNotEmpty()) {
            Surface(shape = SoftCardShapeCompact, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SheetEyebrow(text = stringResource(R.string.recipes_generate_ai_sending_title))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shownChips.forEach { chip ->
                            if (chip.daysUntilExpiry != null) {
                                InfoChip(
                                    text = stringResource(
                                        R.string.recipes_generate_ai_chip_format,
                                        chip.name,
                                        pluralStringResource(R.plurals.recipes_generate_ai_chip_days_format, chip.daysUntilExpiry, chip.daysUntilExpiry),
                                    ),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            } else {
                                InfoChip(
                                    text = chip.name,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (moreCount > 0) {
                            InfoChip(
                                text = pluralStringResource(R.plurals.recipes_generate_ai_more_chips_format, moreCount, moreCount),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    if (expiringChips.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.recipes_generate_ai_use_expiring_label),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(checked = useExpiringSoon, onCheckedChange = { useExpiringSoon = it })
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetEyebrow(text = stringResource(R.string.recipes_generate_wish_section_title))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeWishPreset.entries.forEach { preset ->
                    SheetChip(
                        label = presetLabels.getValue(preset),
                        selected = preset in selectedPresets,
                        onClick = {
                            if (preset in selectedPresets) selectedPresets.remove(preset) else selectedPresets.add(preset)
                        },
                    )
                }
            }
            OutlinedTextField(
                value = wish,
                onValueChange = onWishChange,
                placeholder = { Text(stringResource(R.string.recipes_generate_ai_placeholder)) },
                singleLine = true,
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (error != null) {
            val (icon, messageRes) = when (error) {
                GenerateRecipeError.PREMIUM_REQUIRED -> Icons.Filled.WorkspacePremium to R.string.recipes_generate_ai_failed_premium
                GenerateRecipeError.NO_CONNECTION -> Icons.Filled.CloudOff to R.string.recipes_generate_ai_failed_no_connection
                GenerateRecipeError.UNKNOWN -> Icons.Filled.WifiOff to R.string.recipes_generate_ai_failed_unknown
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        SheetPrimaryButton(
            text = if (isGenerating) stringResource(R.string.recipes_generate_ai_loading) else stringResource(R.string.recipes_generate_ai_action),
            onClick = { onGenerate(composeWish()) },
            loading = isGenerating,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

/** Small display-only pill — not a [SheetChip], since these represent facts ("what's being
 *  sent"), not a selectable option. */
@Composable
private fun InfoChip(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = CircleShape, color = containerColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Full-screen import flow (used to be an [AlertDialog]) for
 * [RecipesViewModel.importRecipeFromUrl] — a URL card (auto-filled from the clipboard the
 * moment this opens, if there's a plausible link already there), then once [preview] is in, a
 * found-recipe summary with real stat counts, a peek at the parsed ingredients, an honest
 * "scraped text isn't always perfect" disclaimer, and a hint about the faster share-sheet route
 * in (see MainActivity.sharedRecipeUrlFromIntent). The household still chooses between saving
 * the draft as-is or reviewing it first in CustomRecipeEditScreen — see [RecipeRepository.importRecipeFromUrl]'s
 * doc for why an import is never saved silently.
 */
@Composable
private fun ImportRecipeScreen(
    url: String,
    onUrlChange: (String) -> Unit,
    isImporting: Boolean,
    error: ImportRecipeError?,
    preview: RecipeImportPreview?,
    onImport: () -> Unit,
    onChangeUrl: () -> Unit,
    onSave: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var isEditingUrl by remember { mutableStateOf(url.isBlank()) }
    var pastedFromClipboard by remember { mutableStateOf(false) }

    // Auto-detects a URL already on the clipboard the instant this screen opens — almost always
    // true in practice (a browser/another app's share sheet is how a link ends up here to begin
    // with) — and fetches it right away, same as MainActivity's share-sheet entry point does.
    // Only when nothing was already typed/shared in, so this never clobbers that.
    LaunchedEffect(Unit) {
        if (url.isBlank()) {
            val clipped = clipboardManager.getText()?.text?.trim()
            if (!clipped.isNullOrEmpty() && looksLikeUrl(clipped)) {
                onUrlChange(clipped)
                pastedFromClipboard = true
                isEditingUrl = false
                onImport()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ImportRecipeHeader(onDismiss = onDismiss)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (isEditingUrl) {
                        ImportUrlEditor(
                            url = url,
                            onUrlChange = onUrlChange,
                            enabled = !isImporting,
                            onPaste = { clipboardManager.getText()?.text?.let(onUrlChange) },
                        )
                    } else {
                        ImportUrlCard(
                            url = url,
                            pastedFromClipboard = pastedFromClipboard,
                            onChangeClick = {
                                isEditingUrl = true
                                onChangeUrl()
                            },
                        )
                    }

                    if (isImporting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(
                                text = stringResource(messageRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }

                    if (preview != null) {
                        ImportFoundCard(preview)
                        ImportFoundIngredientsCard(preview.detail.ingredients)
                        ImportDisclaimerBanner()
                    }

                    ImportShareHintCard()
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (preview != null) {
                        Button(
                            onClick = onSave,
                            enabled = !isImporting,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.recipes_import_save_action), modifier = Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(
                            onClick = onReview,
                            enabled = !isImporting,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.recipes_import_review_action), modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                isEditingUrl = false
                                onImport()
                            },
                            enabled = !isImporting && url.isNotBlank(),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(stringResource(R.string.recipes_import_fetch_action))
                        }
                    }
                }
            }
        }
    }
}

/** True for a plain, single-token `http(s)://…` string — the only shape worth auto-fetching
 *  without asking; anything else (a sentence, a search query someone happened to have copied)
 *  just leaves the URL field for manual entry instead of firing a doomed fetch. */
private fun looksLikeUrl(text: String): Boolean =
    !text.contains(" ") && (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true))

/** The registrable domain a [url] was fetched from ("leukerecepten.nl", "www." stripped) — for
 *  the found-recipe card's subtitle, since [RecipeRepository.importRecipeFromUrl]'s
 *  [com.dtraas.homestock.data.repository.RecipeDetail] itself carries no source-site field of
 *  its own. Null for anything [Uri] can't make sense of, rather than showing a raw URL fragment. */
private fun hostNameOf(url: String): String? = Uri.parse(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }

@Composable
private fun ImportRecipeHeader(onDismiss: () -> Unit) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = contentColor)
        }
        Text(
            text = stringResource(R.string.recipes_import_title),
            style = MaterialTheme.typography.headlineSmall,
            color = contentColor,
            modifier = Modifier.padding(start = 16.dp),
        )
        Text(
            text = stringResource(R.string.recipes_import_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = OnTopAppBarContainerAccent,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
        )
    }
}

/** The URL, editable — shown instead of [ImportUrlCard] until there's something worth locking
 *  into a read-only pill, or right after tapping "Wijzigen" on that pill. */
@Composable
private fun ImportUrlEditor(url: String, onUrlChange: (String) -> Unit, enabled: Boolean, onPaste: () -> Unit) {
    Column {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = { Text(stringResource(R.string.recipes_import_url_placeholder)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        // A link is almost always shared into this flow from somewhere else (a browser, another
        // app's share sheet), so it's already on the clipboard more often than not — one tap
        // beats switching apps to copy it, then switching back to paste.
        TextButton(onClick = onPaste, enabled = enabled, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.recipes_import_url_paste_action), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** The URL, read-only — a clipboard icon, the link itself (clipped, not wrapped — this card is
 *  about confirming *which* link, not reading it in full), and a caption naming where it came
 *  from with a "Wijzigen" escape hatch back to [ImportUrlEditor]. */
@Composable
private fun ImportUrlCard(url: String, pastedFromClipboard: Boolean, onChangeClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShape,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    if (pastedFromClipboard) {
                        Text(
                            text = stringResource(R.string.recipes_import_pasted_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.recipes_import_change_url_action),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onChangeClick),
                    )
                }
            }
        }
    }
}

/** The found-recipe summary — a thumbnail (or the same [Icons.Filled.RestaurantMenu] placeholder
 *  every other recipe tile falls back to), a "Gevonden" badge, the title, a "bron · tijd ·
 *  porties" line built only from whichever of those [RecipeImportPreview.detail] actually has,
 *  and three real stat tiles. */
@Composable
private fun ImportFoundCard(preview: RecipeImportPreview) {
    val detail = preview.detail
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = SoftImageShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp),
                ) {
                    if (detail.thumbnailUrl != null) {
                        AsyncImage(
                            model = detail.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.RestaurantMenu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.recipes_import_found_badge).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    val metaParts = listOfNotNull(
                        hostNameOf(preview.sourceUrl),
                        detail.readyInMinutes?.let { stringResource(R.string.recipes_ready_in_minutes_format, it) },
                        detail.servings?.let { pluralStringResource(R.plurals.recipes_import_servings_format, it, it) },
                    )
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ImportStatTile(
                    value = preview.totalIngredientCount.toString(),
                    label = stringResource(R.string.recipes_import_stat_ingredients),
                    modifier = Modifier.weight(1f),
                )
                ImportStatTile(
                    value = preview.stepCount.toString(),
                    label = stringResource(R.string.recipes_import_stat_steps),
                    modifier = Modifier.weight(1f),
                )
                ImportStatTile(
                    value = "${preview.matchedIngredientCount}/${preview.totalIngredientCount}",
                    label = stringResource(R.string.recipes_import_stat_in_stock),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ImportStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A peek at the parsed ingredients — the first few as chips, the rest folded into a single
 *  "+N" chip rather than listing all of them here; the full list is what the save/review buttons
 *  below lead to either way. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportFoundIngredientsCard(ingredients: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val visibleCount = 4
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.recipes_import_found_ingredients_title).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ingredients.take(visibleCount).forEach { (name, measure) ->
                    val label = listOf(measure, name).filter { it.isNotBlank() }.joinToString(" ")
                    IngredientFoundChip(label)
                }
                val overflow = ingredients.size - visibleCount
                if (overflow > 0) {
                    IngredientFoundChip(stringResource(R.string.recipes_import_overflow_format, overflow))
                }
            }
        }
    }
}

@Composable
private fun IngredientFoundChip(label: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Honest on purpose — nothing in [RecipeRepository.importRecipeFromUrl]'s response actually
 *  flags *which* step or ingredient might be wrong (there's no per-line confidence score coming
 *  back from the schema.org/Claude-fallback parse), so this stays a general nudge toward "Eerst
 *  nakijken en bewerken" rather than pointing at a specific line it has no real signal for. */
@Composable
private fun ImportDisclaimerBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = SoftCardShapeCompact,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.recipes_import_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** Points at the real ACTION_SEND entry point (see MainActivity.sharedRecipeUrlFromIntent and
 *  its intent-filter in AndroidManifest.xml) — purely informational, not a button, since there's
 *  nothing this screen itself can do to trigger another app's share sheet. */
@Composable
private fun ImportShareHintCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShape,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = SoftBadgeShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.recipes_import_share_hint_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.recipes_import_share_hint_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Switches between the household's own merged favorites+custom list, its inventory-matched
 *  picks, browsing Spoonacular, and the AI form — see [RecipesTab]. */
@Composable
private fun RecipesTabRow(selected: RecipesTab, onSelect: (RecipesTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Ontdek, Mijn recepten, AI, Uit je voorraad — per expliciet verzoek. Favorieten en Eigen
        // recepten zijn niet langer twee losse tabbladen (een recept kan allebei zijn), maar één
        // gecombineerde "Mijn recepten" met Alles/Favoriet/Zelf-chips erin (zie MineFilterRow) in
        // plaats van een harde tab-knip.
        val tabs = listOf(
            RecipesTab.BROWSE to R.string.recipes_tab_browse,
            RecipesTab.MINE to R.string.recipes_tab_mine,
            RecipesTab.AI to R.string.recipes_tab_ai,
            RecipesTab.INVENTORY to R.string.recipes_tab_inventory,
        )
        tabs.forEach { (tab, labelRes) ->
            if (tab == RecipesTab.AI) {
                // Sparkle + bold label, same plain colors as the other three chips now (used to
                // also get its own coral container color, dropped per explicit request) — AI is
                // still a different kind of tab (a form, not a result list, see RecipesTab's
                // doc), just signaled by the icon/weight alone rather than a background color too.
                FilterChip(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    label = { Text(stringResource(labelRes), fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            } else {
                FilterChip(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

/**
 * Persistent "Kook wat je hebt" card, always shown atop Ontdek (BROWSE) — not an opt-in banner
 * the way this used to work. [TopAppBarContainerGradientEnd] is the same dark-green token the
 * app's gradient headers bottom out to (see MoreScreen's PremiumCard for the same reuse), so it
 * reads as a promoted shortcut rather than just another recipe result. Tapping the card anywhere
 * (collapsed or not) still jumps to [RecipesTab.INVENTORY] — collapsing it only hides the badge/
 * subtitle/arrow below the title, it never turns off the shortcut itself. The chevron is its own
 * tap target (an [IconButton] inside the card's own clickable area), so toggling [collapsed]
 * doesn't also fire [onClick] — same [HintCardPreferences]-backed persisted collapse as
 * [ExpiringSoonCard]/the Maaltijden missing-ingredients bar.
 */
@Composable
private fun CookWithWhatYouHaveCard(
    expiringSoonCount: Int,
    expiringSoonNames: List<String>,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TopAppBarContainerGradientEnd),
        shape = SoftCardShape,
    ) {
        Row(
            // Collapsed, this row holds nothing but the one-line title — vertical padding
            // drops from 16dp to 6dp so the card hugs it closely instead of leaving what
            // looked like two blank lines above/below.
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (collapsed) 6.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!collapsed) {
                    if (expiringSoonCount > 0) {
                        Surface(shape = RoundedCornerShape(percent = 50), color = MaterialTheme.colorScheme.secondary) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.recipes_hero_expiring_badge, expiringSoonCount, expiringSoonCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.recipes_hero_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = if (collapsed) 0.dp else 8.dp),
                )
                if (!collapsed) {
                    Text(
                        text = if (expiringSoonNames.isNotEmpty()) {
                            stringResource(R.string.recipes_hero_card_subtitle_format, expiringSoonNames.joinToString(", "))
                        } else {
                            stringResource(R.string.recipes_hero_card_subtitle_empty)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnTopAppBarContainerAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!collapsed) {
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.padding(start = 12.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.recipes_hero_card_cta_cd),
                        tint = TopAppBarContainerGradientEnd,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
            }
            // A plain clickable box instead of IconButton while collapsed — IconButton enforces
            // its own 48dp minimum touch target internally regardless of the row's own padding
            // (same reasoning as QuantityStepper's RepeatingIconButton), which alone would have
            // kept this row tall no matter how far the padding above dropped. Expanded, there's
            // enough else going on in the row that the full-size button doesn't stand out the
            // same way, so it stays there.
            if (collapsed) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleCollapsed),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.hint_card_expand_cd),
                        tint = Color.White,
                    )
                }
            } else {
                IconButton(onClick = onToggleCollapsed, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.hint_card_collapse_cd),
                        tint = Color.White,
                        modifier = Modifier.rotate(180f),
                    )
                }
            }
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
    mineSearchQuery: String,
    onMineSearchQueryChange: (String) -> Unit,
    viewMode: RecipesViewMode,
    onToggleViewMode: () -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.align(Alignment.CenterStart).padding(end = 48.dp)) {
                Text(
                    text = stringResource(R.string.recipes_title),
                    style = MaterialTheme.typography.headlineSmall,
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
            // View-mode toggle only — the allergen filter moved down beside the search field
            // (see below), matching Voorraad's own search-row-plus-filter-button layout. Mijn
            // recepten and Uit je voorraad both have their own fixed grid (no list/grid toggle —
            // see RecipesScreen's `else` branch), and AI has no list at all, so this button only
            // ever shows for Ontdek.
            if (tab == RecipesTab.BROWSE) {
                IconButton(onClick = onToggleViewMode, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        imageVector = if (viewMode == RecipesViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                        contentDescription = stringResource(
                            if (viewMode == RecipesViewMode.LIST) R.string.recipes_show_as_grid_cd else R.string.recipes_show_as_list_cd,
                        ),
                        tint = contentColor,
                    )
                }
            }
        }
        if (tab == RecipesTab.BROWSE || tab == RecipesTab.MINE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                // Ontdek submits a fresh Spoonacular query on IME action (see onSearch); Mijn
                // recepten's own list is small and already fully loaded, so it just live-filters
                // as you type instead (see RecipesUiState.mineSearchQuery's doc) — same field,
                // two different query/onChange sources depending on which tab is showing.
                SearchField(
                    query = if (tab == RecipesTab.MINE) mineSearchQuery else searchQuery,
                    onQueryChange = if (tab == RecipesTab.MINE) onMineSearchQueryChange else onSearchQueryChange,
                    placeholder = stringResource(
                        if (tab == RecipesTab.MINE) R.string.recipes_mine_search_placeholder else R.string.recipes_search_placeholder,
                    ),
                    dense = true,
                    onSearch = onSearch,
                    // A white pill instead of the default outline styling, which would barely
                    // read against the green gradient — same white-on-green pairing as
                    // Voorraad's header search field.
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
                    modifier = Modifier.weight(1f),
                )
                // Allergenen zijn alleen zinvol tegen Spoonacular's brede catalogus op
                // Ontdekken — Mijn recepten/Uit je voorraad zijn al beperkt tot wat het
                // huishouden zelf al heeft opgeslagen of in voorraad heeft. Same white
                // FilledIconButton + coral dot badge as Voorraad's own filter button, next to
                // the search field instead of up with the title, per explicit request.
                if (tab == RecipesTab.BROWSE) {
                    Box {
                        FilledIconButton(
                            onClick = onOpenFilters,
                            shape = SoftCardShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = SageGreenPrimary,
                            ),
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.recipes_filter_cd))
                        }
                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ready-time slider steps for [RecipesFilterSheet]'s BEREIDINGSTIJD section — `null` is the
 *  slider's rightmost "alles" (no cap) position. A fixed discrete list rather than a free-form
 *  0-120 range: Spoonacular's `maxReadyTime` is a blunt filter anyway, and a handful of round
 *  numbers reads more predictably on a slider than an arbitrary minute count would. */
private val readyTimeSliderOptions: List<Int?> = listOf(10, 20, 30, 45, 60, null)

@StringRes
private fun matchThresholdLabelRes(threshold: MatchThreshold): Int = when (threshold) {
    MatchThreshold.ALL_IN_HOUSE -> R.string.recipes_filter_match_threshold_all_in_house
    MatchThreshold.MAX_3_MISSING -> R.string.recipes_filter_match_threshold_max_3_missing
    MatchThreshold.ANY -> R.string.recipes_filter_match_threshold_any
}

@StringRes
private fun mealTypeLabelRes(type: MealType): Int = when (type) {
    MealType.BREAKFAST -> R.string.recipes_filter_meal_type_breakfast
    MealType.LUNCH -> R.string.recipes_filter_meal_type_lunch
    MealType.DINNER -> R.string.recipes_filter_meal_type_dinner
    MealType.SIDE_DISH -> R.string.recipes_filter_meal_type_side_dish
    MealType.DESSERT -> R.string.recipes_filter_meal_type_dessert
}

@StringRes
private fun dietPreferenceLabelRes(diet: DietPreference): Int = when (diet) {
    DietPreference.VEGETARIAN -> R.string.recipes_filter_diet_vegetarian
    DietPreference.VEGAN -> R.string.recipes_filter_diet_vegan
    DietPreference.KETO -> R.string.recipes_filter_diet_keto
    DietPreference.PALEO -> R.string.recipes_filter_diet_paleo
    DietPreference.PESCETARIAN -> R.string.recipes_filter_diet_pescetarian
    DietPreference.LOW_FODMAP -> R.string.recipes_filter_diet_low_fodmap
}

/**
 * Recepten's "Filters" bottom sheet. Every edit here lands in [draftFilters] only — nothing
 * changes the recipe list on the screen behind it until [onApply] ("Toon resultaten") is tapped;
 * dismissing any other way (drag-down, scrim tap, back gesture — all surface as [onDismiss])
 * discards every edit instead. [resultCount] is [RecipeRepository.countMatchingRecipes]'s real,
 * debounced live count for [draftFilters] — see that function's doc for why it's cheap enough to
 * refresh on every edit, and for why it can't (and doesn't try to) reflect
 * [RecipeFilters.matchThreshold], which only ever narrows an already-fetched page client-side.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipesFilterSheet(
    draftFilters: RecipeFilters,
    allergenOwners: Map<Allergen, String>,
    resultCount: Int?,
    isLoadingCount: Boolean,
    onMatchThresholdChange: (MatchThreshold) -> Unit,
    onReadyMinutesChange: (Int?) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onDietPreferenceChange: (DietPreference) -> Unit,
    onToggleAllergen: (Allergen) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    var allergenSectionExpanded by remember { mutableStateOf(false) }
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(sheetContentPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    SheetTitle(title = stringResource(R.string.recipes_filter_sheet_title))
                    if (draftFilters.activeCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.recipes_filter_sheet_active_count,
                                draftFilters.activeCount,
                                draftFilters.activeCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (draftFilters.activeCount > 0) {
                    TextButton(onClick = onClearAll) {
                        Text(stringResource(R.string.recipes_filter_sheet_clear_all))
                    }
                }
            }

            // One removable chip per currently active choice — lets the household see (and undo)
            // everything they've set without opening each section below.
            if (draftFilters.activeCount > 0) {
                val removeCd = stringResource(R.string.recipes_filter_remove_chip_cd)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    if (draftFilters.matchThreshold != MatchThreshold.ANY) {
                        SheetRemovableChip(
                            label = stringResource(matchThresholdLabelRes(draftFilters.matchThreshold)),
                            removeCd = removeCd,
                            onRemove = { onMatchThresholdChange(MatchThreshold.ANY) },
                        )
                    }
                    draftFilters.maxReadyMinutes?.let { minutes ->
                        SheetRemovableChip(
                            label = stringResource(R.string.recipes_filter_ready_time_chip, minutes),
                            removeCd = removeCd,
                            onRemove = { onReadyMinutesChange(null) },
                        )
                    }
                    draftFilters.mealType?.let { type ->
                        SheetRemovableChip(
                            label = stringResource(mealTypeLabelRes(type)),
                            removeCd = removeCd,
                            onRemove = { onMealTypeChange(type) },
                        )
                    }
                    draftFilters.dietPreference?.let { diet ->
                        SheetRemovableChip(
                            label = stringResource(dietPreferenceLabelRes(diet)),
                            removeCd = removeCd,
                            onRemove = { onDietPreferenceChange(diet) },
                        )
                    }
                    RecipeRepository.filterableAllergens.filter { it in draftFilters.excludedAllergens }.forEach { allergen ->
                        SheetRemovableChip(
                            label = stringResource(allergen.labelRes),
                            removeCd = removeCd,
                            onRemove = { onToggleAllergen(allergen) },
                        )
                    }
                }
            }

            SheetEyebrow(text = stringResource(R.string.recipes_filter_section_match_threshold), modifier = Modifier.padding(top = 24.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                listOf(MatchThreshold.ALL_IN_HOUSE, MatchThreshold.MAX_3_MISSING, MatchThreshold.ANY).forEach { threshold ->
                    SheetChip(
                        label = stringResource(matchThresholdLabelRes(threshold)),
                        selected = draftFilters.matchThreshold == threshold,
                        onClick = { onMatchThresholdChange(threshold) },
                    )
                }
            }

            SheetEyebrow(text = stringResource(R.string.recipes_filter_section_ready_time), modifier = Modifier.padding(top = 24.dp))
            ReadyTimeSlider(minutes = draftFilters.maxReadyMinutes, onChange = onReadyMinutesChange, modifier = Modifier.padding(top = 4.dp))

            SheetEyebrow(text = stringResource(R.string.recipes_filter_section_meal_type), modifier = Modifier.padding(top = 20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                MealType.entries.forEach { type ->
                    SheetChip(
                        label = stringResource(mealTypeLabelRes(type)),
                        selected = draftFilters.mealType == type,
                        onClick = { onMealTypeChange(type) },
                    )
                }
            }

            SheetEyebrow(text = stringResource(R.string.recipes_filter_section_diet), modifier = Modifier.padding(top = 20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                DietPreference.entries.forEach { diet ->
                    SheetChip(
                        label = stringResource(dietPreferenceLabelRes(diet)),
                        selected = draftFilters.dietPreference == diet,
                        onClick = { onDietPreferenceChange(diet) },
                    )
                }
                // "Glutenvrij" isn't a DietPreference — it toggles Allergen.GLUTEN, the exact same
                // exclusion the ALLERGENEN VERMIJDEN section below controls, so the two always
                // agree instead of tracking two independent flags for the same thing.
                SheetChip(
                    label = stringResource(R.string.recipes_filter_diet_gluten_free),
                    selected = Allergen.GLUTEN in draftFilters.excludedAllergens,
                    onClick = { onToggleAllergen(Allergen.GLUTEN) },
                )
            }

            Column(modifier = Modifier.padding(top = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { allergenSectionExpanded = !allergenSectionExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SheetEyebrow(text = stringResource(R.string.recipes_filter_section_allergens), modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.rotate(if (allergenSectionExpanded) 180f else 0f),
                    )
                }
                if (allergenSectionExpanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        RecipeRepository.filterableAllergens.forEach { allergen ->
                            SheetChip(
                                label = stringResource(allergen.labelRes),
                                selected = allergen in draftFilters.excludedAllergens,
                                onClick = { onToggleAllergen(allergen) },
                            )
                        }
                    }
                    // "X heeft dit in hun profiel staan" — only for an allergen that's both
                    // currently excluded and actually traceable to a household member's own
                    // profile (see RecipesViewModel's allergenOwners doc); an allergen the
                    // household just toggled on themselves gets no banner, since there's nothing
                    // true to attribute it to.
                    val attributed = RecipeRepository.filterableAllergens
                        .filter { it in draftFilters.excludedAllergens }
                        .mapNotNull { allergen -> allergenOwners[allergen]?.let { owner -> allergen to owner } }
                    if (attributed.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            attributed.forEach { (allergen, owner) ->
                                val allergenLabel = stringResource(allergen.labelRes)
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = SoftCardShapeCompact) {
                                    Text(
                                        text = stringResource(R.string.recipes_filter_allergen_owner_banner, owner, allergenLabel),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val footerCountText = when {
                isLoadingCount -> stringResource(R.string.recipes_filter_footer_counting)
                resultCount != null -> pluralStringResource(R.plurals.recipes_filter_footer_result_count, resultCount, resultCount)
                else -> null
            }
            Column(modifier = Modifier.padding(top = 28.dp)) {
                if (footerCountText != null) {
                    Text(
                        text = footerCountText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                SheetPrimaryButton(text = stringResource(R.string.recipes_filter_footer_show_results), onClick = onApply)
            }
        }
    }
}

/** [RecipesFilterSheet]'s BEREIDINGSTIJD control — a discrete slider over [readyTimeSliderOptions] rather than a free-form range, see that list's doc. */
@Composable
private fun ReadyTimeSlider(minutes: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val index = readyTimeSliderOptions.indexOf(minutes).takeIf { it >= 0 } ?: readyTimeSliderOptions.lastIndex
    Column(modifier = modifier) {
        Slider(
            value = index.toFloat(),
            onValueChange = { raw ->
                val snapped = raw.roundToInt().coerceIn(0, readyTimeSliderOptions.lastIndex)
                onChange(readyTimeSliderOptions[snapped])
            },
            valueRange = 0f..readyTimeSliderOptions.lastIndex.toFloat(),
            steps = readyTimeSliderOptions.size - 2,
            colors = SliderDefaults.colors(activeTrackColor = SageGreenPrimary, thumbColor = SageGreenPrimary),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.recipes_filter_ready_time_min, readyTimeSliderOptions.first()!!),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.recipes_filter_ready_time_alles),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** [RecipesTab.MINE]'s "Alles · 18 / Favoriet · 7 / Zelf · 6" row, plus a sort toggle at the far
 *  end (see [MineSortOrder]) — the counts come from [MineCounts], not the length of the already-
 *  filtered [RecipesUiState.recipes], so switching chips doesn't change what the others report. */
@Composable
private fun MineFilterRow(
    counts: MineCounts,
    selected: MineFilter,
    onSelect: (MineFilter) -> Unit,
    sortOrder: MineSortOrder,
    onToggleSort: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == MineFilter.ALL,
                onClick = { onSelect(MineFilter.ALL) },
                label = { Text(stringResource(R.string.recipes_mine_filter_count_format, stringResource(R.string.recipes_mine_filter_all), counts.all)) },
            )
            FilterChip(
                selected = selected == MineFilter.FAVORITE,
                onClick = { onSelect(MineFilter.FAVORITE) },
                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = {
                    Text(
                        stringResource(
                            R.string.recipes_mine_filter_count_format,
                            stringResource(R.string.recipes_mine_filter_favorite),
                            counts.favorite,
                        ),
                    )
                },
            )
            FilterChip(
                selected = selected == MineFilter.CUSTOM,
                onClick = { onSelect(MineFilter.CUSTOM) },
                label = {
                    Text(
                        stringResource(
                            R.string.recipes_mine_filter_count_format,
                            stringResource(R.string.recipes_mine_filter_custom),
                            counts.custom,
                        ),
                    )
                },
            )
        }
        IconButton(onClick = onToggleSort, modifier = Modifier.padding(start = 4.dp)) {
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = stringResource(
                    if (sortOrder == MineSortOrder.RECENT) R.string.recipes_mine_sort_to_name_cd else R.string.recipes_mine_sort_to_recent_cd,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Chip row for filtering Mijn recepten by the household's own custom labels (see
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
 * Image-forward alternative to [RecipeRow] for [RecipesViewMode.GRID] — a circular match-ratio
 * ring in the top-start corner of the photo (a checkmark instead once everything's in house), a
 * heart overlay in the top-end corner for [onToggleFavorite], and below the name: an orange
 * "gebruikt X" pill when [RecipeSuggestion.expiringIngredientUsed] names something close to its
 * use-by date this recipe would use up, then a "35 min · 4 pers. · N missend" meta line built
 * from whichever of [RecipeSuggestion.readyInMinutes]/[RecipeSuggestion.servings]/the missing
 * count are actually known — the "sluit aan bij jouw taal/regio" globe (see [RecipeRow]'s own
 * use of it) moves into that same line instead of a third image overlay competing with the ring
 * and the heart for the same corner.
 */
@Composable
private fun RecipeGridTile(
    recipe: RecipeSuggestion,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
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
                MatchRingBadge(matchRatio = matchRatioOf(recipe), modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                FavoriteHeartButton(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = recipe.meal.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe.expiringIngredientUsed != null) {
                    ExpiringIngredientPill(recipe.expiringIngredientUsed, modifier = Modifier.padding(top = 6.dp))
                }
                RecipeMetaLine(recipe, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Fraction of [RecipeSuggestion.totalIngredientCount] matched — shared by [RecipeGridTile] and
 *  [MineRecipeTile]'s [MatchRingBadge]. Null (no ring at all) when the source this suggestion
 *  came from never carries a match count (e.g. a plain search result) — same null/unknown vs.
 *  0/N-matched distinction [RecipeSuggestion.matchCount]'s own doc describes. */
private fun matchRatioOf(recipe: RecipeSuggestion): Float? {
    val total = recipe.totalIngredientCount ?: return null
    val matched = recipe.matchCount ?: return null
    return if (total > 0) matched.toFloat() / total else null
}

private fun missingCountOf(recipe: RecipeSuggestion): Int? {
    val total = recipe.totalIngredientCount ?: return null
    val matched = recipe.matchCount ?: return null
    return (total - matched).coerceAtLeast(0)
}

/** Circular match-ratio badge — a checkmark once everything's in huis, otherwise a progress ring
 *  with the percentage inside. Renders nothing at all when [matchRatio] is null (see
 *  [matchRatioOf]), so callers can place this unconditionally without their own null check. */
@Composable
private fun MatchRingBadge(matchRatio: Float?, modifier: Modifier = Modifier) {
    if (matchRatio == null) return
    Surface(shape = CircleShape, color = Color.White, modifier = modifier.size(34.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (matchRatio >= 1f) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.recipes_match_complete_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                CircularProgressIndicator(
                    progress = { matchRatio },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = "${(matchRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Heart overlay shared by every grid tile flavor — toggles [RecipesViewModel.toggleFavorite].
 *  Same fixed 34dp circle (and same 6dp corner padding) as [MatchRingBadge] — this used to just
 *  wrap tightly around its icon+padding instead (a smaller ~28dp circle), which still put its
 *  top edge at the same 6dp from the card corner as the ring badge but, being a smaller circle,
 *  left its CENTER a few dp higher than the ring badge's center: the two looked like they sat on
 *  different lines even though both were "6dp from the top". Explicitly sizing both the same
 *  makes their centers line up automatically. */
@Composable
private fun FavoriteHeartButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.85f), onClick = onToggle, modifier = modifier.size(34.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(if (isFavorite) R.string.recipes_favorite_remove_cd else R.string.recipes_favorite_add_cd),
                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ExpiringIngredientPill(ingredientName: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(percent = 50), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Text(
            text = stringResource(R.string.recipes_used_expiring_pill_format, ingredientName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** "35 min · 4 pers. · N missend" (+ the taal/regio globe, trailing) — whichever parts are
 *  actually known for [recipe]; renders nothing at all when none are. */
@Composable
private fun RecipeMetaLine(recipe: RecipeSuggestion, modifier: Modifier = Modifier) {
    val missingCount = missingCountOf(recipe)
    val metaParts = buildList {
        recipe.readyInMinutes?.let { add(stringResource(R.string.recipes_ready_in_minutes_format, it)) }
        recipe.servings?.let { add(stringResource(R.string.recipes_servings_short_format, it)) }
        if (missingCount != null && missingCount > 0) {
            add(pluralStringResource(R.plurals.recipes_missing_count_format, missingCount, missingCount))
        }
    }
    if (metaParts.isEmpty() && !recipe.matchesArea) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (recipe.matchesArea) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = stringResource(R.string.recipes_area_match_cd),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 4.dp).size(12.dp),
            )
        }
    }
}

/**
 * [RecipesTab.MINE]'s own tile — [RecipeGridTile] plus two things unique to "wat is van mij":
 * a colored band behind the photo (rotating through the app's 3 container colors by [index], so
 * a grid of mostly photo-less custom recipes doesn't read as a monotone wall of grey), and an
 * "EIGEN" badge for a hand-entered/imported recipe (see [RecipeRepository.CUSTOM_ID_PREFIX]) —
 * a favorited *Spoonacular* recipe has no such badge, only the heart. Tags (see
 * [RecipeDetailScreen]'s tag editor) render as a small chip row underneath — the one piece of
 * per-recipe detail that's genuinely MINE-only, Ontdek/search results never carry any (see
 * [RecipeSuggestion.tags]'s doc).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MineRecipeTile(
    recipe: RecipeSuggestion,
    index: Int,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val isCustom = recipe.meal.id.startsWith(RecipeRepository.CUSTOM_ID_PREFIX)
    val (bandColor, onBandColor) = when (index % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShapeCompact,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(bandColor, SoftImageShape)) {
                if (recipe.meal.thumbnailUrl != null) {
                    AsyncImage(
                        model = recipe.meal.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(SoftImageShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.RestaurantMenu,
                        contentDescription = null,
                        tint = onBandColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                    )
                }
                MatchRingBadge(matchRatio = matchRatioOf(recipe), modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                FavoriteHeartButton(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
                if (isCustom) {
                    Surface(
                        shape = SoftBadgeShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.recipes_custom_badge_short),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = onBandColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = recipe.meal.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe.expiringIngredientUsed != null) {
                    ExpiringIngredientPill(recipe.expiringIngredientUsed, modifier = Modifier.padding(top = 4.dp))
                }
                RecipeMetaLine(recipe, modifier = Modifier.padding(top = 4.dp))
                if (recipe.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        recipe.tags.forEach { tag ->
                            Surface(shape = RoundedCornerShape(percent = 50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * First tile in [RecipesTab.MINE]'s grid — dashed border matching [EmptySlotAddButton]'s own
 * style elsewhere in the app, folding what used to be two separate "Eigen recept toevoegen"/
 * "Importeer van URL" text buttons above the list into one always-present tile instead, the same
 * height as every recipe tile beside it rather than a full-width row that pushed the grid down.
 */
@Composable
private fun AddRecipeTile(onWriteOwnRecipe: () -> Unit, onImportFromUrl: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .dashedBorder(MaterialTheme.colorScheme.outlineVariant, cornerRadius = 12.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = SoftBadgeShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(10.dp).size(20.dp),
            )
        }
        Text(
            text = stringResource(R.string.recipes_add_tile_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.padding(top = 10.dp).clickable(onClick = onWriteOwnRecipe),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(R.string.recipes_add_write_own_action),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onImportFromUrl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(R.string.recipes_add_from_link_action),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
