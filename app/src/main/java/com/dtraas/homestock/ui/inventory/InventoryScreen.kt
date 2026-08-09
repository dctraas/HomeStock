package com.dtraas.homestock.ui.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.ProductImage
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.components.color
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.components.labelRes
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import java.io.File
import kotlinx.coroutines.launch

private enum class InventoryViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryScreen(
    onProductClick: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
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
    var searchActive by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var selectedBarcodes by remember { mutableStateOf(emptySet<String>()) }
    val selectionMode = selectedBarcodes.isNotEmpty()
    val deviceProfile = application.container.deviceProfile
    val displayName by deviceProfile.displayName.collectAsState()
    val photoPath by deviceProfile.photoPath.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val removedFormat = stringResource(R.string.inventory_removed_snackbar_format)
    val undoLabel = stringResource(R.string.common_undo)
    val addedToShoppingListMessage = stringResource(R.string.inventory_added_to_shopping_list_snackbar)
    val bulkAddedFormat = stringResource(R.string.inventory_bulk_added_to_shopping_list_format)
    val restockedFormat = stringResource(R.string.inventory_restocked_snackbar_format)

    LaunchedEffect(Unit) {
        viewModel.restockEvents.collect { name ->
            snackbarHostState.showSnackbar(restockedFormat.format(name), duration = SnackbarDuration.Short)
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
        topBar = {
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
                HomeStockTopAppBar(
                    title = {
                        Text(
                            text = uiState.householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inventory_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        // R.mipmap.ic_launcher is an <adaptive-icon> XML (background + foreground
                        // layers); painterResource only supports plain VectorDrawable/raster assets
                        // and crashes on it, so the two vector layers are composited by hand instead.
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(32.dp)
                                .clip(CircleShape),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_background),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showProfileDialog = true }) {
                            if (photoPath != null) {
                                AsyncImage(
                                    model = File(photoPath),
                                    contentDescription = stringResource(R.string.more_profile_title),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.more_profile_title))
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    SearchField(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        placeholder = stringResource(R.string.inventory_search_placeholder),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            searchActive = false
                            viewModel.onSearchQueryChange("")
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.inventory_search_close_cd),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    IconButton(onClick = { searchActive = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.inventory_search_cd),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    // Icons here were 56dp each with 28dp glyphs — comfortably tap-able but
                    // visually heavy for a row that's just filters/view options, not primary
                    // actions. Shrinking to a standard 48dp touch target tightens the gaps
                    // between them without making them harder to tap.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Favorieten used to be its own star icon here; it's now one of the
                        // choices inside the filter dropdown instead (alongside category),
                        // which both saves a slot in this row and keeps every way of
                        // narrowing the list in one place.
                        FilterMenuButton(
                            selectedCategory = uiState.selectedCategory,
                            favoritesOnly = uiState.favoritesOnly,
                            onCategorySelected = viewModel::onCategoryFilterChange,
                            onFavoritesToggle = viewModel::onFavoritesFilterChange,
                        )
                        SortMenuButton(
                            selected = uiState.sortOption,
                            onSelected = viewModel::onSortOptionChange,
                        )
                        IconButton(
                            onClick = {
                                viewMode = if (viewMode == InventoryViewMode.LIST) {
                                    InventoryViewMode.GRID
                                } else {
                                    InventoryViewMode.LIST
                                }
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (viewMode == InventoryViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                                contentDescription = if (viewMode == InventoryViewMode.LIST) {
                                    stringResource(R.string.inventory_show_as_tiles_cd)
                                } else {
                                    stringResource(R.string.inventory_show_as_list_cd)
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            // Grouping by category loses the order between categories (each still shows in
            // category order, not by whichever item within it sorts first) — for Houdbaarheid,
            // where seeing what's soonest across the whole voorraad is the point, render one
            // flat list instead of grouping by category at all.
            val isFlatSort = uiState.sortOption == InventorySortOption.EXPIRATION

            if (uiState.groupedInventory.isEmpty()) {
                EmptyInventory(
                    isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedCategory != null || uiState.favoritesOnly,
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
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
}

@Composable
private fun SortMenuButton(
    selected: InventorySortOption,
    onSelected: (InventorySortOption) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isCustomSort = selected != InventorySortOption.NAME
    Box {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
            if (isCustomSort) {
                val activeFormat = stringResource(R.string.inventory_sort_active_cd_format)
                BadgedBox(badge = { Badge() }) {
                    Icon(
                        Icons.Filled.Sort,
                        contentDescription = activeFormat.format(stringResource(selected.labelRes)),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.Sort,
                    contentDescription = stringResource(R.string.inventory_sort_cd),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            InventorySortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSelected(option)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterMenuButton(
    selectedCategory: Category?,
    favoritesOnly: Boolean,
    onCategorySelected: (Category?) -> Unit,
    onFavoritesToggle: (Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isActive = selectedCategory != null || favoritesOnly
    Box {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
            if (isActive) {
                // Both filter dimensions can be active together (e.g. "Favorieten" within
                // "Groente & fruit"), so the badge's description lists whichever are on
                // rather than assuming just one.
                val activeLabels = listOfNotNull(
                    stringResource(R.string.inventory_favorites_filter_menu_item).takeIf { favoritesOnly },
                    selectedCategory?.let { stringResource(it.displayNameRes) },
                ).joinToString(", ")
                val activeFormat = stringResource(R.string.inventory_filter_active_cd_format)
                BadgedBox(badge = { Badge() }) {
                    Icon(
                        Icons.Filled.FilterAlt,
                        contentDescription = activeFormat.format(activeLabels),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.FilterAlt,
                    contentDescription = stringResource(R.string.inventory_filter_cd),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.inventory_filter_all)) },
                trailingIcon = {
                    if (selectedCategory == null && !favoritesOnly) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onCategorySelected(null)
                    onFavoritesToggle(false)
                    menuExpanded = false
                },
            )
            // Favorieten used to be its own star icon next to this menu; folding it in here
            // instead means every way of narrowing the list lives in one place. It's
            // independent of the category choice below — both can be active at once.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.inventory_favorites_filter_menu_item)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = if (favoritesOnly) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (favoritesOnly) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onFavoritesToggle(!favoritesOnly)
                    menuExpanded = false
                },
            )
            // The full fixed category set, in the same order a typical supermarket lays out
            // its aisles (see Category.sortOrder) — every category a product can have is
            // also offered as a filter.
            val filterableCategories = Category.entries.sortedBy { it.sortOrder }
            filterableCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.displayNameRes)) },
                    leadingIcon = {
                        Icon(category.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (selectedCategory == category) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onCategorySelected(if (selectedCategory == category) null else category)
                        menuExpanded = false
                    },
                )
            }
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
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(category.displayNameRes),
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(SoftCardShapeCompact)
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
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Box(modifier = Modifier.size(32.dp)) {
                        ProductImage(
                            imageUrl = item.imageUrl,
                            fallbackIcon = Category.fromStorageKey(item.category).icon,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        )
                        StockStatusDot(status = stockStatus, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(item.brand, item.unit).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
                if (!selectionMode) {
                    QuantityStepper(
                        quantity = item.quantity,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease,
                        dense = true,
                    )
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(
                                if (item.isFavorite) R.string.inventory_unmark_favorite_cd else R.string.inventory_mark_favorite_cd,
                            ),
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onAddToShoppingList, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.AddShoppingCart,
                            contentDescription = stringResource(R.string.inventory_add_to_shopping_list_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.inventory_remove_cd),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
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
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.1f)) {
            ProductImage(
                imageUrl = item.imageUrl,
                fallbackIcon = Category.fromStorageKey(item.category).icon,
                shape = SoftImageShape,
                modifier = Modifier.fillMaxSize(),
            )
            StockStatusDot(
                status = stockStatus,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            // TopEnd hosts either the selection checkmark or the add-to-shopping-list badge —
            // never both, since they're mutually exclusive modes. The badge mirrors the
            // favorite badge on ProductDetailScreen's hero image (same Surface-circle pattern)
            // now that it's the only quick action left here — freed up by dropping the
            // favorite star from this tile and the stepper's own row from cramming a second
            // icon button next to it.
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
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp),
                ) {
                    IconButton(onClick = onAddToShoppingList, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.AddShoppingCart,
                            contentDescription = stringResource(R.string.inventory_add_to_shopping_list_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(item.brand, item.unit).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            // Just the stepper now — dropping the favorite star and moving add-to-shopping-list
            // up onto the image (see above) means this no longer needs to share its row with
            // any other controls, so minus/count/plus finally sit cleanly on one line.
            QuantityStepper(
                quantity = item.quantity,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                dense = true,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Small colored dot (🟢/🟡/🟠/🔴-style) showing an item's stock status at a glance. */
@Composable
private fun StockStatusDot(status: InventoryStockStatus, modifier: Modifier = Modifier) {
    val label = stringResource(status.labelRes)
    Box(
        modifier = modifier
            .size(14.dp)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(status.color),
    )
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
