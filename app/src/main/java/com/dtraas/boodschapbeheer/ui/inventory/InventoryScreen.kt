package com.dtraas.boodschapbeheer.ui.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var viewMode by remember { mutableStateOf(InventoryViewMode.GRID) }
    var searchActive by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    val deviceProfile = application.container.deviceProfile
    val displayName by deviceProfile.displayName.collectAsState()
    val photoPath by deviceProfile.photoPath.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val removedFormat = stringResource(R.string.inventory_removed_snackbar_format)
    val undoLabel = stringResource(R.string.common_undo)
    val addedToShoppingListMessage = stringResource(R.string.inventory_added_to_shopping_list_snackbar)

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
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
                    uiState.groupedInventory.forEach { (category, itemsInCategory) ->
                        stickyHeader {
                            CategoryHeader(category, itemCount = itemsInCategory.size)
                        }
                        items(itemsInCategory, key = { it.barcode }) { item ->
                            InventoryRow(
                                item = item,
                                onClick = { onProductClick(item.barcode) },
                                onIncrease = { viewModel.setQuantity(item.barcode, item.quantity + 1) },
                                onDecrease = { viewModel.setQuantity(item.barcode, item.quantity - 1) },
                                onDelete = { deleteWithUndo(item) },
                                onAddToShoppingList = { addToShoppingListWithFeedback(item) },
                            )
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
                    uiState.groupedInventory.forEach { (category, itemsInCategory) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CategoryHeader(category, itemCount = itemsInCategory.size)
                        }
                        items(itemsInCategory, key = { it.barcode }) { item ->
                            InventoryGridTile(
                                item = item,
                                onClick = { onProductClick(item.barcode) },
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

    if (showProfileDialog) {
        ProfileEditDialog(
            displayName = displayName,
            photoPath = photoPath,
            onSaveName = { deviceProfile.setDisplayName(it) },
            onPhotoPicked = { uri -> coroutineScope.launch { deviceProfile.setPhotoFromUri(uri) } },
            onRemovePhoto = { coroutineScope.launch { deviceProfile.clearPhoto() } },
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
            Category.entries.sortedBy { it.sortOrder }.forEach { category ->
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

@Composable
private fun InventoryRow(
    item: InventoryItemWithProduct,
    onClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit,
    onAddToShoppingList: () -> Unit,
) {
    val stockStatus = InventoryStockStatus.of(item.quantity, item.minQuantity, item.expirationDate)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(32.dp)) {
                ProductImage(
                    imageUrl = item.imageUrl,
                    fallbackIcon = Category.fromStorageKey(item.category).icon,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxSize(),
                )
                StockStatusDot(status = stockStatus, modifier = Modifier.align(Alignment.BottomEnd))
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

@Composable
private fun InventoryGridTile(
    item: InventoryItemWithProduct,
    onClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onAddToShoppingList: () -> Unit,
) {
    val stockStatus = InventoryStockStatus.of(item.quantity, item.minQuantity, item.expirationDate)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            ProductImage(
                imageUrl = item.imageUrl,
                fallbackIcon = Category.fromStorageKey(item.category).icon,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxSize(),
            )
            StockStatusDot(
                status = stockStatus,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp),
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
            QuantityStepper(
                quantity = item.quantity,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                dense = true,
                modifier = Modifier.padding(top = 2.dp),
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
            shape = CircleShape,
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
