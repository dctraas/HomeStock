package com.dtraas.boodschapbeheer.ui.inventory

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
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.boodschapbeheer.BoodschapBeheerApplication
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.local.dao.InventoryItemWithProduct
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.InventoryStockStatus
import com.dtraas.boodschapbeheer.ui.components.ProductImage
import com.dtraas.boodschapbeheer.ui.components.ProfileEditDialog
import com.dtraas.boodschapbeheer.ui.components.QuantityStepper
import com.dtraas.boodschapbeheer.ui.components.SearchField
import com.dtraas.boodschapbeheer.ui.components.color
import com.dtraas.boodschapbeheer.ui.components.icon
import com.dtraas.boodschapbeheer.ui.components.labelRes
import com.dtraas.boodschapbeheer.ui.theme.SoftBadgeShape
import com.dtraas.boodschapbeheer.ui.theme.SoftCardShapeCompact
import com.dtraas.boodschapbeheer.ui.theme.SoftImageShape
import java.io.File
import kotlinx.coroutines.launch

private enum class InventoryViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryScreen(
    onProductClick: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as BoodschapBeheerApplication
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
                CenterAlignedTopAppBar(
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
                CenterAlignedTopAppBar(
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
                    IconButton(onClick = { searchActive = true }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.inventory_search_cd),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterMenuButton(
                            selected = uiState.selectedCategory,
                            onSelected = viewModel::onCategoryFilterChange,
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
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = if (viewMode == InventoryViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                                contentDescription = if (viewMode == InventoryViewMode.LIST) {
                                    stringResource(R.string.inventory_show_as_tiles_cd)
                                } else {
                                    stringResource(R.string.inventory_show_as_list_cd)
                                },
                                modifier = Modifier.size(28.dp),
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
                    isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedCategory != null,
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
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
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
                                CategoryHeader(category, itemCount = itemsInCategory.size)
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
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(56.dp)) {
            if (isCustomSort) {
                val activeFormat = stringResource(R.string.inventory_sort_active_cd_format)
                BadgedBox(badge = { Badge() }) {
                    Icon(
                        Icons.Filled.Sort,
                        contentDescription = activeFormat.format(stringResource(selected.labelRes)),
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.Sort,
                    contentDescription = stringResource(R.string.inventory_sort_cd),
                    modifier = Modifier.size(28.dp),
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
    selected: Category?,
    onSelected: (Category?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(56.dp)) {
            if (selected != null) {
                val activeFormat = stringResource(R.string.inventory_filter_active_cd_format)
                BadgedBox(badge = { Badge() }) {
                    Icon(
                        Icons.Filled.FilterAlt,
                        contentDescription = activeFormat.format(stringResource(selected.displayNameRes)),
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Filled.FilterAlt,
                    contentDescription = stringResource(R.string.inventory_filter_cd),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.inventory_filter_all)) },
                trailingIcon = {
                    if (selected == null) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onSelected(null)
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
                        if (selected == category) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSelected(if (selected == category) null else category)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: Category, itemCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                style = MaterialTheme.typography.titleSmall,
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
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.4f)) {
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
        Column(modifier = Modifier.padding(top = 6.dp)) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QuantityStepper(
                    quantity = item.quantity,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    dense = true,
                )
                IconButton(onClick = onAddToShoppingList, modifier = Modifier.size(32.dp)) {
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
