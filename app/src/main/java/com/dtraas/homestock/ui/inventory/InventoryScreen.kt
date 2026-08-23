package com.dtraas.homestock.ui.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.InventoryStockStatus
import com.dtraas.homestock.data.model.Location
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.ProductImage
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.components.color
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.components.onColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import com.dtraas.homestock.ui.theme.TopAppBarContainerGradientEnd
import java.io.File
import kotlinx.coroutines.launch

private enum class InventoryViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryScreen(
    onProductClick: (String) -> Unit,
    onNavigateToScan: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToReceiptScan: () -> Unit = {},
    onNavigateToAiRecognize: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    // True for exactly one composition right after opening the app from the expiry-reminder
    // notification (see MainActivity/ExpiryCheckWorker) — switches on the "Verloopt bijna"
    // quick filter so the products that notification was about are what's showing.
    showExpiringSoonOnOpen: Boolean = false,
    onShowExpiringSoonConsumed: () -> Unit = {},
    // Same idea as [showExpiringSoonOnOpen], but for arriving from Statistieken's "bijna op"
    // status tile instead of a system notification — see HomeStockApp's pendingInventoryFilter.
    showLowStockOnOpen: Boolean = false,
    onShowLowStockConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val viewModel: InventoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                InventoryViewModel(
                    inventoryRepository = application.container.inventoryRepository,
                    shoppingListRepository = application.container.shoppingListRepository,
                    activityLogRepository = application.container.activityLogRepository,
                    householdRepository = application.container.householdRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var viewMode by remember { mutableStateOf(InventoryViewMode.GRID) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    // Opens InventoryFilterSheet — the search field's trailing "tune" icon, replacing the old
    // always-visible sorteren/filteren/groeperen/weergave row with one bottom sheet holding all
    // four, per the Claude Design review.
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedBarcodes by remember { mutableStateOf(emptySet<String>()) }
    // Bonnetje scannen / AI-productherkenning in the "+" menu below are premium-only, same
    // gating as their entries in Instellingen/Meer and the (now-removed) Scannen tab.
    val isPremium by application.container.householdMembersRepository
        .observeHouseholdIsPremium()
        .collectAsState(initial = false)
    val selectionMode = selectedBarcodes.isNotEmpty()
    val deviceProfile = application.container.deviceProfile
    val displayName by deviceProfile.displayName.collectAsState()
    val photoPath by deviceProfile.photoPath.collectAsState()
    val unreadNoticeCount by application.container.dismissedNoticesStore.unreadCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val removedFormat = stringResource(R.string.inventory_removed_snackbar_format)
    val undoLabel = stringResource(R.string.common_undo)
    val addedToShoppingListMessage = stringResource(R.string.inventory_added_to_shopping_list_snackbar)
    val bulkAddedFormat = stringResource(R.string.inventory_bulk_added_to_shopping_list_format)
    val restockedFormat = stringResource(R.string.inventory_restocked_snackbar_format)

    // Drives the coral dot on the tune icon — any dimension the sheet controls being non-default.
    val hasActiveFilter = uiState.selectedCategory != null || uiState.favoritesOnly || uiState.lowStockOnly ||
        uiState.expiringSoonOnly || uiState.selectedLocation != null

    LaunchedEffect(Unit) {
        viewModel.restockEvents.collect { name ->
            snackbarHostState.showSnackbar(restockedFormat.format(name), duration = SnackbarDuration.Short)
        }
    }

    // Keyed on the flag itself (not Unit) so tapping the notification again while already on
    // Voorraad — e.g. after having since turned the filter back off — re-applies it, matching
    // MainActivity's onNewIntent semantics for pendingRoute.
    LaunchedEffect(showExpiringSoonOnOpen) {
        if (showExpiringSoonOnOpen) {
            viewModel.onExpiringSoonFilterChange(true)
            onShowExpiringSoonConsumed()
        }
    }

    LaunchedEffect(showLowStockOnOpen) {
        if (showLowStockOnOpen) {
            viewModel.onLowStockFilterChange(true)
            onShowLowStockConsumed()
        }
    }

    fun toggleSelected(barcode: String) {
        selectedBarcodes = if (barcode in selectedBarcodes) selectedBarcodes - barcode else selectedBarcodes + barcode
    }

    fun deleteWithUndo(item: InventoryItemWithProduct) {
        viewModel.removeFromInventory(item.barcode)
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = removedFormat.format(item.name),
                actionLabel = undoLabel,
                // showSnackbar defaults to SnackbarDuration.Indefinite whenever an
                // actionLabel is set, so without this the "ongedaan maken" snackbar
                // would never auto-dismiss.
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreItem(item)
            }
        }
    }

    fun addToShoppingListWithFeedback(item: InventoryItemWithProduct) {
        viewModel.addToShoppingList(item)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = addedToShoppingListMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    fun bulkDeleteSelected() {
        val items = uiState.groupedInventory.values.flatten().filter { it.barcode in selectedBarcodes }
        items.forEach { viewModel.removeFromInventory(it.barcode) }
        selectedBarcodes = emptySet()
        coroutineScope.launch {
            // Count is only known once the user has tapped delete, so this can't be resolved
            // via pluralStringResource (a @Composable call) like the other pre-resolved
            // snackbar strings in this file — same reasoning as bulkAddedFormat, but plural.
            val message = context.resources.getQuantityString(
                R.plurals.inventory_bulk_removed_snackbar_format, items.size, items.size,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                items.forEach { viewModel.restoreItem(it) }
            }
        }
    }

    fun bulkAddSelectedToShoppingList() {
        val items = uiState.groupedInventory.values.flatten().filter { it.barcode in selectedBarcodes }
        items.forEach { viewModel.addToShoppingList(it) }
        val count = items.size
        selectedBarcodes = emptySet()
        coroutineScope.launch {
            snackbarHostState.showSnackbar(bulkAddedFormat.format(count), duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        // InventoryHeader below already claims the status bar inset itself (selectionMode's
        // HomeStockTopAppBar does too, via its own default TopAppBarDefaults.windowInsets) —
        // without this, Scaffold's default contentWindowInsets (safeDrawing, top included
        // since there's no topBar) hands that same inset to `padding` too, stacking a second
        // status-bar-height gap above the header instead of it starting flush at the true top
        // of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        floatingActionButton = {
            if (!selectionMode) {
                AddFab(onClick = { showAddMenu = true })
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectionMode) {
                HomeStockTopAppBar(
                    title = {
                        Text(stringResource(R.string.inventory_selection_count_format, selectedBarcodes.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedBarcodes = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = ::bulkAddSelectedToShoppingList) {
                            Icon(
                                Icons.Filled.AddShoppingCart,
                                contentDescription = stringResource(R.string.inventory_bulk_add_to_shopping_list_cd),
                            )
                        }
                        IconButton(onClick = ::bulkDeleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.inventory_bulk_delete_cd))
                        }
                    },
                )
            } else {
                // Zoekbalk en filterknop zitten nu in de header zelf, net als het huishouden-
                // profiel-rijtje dat hiervoor los als HomeStockTopAppBar bovenaan stond — de
                // aparte Row eronder met SearchField + tune-knop is hierin opgegaan.
                InventoryHeader(
                    householdName = uiState.householdName,
                    photoPath = photoPath,
                    unreadNoticeCount = unreadNoticeCount,
                    onNotificationsClick = onNavigateToNotifications,
                    onProfileClick = { showProfileDialog = true },
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onFilterClick = { showFilterSheet = true },
                    hasActiveFilter = hasActiveFilter,
                )
            }

            // Grouping by category loses the order between categories (each still shows in
            // category order, not by whichever item within it sorts first) — for Houdbaarheid,
            // where seeing what's soonest across the whole voorraad is the point, render one
            // flat list instead of grouping by category at all.
            val isFlatSort = uiState.sortOption == InventorySortOption.EXPIRATION

            // Grouped by location instead of category once selected — same filtered/sorted
            // items either way, just bucketed differently for the section headers below.
            val isLocationGrouped = !isFlatSort && uiState.groupBy == InventoryGroupBy.LOCATION

            if (uiState.flatInventory.isEmpty()) {
                EmptyInventory(
                    isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedCategory != null ||
                        uiState.favoritesOnly || uiState.lowStockOnly || uiState.expiringSoonOnly ||
                        uiState.selectedLocation != null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (viewMode == InventoryViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (isFlatSort) {
                        items(uiState.flatInventory, key = { it.barcode }) { item ->
                            InventoryRow(
                                item = item,
                                selected = item.barcode in selectedBarcodes,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) toggleSelected(item.barcode) else onProductClick(item.barcode)
                                },
                                onLongClick = { toggleSelected(item.barcode) },
                                onIncrease = { viewModel.setQuantity(item.barcode, item.quantity + 1) },
                                onDecrease = { viewModel.setQuantity(item.barcode, item.quantity - 1) },
                                onDelete = { deleteWithUndo(item) },
                                onAddToShoppingList = { addToShoppingListWithFeedback(item) },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else if (isLocationGrouped) {
                        uiState.groupedByLocation.forEach { (location, itemsAtLocation) ->
                            stickyHeader {
                                LocationHeader(location, itemCount = itemsAtLocation.size)
                            }
                            items(itemsAtLocation, key = { it.barcode }) { item ->
                                InventoryRow(
                                    item = item,
                                    selected = item.barcode in selectedBarcodes,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) toggleSelected(item.barcode) else onProductClick(item.barcode)
                                    },
                                    onLongClick = { toggleSelected(item.barcode) },
                                    onIncrease = { viewModel.setQuantity(item.barcode, item.quantity + 1) },
                                    onDecrease = { viewModel.setQuantity(item.barcode, item.quantity - 1) },
                                    onDelete = { deleteWithUndo(item) },
                                    onAddToShoppingList = { addToShoppingListWithFeedback(item) },
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    } else {
                        uiState.groupedInventory.forEach { (category, itemsInCategory) ->
                            stickyHeader {
                                CategoryHeader(category, itemCount = itemsInCategory.size)
                            }
                            items(itemsInCategory, key = { it.barcode }) { item ->
                                InventoryRow(
                                    item = item,
                                    selected = item.barcode in selectedBarcodes,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) toggleSelected(item.barcode) else onProductClick(item.barcode)
                                    },
                                    onLongClick = { toggleSelected(item.barcode) },
                                    onIncrease = { viewModel.setQuantity(item.barcode, item.quantity + 1) },
                                    onDecrease = { viewModel.setQuantity(item.barcode, item.quantity - 1) },
                                    onDelete = { deleteWithUndo(item) },
                                    onAddToShoppingList = { addToShoppingListWithFeedback(item) },
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isFlatSort) {
                        items(uiState.flatInventory, key = { it.barcode }) { item ->
                            InventoryGridTile(
                                item = item,
                                selected = item.barcode in selectedBarcodes,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) toggleSelected(item.barcode) else onProductClick(item.barcode)
                                },
                                onLongClick = { toggleSelected(item.barcode) },
                                onIncrease = { viewModel.setQuantity(item.barcode, item.quantity + 1) },
                                onDecrease = { viewModel.setQuantity(item.barcode, item.quantity - 1) },
                                onAddToShoppingList = { addToShoppingListWithFeedback(item) },
                            )
                        }
                    } else if (isLocationGrouped) {
                        uiState.groupedByLocation.forEach { (location, itemsAtLocation) ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LocationHeader(location, itemCount = itemsAtLocation.size, horizontalPadding = 0.dp)
                            }
                            items(itemsAtLocation, key = { it.barcode }) { item ->
                                InventoryGridTile(
                                    item = item,
                                    selected = item.barcode in selectedBarcodes,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) toggleSelected(item.barcode) else onProductClick(item.barcode)
                                    },
                                    onLongClick = { toggleSelected(item.barcode) },
                                    onIncrease = { viewModel.setQuantity(item.barcode, item.quantity + 1) },
                                    onDecrease = { viewModel.setQuantity(item.barcode, item.quantity - 1) },
                                    onAddToShoppingList = { addToShoppingListWithFeedback(item) },
                                )
                            }
                        }
                    } else {
                        uiState.groupedInventory.forEach { (category, itemsInCategory) ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CategoryHeader(category, itemCount = itemsInCategory.size, horizontalPadding = 0.dp)
                            }
                            items(itemsInCategory, key = { it.barcode }) { item ->
                                InventoryGridTile(
                                    item = item,
                                    selected = item.barcode in selectedBarcodes,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) toggleSelected(item.barcode) else onProductClick(item.barcode)
                                    },
                                    onLongClick = { toggleSelected(item.barcode) },
                                    onIncrease = { viewModel.setQuantity(item.barcode, item.quantity + 1) },
                                    onDecrease = { viewModel.setQuantity(item.barcode, item.quantity - 1) },
                                    onAddToShoppingList = { addToShoppingListWithFeedback(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        val householdMembersRepository = application.container.householdMembersRepository
        ProfileEditDialog(
            displayName = displayName,
            photoPath = photoPath,
            onSaveName = { deviceProfile.setDisplayName(it) },
            onPhotoPicked = { uri ->
                coroutineScope.launch {
                    deviceProfile.setPhotoFromUri(uri)
                    householdMembersRepository.syncCurrentDevicePhoto()
                }
            },
            onRemovePhoto = {
                coroutineScope.launch {
                    deviceProfile.clearPhoto()
                    householdMembersRepository.syncCurrentDevicePhoto()
                }
            },
            onDismiss = { showProfileDialog = false },
        )
    }

    if (showAddMenu) {
        AddMenuDialog(
            isPremium = isPremium,
            onBarcodeScan = onNavigateToScan,
            onSearchByName = onNavigateToSearch,
            onReceiptScan = onNavigateToReceiptScan,
            onAiRecognize = onNavigateToAiRecognize,
            onNavigateToPremium = onNavigateToPremium,
            onDismiss = { showAddMenu = false },
        )
    }

    if (showFilterSheet) {
        InventoryFilterSheet(
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            sortSelected = uiState.sortOption,
            onSortSelected = viewModel::onSortOptionChange,
            groupBySelected = uiState.groupBy,
            onGroupBySelected = viewModel::onGroupByChange,
            showGroupBy = uiState.availableLocations.size > 1,
            selectedCategory = uiState.selectedCategory,
            favoritesOnly = uiState.favoritesOnly,
            lowStockOnly = uiState.lowStockOnly,
            expiringSoonOnly = uiState.expiringSoonOnly,
            availableLocations = uiState.availableLocations,
            selectedLocation = uiState.selectedLocation,
            onCategorySelected = viewModel::onCategoryFilterChange,
            onFavoritesToggle = viewModel::onFavoritesFilterChange,
            onLowStockToggle = viewModel::onLowStockFilterChange,
            onExpiringSoonToggle = viewModel::onExpiringSoonFilterChange,
            onLocationSelected = viewModel::onLocationFilterChange,
            onDismiss = { showFilterSheet = false },
        )
    }
}

/**
 * Household profile row + search field + filter button, all folded into one green gradient
 * header (same Keukenlinnen pattern as Productdetail/Boodschappenlijst/Maaltijdplanner/
 * Instellingen/Statistieken/Premium/Activiteiten this round) — replaces the flat
 * HomeStockTopAppBar plus the separate always-visible search Row that used to sit right below
 * it, per the design review ("Neem de Zoekbalk en filter op in de Header").
 */
@Composable
private fun InventoryHeader(
    householdName: String?,
    photoPath: String?,
    unreadNoticeCount: Int,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    hasActiveFilter: Boolean,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SageGreenPrimary, TopAppBarContainerGradientEnd)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .padding(bottom = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Meldingen is no longer its own bottom-nav tab — this is the way to reach it, at
            // the far-left glance position. The red counter badge tracks unread developer
            // notices.
            IconButton(onClick = onNotificationsClick) {
                if (unreadNoticeCount > 0) {
                    BadgedBox(badge = { Badge { Text(unreadNoticeCount.toString()) } }) {
                        Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.nav_news), tint = contentColor)
                    }
                } else {
                    Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.nav_news), tint = contentColor)
                }
            }
            Text(
                text = householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inventory_title),
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = onProfileClick) {
                if (photoPath != null) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = stringResource(R.string.more_profile_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.more_profile_title), tint = contentColor)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.inventory_search_placeholder),
                dense = true,
                // A white pill instead of the default outline styling, which would barely read
                // against the green gradient — same white-on-green pairing as the filter
                // button beside it and the filter chips used elsewhere in this round's headers.
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
            Box {
                FilledIconButton(
                    onClick = onFilterClick,
                    shape = SoftCardShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = SageGreenPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.inventory_tune_cd))
                }
                if (hasActiveFilter) {
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

/**
 * An icon-only circular FAB — opens the whole add-a-product menu ([AddMenuDialog]:
 * barcode/bon/AI/zoeken) rather than jumping straight to the camera. No longer an extended
 * pill with a "Scan" label; per the design review the bottom-right scan button shouldn't
 * carry any text.
 */
@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    ) {
        Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.inventory_add_menu_cd))
    }
}

/**
 * The four ways to add a product, opened by tapping "Scannen": barcode scannen and zoeken op
 * naam are free, bonnetje scannen and AI-herkenning are premium-gated (falling back to
 * [onNavigateToPremium] when not premium). A 2x2 grid of [AddMenuTile]s, same shape as before
 * the redesign.
 */
@Composable
private fun AddMenuDialog(
    isPremium: Boolean,
    onBarcodeScan: () -> Unit,
    onSearchByName: () -> Unit,
    onReceiptScan: () -> Unit,
    onAiRecognize: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.inventory_add_menu_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AddMenuTile(
                        icon = Icons.Filled.QrCodeScanner,
                        label = stringResource(R.string.inventory_add_menu_barcode),
                        onClick = { onDismiss(); onBarcodeScan() },
                        modifier = Modifier.weight(1f),
                    )
                    AddMenuTile(
                        icon = Icons.Filled.Search,
                        label = stringResource(R.string.inventory_add_menu_search),
                        onClick = { onDismiss(); onSearchByName() },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AddMenuTile(
                        icon = Icons.Filled.Receipt,
                        label = stringResource(R.string.more_beta_receipt_scan),
                        premiumLocked = !isPremium,
                        onClick = { onDismiss(); if (isPremium) onReceiptScan() else onNavigateToPremium() },
                        modifier = Modifier.weight(1f),
                    )
                    AddMenuTile(
                        icon = Icons.Filled.AutoAwesome,
                        label = stringResource(R.string.ai_recognize_title),
                        premiumLocked = !isPremium,
                        onClick = { onDismiss(); if (isPremium) onAiRecognize() else onNavigateToPremium() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/** One tile of [AddMenuDialog] — a large icon with its description underneath. */
@Composable
private fun AddMenuTile(
    icon: ImageVector,
    label: String,
    premiumLocked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(SoftCardShapeCompact)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            if (premiumLocked) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (premiumLocked) {
            Text(
                text = stringResource(R.string.more_premium_locked_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * One sheet holding everything that used to live behind four separate icon-button dropdowns
 * (sorteren/groeperen/filteren/weergave) — opened from the search field's trailing "tune"
 * button, per the Claude Design review. Scrollable: the category chip row alone can run wider
 * than useful screen height together with everything above it on a small device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryFilterSheet(
    viewMode: InventoryViewMode,
    onViewModeChange: (InventoryViewMode) -> Unit,
    sortSelected: InventorySortOption,
    onSortSelected: (InventorySortOption) -> Unit,
    groupBySelected: InventoryGroupBy,
    onGroupBySelected: (InventoryGroupBy) -> Unit,
    showGroupBy: Boolean,
    selectedCategory: Category?,
    favoritesOnly: Boolean,
    lowStockOnly: Boolean,
    expiringSoonOnly: Boolean,
    availableLocations: List<String>,
    selectedLocation: String?,
    onCategorySelected: (Category?) -> Unit,
    onFavoritesToggle: (Boolean) -> Unit,
    onLowStockToggle: (Boolean) -> Unit,
    onExpiringSoonToggle: (Boolean) -> Unit,
    onLocationSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FilterSheetSection(title = stringResource(R.string.inventory_view_mode_title)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewMode == InventoryViewMode.GRID,
                        onClick = { onViewModeChange(InventoryViewMode.GRID) },
                        label = { Text(stringResource(R.string.inventory_view_mode_grid)) },
                        leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = viewMode == InventoryViewMode.LIST,
                        onClick = { onViewModeChange(InventoryViewMode.LIST) },
                        label = { Text(stringResource(R.string.inventory_view_mode_list)) },
                        leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            FilterSheetSection(title = stringResource(R.string.inventory_sort_cd)) {
                Column {
                    InventorySortOption.entries.forEach { option ->
                        FilterSheetRow(
                            label = stringResource(option.labelRes),
                            selected = option == sortSelected,
                            onClick = { onSortSelected(option) },
                        )
                    }
                }
            }
            // Only worth offering once there's more than one location in use — with zero or
            // one, "group by location" would either be pointless (nothing to group) or
            // identical to the flat list.
            if (showGroupBy) {
                FilterSheetSection(title = stringResource(R.string.inventory_group_by_cd)) {
                    Column {
                        InventoryGroupBy.entries.forEach { option ->
                            FilterSheetRow(
                                label = stringResource(option.labelRes),
                                selected = option == groupBySelected,
                                onClick = { onGroupBySelected(option) },
                            )
                        }
                    }
                }
            }
            FilterSheetSection(title = stringResource(R.string.inventory_filter_cd)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterSheetRow(
                        label = stringResource(R.string.inventory_favorites_filter_menu_item),
                        selected = favoritesOnly,
                        onClick = { onFavoritesToggle(!favoritesOnly) },
                    )
                    FilterSheetRow(
                        label = stringResource(R.string.inventory_quick_filter_low_stock),
                        selected = lowStockOnly,
                        onClick = { onLowStockToggle(!lowStockOnly) },
                    )
                    FilterSheetRow(
                        label = stringResource(R.string.inventory_quick_filter_expiring_soon),
                        selected = expiringSoonOnly,
                        onClick = { onExpiringSoonToggle(!expiringSoonOnly) },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // The full fixed category set, in the same order a typical supermarket lays
                // out its aisles (see Category.sortOrder).
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text(stringResource(R.string.inventory_filter_all)) },
                        )
                    }
                    items(Category.entries.sortedBy { it.sortOrder }) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onCategorySelected(if (selectedCategory == category) null else category) },
                            label = { Text(stringResource(category.displayNameRes)) },
                            leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                // Only offered once at least one item has a location set — an always-visible
                // empty row would just be dead space for households not using the field yet.
                if (availableLocations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedLocation == null,
                                onClick = { onLocationSelected(null) },
                                label = { Text(stringResource(R.string.inventory_filter_all)) },
                            )
                        }
                        items(availableLocations) { location ->
                            FilterChip(
                                selected = selectedLocation == location,
                                onClick = { onLocationSelected(if (selectedLocation == location) null else location) },
                                label = { Text(locationDisplayLabel(location)) },
                                leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One titled group inside [InventoryFilterSheet] — an eyebrow-style label above its content. */
@Composable
private fun FilterSheetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/** One selectable row inside a [FilterSheetSection] — label left, checkmark right when selected. */
@Composable
private fun FilterSheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SoftCardShapeCompact)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CategoryHeader(
    category: Category,
    itemCount: Int,
    // The list view's LazyColumn has no horizontal contentPadding of its own, so this
    // header needs its own 16dp inset to land flush with InventoryRow's cards below it
    // (which get that same 16dp from their own outer padding). The grid view is the
    // opposite: its LazyVerticalGrid already applies a uniform 12dp contentPadding to
    // every item including this header, so adding another 16dp on top of that pushed
    // the header noticeably further from the edge than the tiles' own left edge below
    // it — grid callers pass 0.dp here so the header lines up flush with the tiles.
    horizontalPadding: Dp = 16.dp,
) {
    GroupHeader(
        title = stringResource(category.displayNameRes),
        itemCount = itemCount,
        icon = category.icon,
        horizontalPadding = horizontalPadding,
    )
}

/** Same shape as [CategoryHeader], for the "group by locatie" view — see InventoryGroupBy. Null
 *  [location] is the bucket of items nobody's given a location yet. */
@Composable
private fun LocationHeader(
    location: String?,
    itemCount: Int,
    horizontalPadding: Dp = 16.dp,
) {
    GroupHeader(
        title = location?.let { locationDisplayLabel(it) } ?: stringResource(R.string.inventory_no_location_label),
        itemCount = itemCount,
        icon = Icons.Filled.LocationOn,
        horizontalPadding = horizontalPadding,
    )
}

/** Maps a stored location value to its displayed label — one of [Location]'s three fixed
 *  options when it matches a [Location.storageKey], or the raw value as-is for a legacy
 *  free-text location saved before that enum existed (see its doc). */
@Composable
private fun locationDisplayLabel(raw: String): String =
    Location.fromStorageKey(raw)?.let { stringResource(it.labelRes) } ?: raw

@Composable
private fun GroupHeader(
    title: String,
    itemCount: Int,
    icon: ImageVector,
    horizontalPadding: Dp = 16.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = itemCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Short badge text for the one thing about [item] that most needs attention right now —
 * a day countdown when it has an expiration date set (mirroring [InventoryStockStatus.of]'s
 * own priority: an expiring item is more urgent than a merely low one), otherwise a short
 * label for out-of-stock/low-stock, or null for a well-stocked item with no known expiry —
 * there's nothing worth calling out, so nothing is shown, rather than a status that's always
 * present regardless of whether it says anything useful.
 */
@Composable
private fun stockStatusPillText(stockStatus: InventoryStockStatus, expirationDate: Long?): String? = when {
    expirationDate != null -> {
        val days = InventoryStockStatus.daysUntilExpiry(expirationDate)
        when {
            days < 0 -> stringResource(R.string.inventory_pill_expiry_expired)
            days == 0L -> stringResource(R.string.inventory_pill_expiry_today)
            days == 1L -> stringResource(R.string.inventory_pill_expiry_tomorrow)
            else -> pluralStringResource(R.plurals.inventory_pill_expiry_days, days.toInt(), days.toInt())
        }
    }
    stockStatus == InventoryStockStatus.OUT_OF_STOCK -> stringResource(R.string.inventory_pill_out_of_stock)
    stockStatus == InventoryStockStatus.LOW_STOCK -> stringResource(R.string.inventory_pill_low_stock)
    else -> null
}

/**
 * The single most useful secondary fact to show alongside [item]'s name — replaces the old
 * three-way "merk · eenheid · locatie" join, which gave brand equal visual weight to the
 * other two despite it already being one tap away on Productdetail. Unit and location (the
 * two facts about the physical item in front of you, rather than which brand it happens to
 * be) stay.
 */
@Composable
private fun inventoryMetaText(item: InventoryItemWithProduct): String? {
    val parts = listOfNotNull(
        item.unit?.takeIf { it.isNotBlank() },
        item.location?.let { locationDisplayLabel(it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun InventoryRow(
    item: InventoryItemWithProduct,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit,
    onAddToShoppingList: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stockStatus = InventoryStockStatus.of(item.quantity, item.minQuantity, item.expirationDate)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Selection mode repurposes a tap/long-press for picking items, so a swipe here
            // would surprise-delete the wrong thing — only live outside selection mode.
            if (value != SwipeToDismissBoxValue.Settled && !selectionMode) onDelete()
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        // Clipped to the card's own shape (see ShoppingListRow's identical fix for why) so the
        // swipe-to-delete background can never render past the rounded corners at rest.
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(SoftCardShapeCompact),
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.inventory_remove_cd),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = SoftCardShapeCompact,
        ) {
            // The stripe (height-matched to the row via IntrinsicSize.Min) is the row's own
            // status color when there's something worth flagging, transparent otherwise — a
            // steady 3dp width either way so nothing shifts when it appears/disappears while
            // scrolling. Lets you scan a long list for what needs attention without reading
            // any text, on top of (not instead of) the status text in the subtitle below.
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(if (stockStatus == InventoryStockStatus.SUFFICIENT) Color.Transparent else stockStatus.color),
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                        Icon(
                            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    } else {
                        ProductImage(
                            imageUrl = item.imageUrl,
                            fallbackIcon = Category.fromStorageKey(item.category).icon,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val statusText = stockStatusPillText(stockStatus, item.expirationDate)
                        val meta = inventoryMetaText(item)
                        if (statusText != null || meta != null) {
                            Row(modifier = Modifier.padding(top = 1.dp)) {
                                if (statusText != null) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = stockStatus.color,
                                        maxLines = 1,
                                    )
                                    if (meta != null) {
                                        Text(
                                            text = " · ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (meta != null) {
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (!selectionMode) {
                        QuantityStepper(
                            quantity = item.quantity,
                            onDecrease = onDecrease,
                            onIncrease = onIncrease,
                            dense = true,
                        )
                        // Favoriet/toevoegen-aan-lijst/verwijderen used to sit here as three
                        // always-visible icon buttons of equal weight — collapsed into one menu
                        // so the row's only always-visible action is the stepper. Verwijderen
                        // stays reachable two ways (here and via swipe, see above) since swipe
                        // isn't always discoverable.
                        var showRowMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showRowMenu = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.inventory_row_menu_cd),
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(expanded = showRowMenu, onDismissRequest = { showRowMenu = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (item.isFavorite) R.string.inventory_unmark_favorite_cd else R.string.inventory_mark_favorite_cd,
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = { showRowMenu = false; onToggleFavorite() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.inventory_add_to_shopping_list_cd)) },
                                    leadingIcon = { Icon(Icons.Filled.AddShoppingCart, contentDescription = null) },
                                    onClick = { showRowMenu = false; onAddToShoppingList() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.inventory_remove_cd)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = { showRowMenu = false; onDelete() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InventoryGridTile(
    item: InventoryItemWithProduct,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAddToShoppingList: () -> Unit,
) {
    val stockStatus = InventoryStockStatus.of(item.quantity, item.minQuantity, item.expirationDate)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SoftCardShapeCompact)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = SoftCardShapeCompact,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        // A fixed height rather than an aspectRatio — that let column count changes throw the
        // photo band's proportions off; a fixed height keeps every tile's photo band the same
        // size regardless of column count (see the grid's GridCells.Fixed(3) below).
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            ProductImage(
                imageUrl = item.imageUrl,
                fallbackIcon = Category.fromStorageKey(item.category).icon,
                shape = SoftImageShape,
                modifier = Modifier.fillMaxSize(),
            )
            // A small camera hint on the fallback (no real photo yet) — an unobtrusive nudge
            // that a product photo would help this tile stand out, without a tap target of
            // its own (ProductDetail, one tap away via onClick, is where photos are managed).
            if (item.imageUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(16.dp),
                )
            }
            // Replaces the old status dot — color and text both, right on the photo, so what
            // needs attention (or how long something's still good for) reads without a tap.
            // Top-left rather than bottom — the add-to-list cart button below now claims the
            // opposite (bottom-end) corner, so the two never compete for the same spot. Null
            // (no badge at all) for a well-stocked item with no known expiry: nothing here is
            // worth flagging, so nothing is shown, rather than always occupying the spot
            // regardless of whether it says anything useful.
            val pillText = stockStatusPillText(stockStatus, item.expirationDate)
            if (pillText != null) {
                Surface(
                    shape = SoftBadgeShape,
                    color = stockStatus.color,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                ) {
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = stockStatus.onColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            // Add-to-shopping-list lives on the photo itself now (bottom-end corner), not
            // beside the stepper below — shown only when a restock might actually be relevant
            // (low/out), same condition as before.
            val showCartBadge = stockStatus == InventoryStockStatus.LOW_STOCK || stockStatus == InventoryStockStatus.OUT_OF_STOCK
            if (!selectionMode && showCartBadge) {
                FilledIconButton(
                    onClick = onAddToShoppingList,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(26.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        Icons.Filled.AddShoppingCart,
                        contentDescription = stringResource(R.string.inventory_add_to_shopping_list_cd),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Brand dropped from here — the badge above already carries the one thing this
            // tile most needs to say, so this line goes to unit/locatie instead (see
            // [inventoryMetaText]'s doc for why brand specifically is the one that moves to
            // Productdetail rather than unit or locatie).
            //
            // Always rendered — even as an empty string when there's no unit/locatie — so
            // this line reserves the same height on every tile. Without it, tiles for items
            // missing that meta text (or, previously, any quantity) came out one text-line
            // shorter than their neighbours, throwing the whole grid row's height off.
            Text(
                text = inventoryMetaText(item) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            // A full-width pill now that the cart button has moved off this row entirely (see
            // the photo above) — − pinned left, quantity centered, + pinned right.
            QuantityStepper(
                quantity = item.quantity,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                dense = true,
                pill = true,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyInventory(isFiltered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = SoftBadgeShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isFiltered) Icons.Filled.Search else Icons.Filled.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (isFiltered) {
            Text(
                text = stringResource(R.string.inventory_empty_filtered_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(R.string.inventory_empty_filtered_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.inventory_empty_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(R.string.inventory_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
