package com.dtraas.boodschapbeheer.ui.shoppinglist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.boodschapbeheer.BoodschapBeheerApplication
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.model.MeasurementUnit
import com.dtraas.boodschapbeheer.data.model.Store
import com.dtraas.boodschapbeheer.ui.components.CategoryDropdown
import com.dtraas.boodschapbeheer.ui.components.MeasurementUnitDropdown
import com.dtraas.boodschapbeheer.ui.components.ProductImage
import com.dtraas.boodschapbeheer.ui.components.QuantityStepper
import com.dtraas.boodschapbeheer.ui.components.SearchField
import com.dtraas.boodschapbeheer.ui.components.StoreDropdown
import com.dtraas.boodschapbeheer.ui.components.formatQuantityWithUnit
import com.dtraas.boodschapbeheer.ui.components.icon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ShoppingListViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen() {
    val application = LocalContext.current.applicationContext as BoodschapBeheerApplication
    val viewModel: ShoppingListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ShoppingListViewModel(application.container.shoppingListRepository) }
        },
    )
    val groupedByStore by viewModel.groupedByStore.collectAsState()
    val searchQuery by viewModel.searchQueryState.collectAsState()
    val hasCheckedItems = groupedByStore.values.flatten().any { it.isChecked }
    var viewMode by remember { mutableStateOf(ShoppingListViewMode.LIST) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingListItemEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val removedFormat = stringResource(R.string.shopping_list_removed_format)
    val undoLabel = stringResource(R.string.common_undo)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.shopping_list_title)) },
                actions = {
                    if (hasCheckedItems) {
                        IconButton(onClick = viewModel::clearChecked) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.shopping_list_clear_checked_cd))
                        }
                    }
                    IconButton(
                        onClick = {
                            viewMode = if (viewMode == ShoppingListViewMode.LIST) {
                                ShoppingListViewMode.GRID
                            } else {
                                ShoppingListViewMode.LIST
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (viewMode == ShoppingListViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                            contentDescription = if (viewMode == ShoppingListViewMode.LIST) {
                                stringResource(R.string.inventory_show_as_tiles_cd)
                            } else {
                                stringResource(R.string.inventory_show_as_list_cd)
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_list_add_item_cd))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                placeholder = stringResource(R.string.shopping_list_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            fun deleteWithUndo(item: ShoppingListItemEntity) {
                viewModel.removeItem(item.id)
                coroutineScope.launch {
                    // showSnackbar defaults to SnackbarDuration.Indefinite whenever an
                    // actionLabel is set, so without an explicit duration the "ongedaan
                    // maken" snackbar would never auto-dismiss.
                    val result = snackbarHostState.showSnackbar(
                        message = removedFormat.format(item.name),
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreItem(item)
                    }
                }
            }

            if (groupedByStore.isEmpty()) {
                EmptyShoppingList(
                    isFiltered = searchQuery.isNotBlank(),
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (viewMode == ShoppingListViewMode.LIST) {
                ReorderableShoppingList(
                    groupedByStore = groupedByStore,
                    onCheckedChange = { item, checked -> viewModel.setChecked(item.id, checked) },
                    onItemClick = { editingItem = it },
                    onIncrease = {
                        val step = MeasurementUnit.fromStorageKey(it.unit).step
                        viewModel.setQuantity(it.id, it.quantity + step)
                    },
                    onDecrease = {
                        val step = MeasurementUnit.fromStorageKey(it.unit).step
                        viewModel.setQuantity(it.id, (it.quantity - step).coerceAtLeast(1))
                    },
                    onDelete = { deleteWithUndo(it) },
                    onMove = viewModel::moveItem,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    groupedByStore.forEach { (store, itemsInStore) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StoreHeader(store, itemCount = itemsInStore.size)
                        }
                        items(itemsInStore, key = { it.id }) { item ->
                            val step = MeasurementUnit.fromStorageKey(item.unit).step
                            ShoppingListGridTile(
                                item = item,
                                onCheckedChange = { checked -> viewModel.setChecked(item.id, checked) },
                                onClick = { editingItem = item },
                                onIncrease = { viewModel.setQuantity(item.id, item.quantity + step) },
                                onDecrease = { viewModel.setQuantity(item.id, (item.quantity - step).coerceAtLeast(1)) },
                                onDelete = { deleteWithUndo(item) },
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            ItemFormDialog(
                title = stringResource(R.string.shopping_list_item_add_title),
                confirmLabel = stringResource(R.string.shopping_list_add_confirm),
                onDismiss = { showAddDialog = false },
                onConfirm = { name, category, store, quantity, note, unit ->
                    viewModel.addItem(name, category, store, quantity, note.trim().ifBlank { null }, unit)
                    showAddDialog = false
                },
            )
        }

        editingItem?.let { item ->
            ItemFormDialog(
                title = stringResource(R.string.shopping_list_item_edit_title),
                confirmLabel = stringResource(R.string.shopping_list_save_confirm),
                initialName = item.name,
                initialCategory = Category.fromStorageKey(item.category),
                initialStore = Store.fromStorageKey(item.store),
                initialQuantity = item.quantity,
                initialNote = item.note ?: "",
                initialUnit = MeasurementUnit.fromStorageKey(item.unit),
                imageUrl = item.imageUrl,
                onDismiss = { editingItem = null },
                onConfirm = { name, category, store, quantity, note, unit ->
                    viewModel.updateItem(
                        item.copy(
                            name = name,
                            category = category.storageKey,
                            store = store.storageKey,
                            quantity = quantity,
                            note = note.trim().ifBlank { null },
                            unit = unit.storageKey,
                        )
                    )
                    editingItem = null
                },
            )
        }
    }
}

/**
 * Renders the list view with drag-to-reorder support. The displayed order is kept in a
 * local [orderedItems] list that mirrors [groupedByStore] flattened; it's only re-synced
 * from Firestore while nothing is being dragged, so a snapshot arriving mid-gesture (e.g.
 * a housemate's edit on another device) can't yank an item out from under the user's
 * finger — the same class of race this app already avoids for other live-edited fields.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableShoppingList(
    groupedByStore: Map<Store, List<ShoppingListItemEntity>>,
    onCheckedChange: (ShoppingListItemEntity, Boolean) -> Unit,
    onItemClick: (ShoppingListItemEntity) -> Unit,
    onIncrease: (ShoppingListItemEntity) -> Unit,
    onDecrease: (ShoppingListItemEntity) -> Unit,
    onDelete: (ShoppingListItemEntity) -> Unit,
    onMove: (item: ShoppingListItemEntity, previous: ShoppingListItemEntity?, next: ShoppingListItemEntity?) -> Unit,
) {
    val flattened = remember(groupedByStore) { groupedByStore.values.flatten() }
    val orderedItems = remember { mutableStateListOf<ShoppingListItemEntity>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var draggingRowHeightPx by remember { mutableFloatStateOf(0f) }

    if (draggingId == null) {
        // Not mid-gesture: reconcile with the latest Firestore data. If it's still the same
        // set of items (e.g. the snapshot that echoes back the drag we just committed, or an
        // unrelated field edited on another device), update values in place but keep our
        // local order — otherwise the moment-old snapshot arriving right after a drop would
        // briefly show the pre-drag order before the new one catches up. Only fall back to
        // the server order outright when items were actually added or removed.
        LaunchedEffect(flattened) {
            val flattenedById = flattened.associateBy { it.id }
            if (flattenedById.keys == orderedItems.map { it.id }.toSet()) {
                for (i in orderedItems.indices) {
                    val updated = flattenedById.getValue(orderedItems[i].id)
                    if (updated != orderedItems[i]) orderedItems[i] = updated
                }
            } else {
                orderedItems.clear()
                orderedItems.addAll(flattened)
            }
        }
    }

    // Two items can only be swapped past each other while dragging if they're in the same
    // store and the same checked/unchecked group — isChecked is the primary sort key, so
    // moving across that boundary wouldn't visually do anything but would silently corrupt
    // the manual order.
    fun canSwap(a: ShoppingListItemEntity, b: ShoppingListItemEntity) =
        a.store == b.store && a.isChecked == b.isChecked

    fun handleDrag(deltaY: Float) {
        val id = draggingId ?: return
        dragOffsetPx += deltaY
        val rowHeight = draggingRowHeightPx.takeIf { it > 0f } ?: return
        while (true) {
            val index = orderedItems.indexOfFirst { it.id == id }
            if (index < 0) break
            if (dragOffsetPx > rowHeight / 2f && index < orderedItems.lastIndex &&
                canSwap(orderedItems[index], orderedItems[index + 1])
            ) {
                orderedItems.add(index, orderedItems.removeAt(index + 1))
                dragOffsetPx -= rowHeight
            } else if (dragOffsetPx < -rowHeight / 2f && index > 0 &&
                canSwap(orderedItems[index], orderedItems[index - 1])
            ) {
                orderedItems.add(index - 1, orderedItems.removeAt(index))
                dragOffsetPx += rowHeight
            } else {
                break
            }
        }
    }

    fun commitDrag() {
        val id = draggingId
        val index = if (id != null) orderedItems.indexOfFirst { it.id == id } else -1
        if (index >= 0) {
            val item = orderedItems[index]
            val previous = orderedItems.getOrNull(index - 1)?.takeIf { canSwap(item, it) }
            val next = orderedItems.getOrNull(index + 1)?.takeIf { canSwap(item, it) }
            if (previous != null || next != null) {
                onMove(item, previous, next)
            }
        }
        draggingId = null
        dragOffsetPx = 0f
        draggingRowHeightPx = 0f
    }

    // Items of the same store are already contiguous in orderedItems (they come from
    // groupedByStore, and a drag only ever swaps items within their own store), so
    // grouping consecutive runs here always yields exactly one run per store.
    val storeRuns = remember(orderedItems.toList()) {
        val runs = mutableListOf<Pair<Store, MutableList<ShoppingListItemEntity>>>()
        for (item in orderedItems) {
            val store = Store.fromStorageKey(item.store)
            val lastRun = runs.lastOrNull()
            if (lastRun != null && lastRun.first == store) {
                lastRun.second.add(item)
            } else {
                runs.add(store to mutableListOf(item))
            }
        }
        runs
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        storeRuns.forEach { (store, groupItems) ->
            stickyHeader(key = "header_${store.storageKey}") {
                StoreHeader(store, itemCount = groupItems.size)
            }
            items(groupItems, key = { it.id }) { item ->
                val isDragging = item.id == draggingId
                ShoppingListRow(
                    item = item,
                    onCheckedChange = { checked -> onCheckedChange(item, checked) },
                    onClick = { onItemClick(item) },
                    onIncrease = { onIncrease(item) },
                    onDecrease = { onDecrease(item) },
                    onDelete = { onDelete(item) },
                    onDragStart = { rowHeightPx ->
                        draggingId = item.id
                        dragOffsetPx = 0f
                        draggingRowHeightPx = rowHeightPx
                    },
                    onDrag = ::handleDrag,
                    onDragEnd = ::commitDrag,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .then(
                            if (isDragging) {
                                Modifier.offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                            } else {
                                Modifier.animateItem()
                            }
                        ),
                )
            }
        }
    }
}

@Composable
private fun StoreHeader(store: Store, itemCount: Int) {
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
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(store.displayNameRes),
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
private fun ShoppingListRow(
    item: ShoppingListItemEntity,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (rowHeightPx: Float) -> Unit,
    onDrag: (deltaYPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = Category.fromStorageKey(item.category)
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val dragHandleCd = stringResource(R.string.shopping_list_drag_handle_cd)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .onGloballyPositioned { rowHeightPx = it.size.height.toFloat() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 0.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = dragHandleCd,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart(rowHeightPx) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        )
                    },
            )
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(32.dp),
            )
            ProductImage(
                imageUrl = item.imageUrl,
                fallbackIcon = category.icon,
                shape = RoundedCornerShape(8.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(stringResource(category.displayNameRes), item.note).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            QuantityStepper(
                quantity = item.quantity,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                minQuantity = 1,
                dense = true,
                displayText = formatQuantityWithUnit(item.quantity, MeasurementUnit.fromStorageKey(item.unit)),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.shopping_list_delete_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ShoppingListGridTile(
    item: ShoppingListItemEntity,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = Category.fromStorageKey(item.category)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            ProductImage(
                imageUrl = item.imageUrl,
                fallbackIcon = category.icon,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(36.dp),
            ) {
                IconButton(
                    onClick = { onCheckedChange(!item.isChecked) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = if (item.isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (item.isChecked) {
                            stringResource(R.string.shopping_list_mark_unchecked_cd)
                        } else {
                            stringResource(R.string.shopping_list_mark_checked_cd)
                        },
                        tint = if (item.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(36.dp),
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.shopping_list_delete_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(stringResource(category.displayNameRes), item.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            QuantityStepper(
                quantity = item.quantity,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                minQuantity = 1,
                modifier = Modifier.padding(top = 4.dp),
                displayText = formatQuantityWithUnit(item.quantity, MeasurementUnit.fromStorageKey(item.unit)),
            )
        }
    }
}

@Composable
private fun EmptyShoppingList(isFiltered: Boolean, modifier: Modifier = Modifier) {
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
                    imageVector = if (isFiltered) Icons.Filled.Search else Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = if (isFiltered) {
                stringResource(R.string.shopping_list_empty_filtered)
            } else {
                stringResource(R.string.shopping_list_empty)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun ItemFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialCategory: Category = Category.OVERIG,
    initialStore: Store = Store.GEEN,
    initialQuantity: Int = 1,
    initialNote: String = "",
    initialUnit: MeasurementUnit = MeasurementUnit.STUKS,
    imageUrl: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: Category, store: Store, quantity: Int, note: String, unit: MeasurementUnit) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var store by remember { mutableStateOf(initialStore) }
    var quantity by remember { mutableIntStateOf(initialQuantity) }
    var note by remember { mutableStateOf(initialNote) }
    var unit by remember { mutableStateOf(initialUnit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ItemFormAvatar(imageUrl = imageUrl, category = category)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.common_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CategoryDropdown(
                        selected = category,
                        onSelected = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StoreDropdown(
                        selected = store,
                        onSelected = { store = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MeasurementUnitDropdown(
                        selected = unit,
                        onSelected = { unit = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.common_quantity), style = MaterialTheme.typography.bodyLarge)
                        QuantityStepper(
                            quantity = quantity,
                            onDecrease = { quantity = (quantity - unit.step).coerceAtLeast(1) },
                            onIncrease = { quantity += unit.step },
                            minQuantity = 1,
                            displayText = formatQuantityWithUnit(quantity, unit),
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.shopping_list_note_label)) },
                        placeholder = { Text(stringResource(R.string.shopping_list_note_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, category, store, quantity, note, unit) },
                enabled = name.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun ItemFormAvatar(imageUrl: String?, category: Category) {
    ProductImage(
        imageUrl = imageUrl,
        fallbackIcon = category.icon,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.size(88.dp),
    )
}
