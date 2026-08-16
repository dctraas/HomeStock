package com.dtraas.homestock.ui.shoppinglist

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.scale
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
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.ShoppingListItemEntity
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.CategoryDropdown
import com.dtraas.homestock.ui.components.MeasurementUnitDropdown
import com.dtraas.homestock.ui.components.ProductImage
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.components.StoreDropdown
import com.dtraas.homestock.ui.components.formatQuantityWithUnit
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ShoppingListViewMode { LIST, GRID }

/**
 * Plain-text shopping list for the system share sheet — grouped by store like the on-screen
 * list, only the still-unchecked items (sharing is "here's what to pick up", not a dump of
 * everything including what's already bought). [unitLabels] is the same storage-key ->
 * localized-label lookup CSV export uses, built once via `stringResource` in composition since
 * this function itself runs from a plain onClick lambda outside of it.
 */
private fun buildShoppingListShareText(
    groupedByStore: Map<String, List<ShoppingListItemEntity>>,
    title: String,
    noStoreLabel: String,
    unitLabels: Map<String, String>,
): String {
    val builder = StringBuilder(title)
    groupedByStore.forEach { (storeName, items) ->
        val unchecked = items.filterNot { it.isChecked }
        if (unchecked.isEmpty()) return@forEach
        builder.append("\n\n").append(storeName.ifBlank { noStoreLabel })
        unchecked.forEach { item ->
            val unit = MeasurementUnit.fromStorageKey(item.unit)
            val label = unitLabels[unit.storageKey] ?: unit.storageKey
            val quantityText = if (unit.spaceBeforeLabel) "${item.quantity} $label" else "${item.quantity}$label"
            builder.append("\n- ").append(item.name).append(" (").append(quantityText).append(')')
        }
    }
    return builder.toString()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val viewModel: ShoppingListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ShoppingListViewModel(application.container.shoppingListRepository, application.container.storeRepository)
            }
        },
    )
    val groupedByStore by viewModel.groupedByStore.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val searchQuery by viewModel.searchQueryState.collectAsState()
    val sortMode by viewModel.sortModeState.collectAsState()
    val hasCheckedItems = groupedByStore.values.flatten().any { it.isChecked }
    val hasUncheckedItems = groupedByStore.values.flatten().any { !it.isChecked }
    var viewMode by remember { mutableStateOf(ShoppingListViewMode.LIST) }
    var searchActive by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingListItemEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val removedFormat = stringResource(R.string.shopping_list_removed_format)
    val undoLabel = stringResource(R.string.common_undo)
    val voiceUnavailableMessage = stringResource(R.string.shopping_list_voice_input_unavailable)
    val onVoiceInputUnavailable: () -> Unit = {
        coroutineScope.launch { snackbarHostState.showSnackbar(voiceUnavailableMessage, duration = SnackbarDuration.Short) }
    }

    // Resolved once via stringResource (composable-only) rather than inside the plain onClick
    // lambda below, which runs outside composition — same pattern as MoreScreen's CSV export.
    val shareTitle = stringResource(R.string.shopping_list_share_title)
    val noStoreLabel = stringResource(R.string.store_geen)
    val unitLabels = MeasurementUnit.entries.associate { it.storageKey to stringResource(it.shortLabelRes) }

    fun shareList() {
        // Only the still-to-buy items — sharing is "here's what to pick up", not a full
        // export of everything that happens to be checked off already.
        val text = buildShoppingListShareText(groupedByStore, shareTitle, noStoreLabel, unitLabels)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(sendIntent, shareTitle))
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(title = { Text(stringResource(R.string.shopping_list_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_list_add_item_cd))
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
                        query = searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        placeholder = stringResource(R.string.shopping_list_search_placeholder),
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
                        if (groupedByStore.isNotEmpty()) {
                            IconButton(onClick = ::shareList, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = stringResource(R.string.shopping_list_share_cd),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        if (hasUncheckedItems) {
                            IconButton(onClick = viewModel::checkAll, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    Icons.Filled.DoneAll,
                                    contentDescription = stringResource(R.string.shopping_list_check_all_cd),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        if (hasCheckedItems) {
                            IconButton(onClick = viewModel::clearChecked, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = stringResource(R.string.shopping_list_clear_checked_cd),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        if (groupedByStore.isNotEmpty()) {
                            ShoppingListSortMenuButton(
                                selected = sortMode,
                                onSelected = viewModel::onSortModeChange,
                            )
                        }
                        IconButton(
                            onClick = {
                                viewMode = if (viewMode == ShoppingListViewMode.LIST) {
                                    ShoppingListViewMode.GRID
                                } else {
                                    ShoppingListViewMode.LIST
                                }
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = if (viewMode == ShoppingListViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                                contentDescription = if (viewMode == ShoppingListViewMode.LIST) {
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
            } else if (viewMode == ShoppingListViewMode.LIST && sortMode == ShoppingListSortMode.MANUAL) {
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
                    onStoreChange = { item, newStore -> viewModel.setStore(item.id, newStore) },
                )
            } else if (viewMode == ShoppingListViewMode.LIST) {
                // Winkelindeling: a fixed, generated order (see ShoppingListViewModel) rather
                // than something the household drags around — so this renders the same rows
                // without ReorderableShoppingList's drag machinery, same as the grid view below.
                AisleOrderedShoppingList(
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
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groupedByStore.forEach { (storeName, itemsInStore) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StoreHeader(storeName, itemCount = itemsInStore.size)
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
                stores = stores,
                onAddStore = viewModel::addStore,
                onDismiss = { showAddDialog = false },
                onVoiceInputUnavailable = onVoiceInputUnavailable,
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
                initialStore = item.store,
                initialQuantity = item.quantity,
                initialNote = item.note ?: "",
                initialUnit = MeasurementUnit.fromStorageKey(item.unit),
                imageUrl = item.imageUrl,
                stores = stores,
                onAddStore = viewModel::addStore,
                onDismiss = { editingItem = null },
                onVoiceInputUnavailable = onVoiceInputUnavailable,
                onConfirm = { name, category, store, quantity, note, unit ->
                    viewModel.updateItem(
                        item.copy(
                            name = name,
                            category = category.storageKey,
                            store = store,
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

/** Handmatige volgorde (drag-to-reorder) vs. Winkelindeling (supermarket-aisle order) —
 *  see [ShoppingListSortMode]. Mirrors InventoryScreen's SortMenuButton shape. */
@Composable
private fun ShoppingListSortMenuButton(
    selected: ShoppingListSortMode,
    onSelected: (ShoppingListSortMode) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isCustomSort = selected != ShoppingListSortMode.MANUAL
    Box {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(56.dp)) {
            if (isCustomSort) {
                val activeFormat = stringResource(R.string.shopping_list_sort_active_cd_format)
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
                    contentDescription = stringResource(R.string.shopping_list_sort_cd),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            ShoppingListSortMode.entries.forEach { option ->
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

/**
 * Renders the list view with drag-to-reorder support. The displayed order is kept in a
 * local [orderedItems] list that mirrors [groupedByStore] flattened; it's only re-synced
 * from Firestore while nothing is being dragged, so a snapshot arriving mid-gesture (e.g.
 * a housemate's edit on another device) can't yank an item out from under the user's
 * finger — the same class of race this app already avoids for other live-edited fields.
 *
 * Dragging an item past the end of its own store's group and into a neighboring store's
 * group reassigns it to that store (see [onStoreChange]) — a quick alternative to opening
 * the edit dialog just to change its store dropdown for a plain "move this to my other
 * list" gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableShoppingList(
    groupedByStore: Map<String, List<ShoppingListItemEntity>>,
    onCheckedChange: (ShoppingListItemEntity, Boolean) -> Unit,
    onItemClick: (ShoppingListItemEntity) -> Unit,
    onIncrease: (ShoppingListItemEntity) -> Unit,
    onDecrease: (ShoppingListItemEntity) -> Unit,
    onDelete: (ShoppingListItemEntity) -> Unit,
    onMove: (item: ShoppingListItemEntity, previous: ShoppingListItemEntity?, next: ShoppingListItemEntity?) -> Unit,
    onStoreChange: (item: ShoppingListItemEntity, newStore: String) -> Unit,
) {
    val flattened = remember(groupedByStore) { groupedByStore.values.flatten() }
    val orderedItems = remember { mutableStateListOf<ShoppingListItemEntity>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var draggingRowHeightPx by remember { mutableFloatStateOf(0f) }
    // The store this item belonged to when the drag started — compared against its
    // (possibly reassigned, see handleDrag) store at drop time so onStoreChange only
    // fires when a store boundary was actually crossed.
    var draggingOriginalStore by remember { mutableStateOf<String?>(null) }

    if (draggingId == null) {
        // Not mid-gesture: reconcile with the latest Firestore data. If it's still the same
        // set of items (e.g. the snapshot that echoes back the drag we just committed, or an
        // unrelated field edited on another device), update values in place but keep our
        // local order — otherwise the moment-old snapshot arriving right after a drop would
        // briefly show the pre-drag order before the new one catches up. Only fall back to
        // the server order outright when items were actually added or removed, or when
        // someone's store changed: storeRuns below assumes same-store items are always
        // contiguous, which an in-place field patch can't guarantee (a store reassigned via
        // the icon/dialog — as opposed to a drag, which already keeps runs contiguous itself
        // — leaves the item at its old position, splitting its new store into two runs and
        // crashing on a duplicate stickyHeader key). Rebuilding from [flattened], which is
        // grouped by store, restores that invariant.
        LaunchedEffect(flattened) {
            val flattenedById = flattened.associateBy { it.id }
            val sameIds = flattenedById.keys == orderedItems.map { it.id }.toSet()
            val storeChanged = sameIds && orderedItems.any { flattenedById.getValue(it.id).store != it.store }
            if (sameIds && !storeChanged) {
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
    // checked/unchecked group — isChecked is the primary sort key, so moving across that
    // boundary wouldn't visually do anything but would silently corrupt the manual order.
    // Store is deliberately not part of this gate: dragging an item past the last item of
    // its own store's group and into a neighboring store's group is how a store reassignment
    // happens (see handleDrag), so the two runs need to be swappable across that boundary.
    fun canSwap(a: ShoppingListItemEntity, b: ShoppingListItemEntity) =
        a.isChecked == b.isChecked

    fun handleDrag(deltaY: Float) {
        val id = draggingId ?: return
        dragOffsetPx += deltaY
        val rowHeight = draggingRowHeightPx.takeIf { it > 0f } ?: return
        while (true) {
            val index = orderedItems.indexOfFirst { it.id == id }
            if (index < 0) break
            val current = orderedItems[index]
            if (dragOffsetPx > rowHeight / 2f && index < orderedItems.lastIndex &&
                canSwap(current, orderedItems[index + 1])
            ) {
                val neighbor = orderedItems[index + 1]
                // Crossing into a neighboring store's run: adopt its store right as the
                // dragged item passes it, so it visually merges into that group immediately
                // instead of only updating once the drag ends.
                if (neighbor.store != current.store) orderedItems[index] = current.copy(store = neighbor.store)
                orderedItems.add(index, orderedItems.removeAt(index + 1))
                dragOffsetPx -= rowHeight
            } else if (dragOffsetPx < -rowHeight / 2f && index > 0 &&
                canSwap(current, orderedItems[index - 1])
            ) {
                val neighbor = orderedItems[index - 1]
                if (neighbor.store != current.store) orderedItems[index] = current.copy(store = neighbor.store)
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
            if (item.store != draggingOriginalStore) {
                onStoreChange(item, item.store)
            }
            if (previous != null || next != null) {
                onMove(item, previous, next)
            }
        }
        draggingId = null
        dragOffsetPx = 0f
        draggingRowHeightPx = 0f
        draggingOriginalStore = null
    }

    // Items of the same store are always contiguous in orderedItems — they start out that
    // way (from groupedByStore), and a drag either swaps within a run or, at a run's edge,
    // relabels the dragged item's store to merge it into the neighboring run (see
    // handleDrag) — so grouping consecutive runs here always yields exactly one run per store.
    val storeRuns = remember(orderedItems.toList()) {
        val runs = mutableListOf<Pair<String, MutableList<ShoppingListItemEntity>>>()
        for (item in orderedItems) {
            val lastRun = runs.lastOrNull()
            if (lastRun != null && lastRun.first == item.store) {
                lastRun.second.add(item)
            } else {
                runs.add(item.store to mutableListOf(item))
            }
        }
        runs
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        storeRuns.forEach { (storeName, groupItems) ->
            stickyHeader(key = "header_$storeName") {
                StoreHeader(storeName, itemCount = groupItems.size)
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
                        draggingOriginalStore = item.store
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

/**
 * Plain, non-draggable counterpart to [ReorderableShoppingList] — used when
 * [ShoppingListSortMode.AISLE] is active, since that order is generated from each item's
 * category rather than something the household drags around by hand (see
 * ShoppingListViewModel.groupedByStore). [ShoppingListRow] still requires drag callbacks, so
 * no-ops are passed through rather than reworking it to make them optional just for this.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AisleOrderedShoppingList(
    groupedByStore: Map<String, List<ShoppingListItemEntity>>,
    onCheckedChange: (ShoppingListItemEntity, Boolean) -> Unit,
    onItemClick: (ShoppingListItemEntity) -> Unit,
    onIncrease: (ShoppingListItemEntity) -> Unit,
    onDecrease: (ShoppingListItemEntity) -> Unit,
    onDelete: (ShoppingListItemEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        groupedByStore.forEach { (storeName, itemsInStore) ->
            stickyHeader(key = "header_$storeName") {
                StoreHeader(storeName, itemCount = itemsInStore.size)
            }
            items(itemsInStore, key = { it.id }) { item ->
                ShoppingListRow(
                    item = item,
                    onCheckedChange = { checked -> onCheckedChange(item, checked) },
                    onClick = { onItemClick(item) },
                    onIncrease = { onIncrease(item) },
                    onDecrease = { onDecrease(item) },
                    onDelete = { onDelete(item) },
                    onDragStart = {},
                    onDrag = {},
                    onDragEnd = {},
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun StoreHeader(storeName: String, itemCount: Int) {
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
                text = storeName.ifBlank { stringResource(R.string.store_geen) },
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
    // Swipe-to-delete only from the end edge (left, in LTR) — the same direction and
    // trash-can treatment as Voorraad's InventoryRow — not from the start edge, which would
    // otherwise fight with a stray horizontal component of the long-press reorder drag below.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        // .clip(SoftCardShapeCompact) on the box itself (not just its children below) — same
        // as ShoppingListGridTile/InventoryGridTile already do. Without this, nothing stops
        // the swipe-to-delete background's errorContainer color (a warm salmon/orange tone,
        // see Theme.kt's LinenErrorContainer) from rendering past the card's rounded corners
        // at rest, which is what read as an orange outline around every item — a plain border
        // drawn on the Card sits on *top* of that, it doesn't stop it from showing at all.
        // Clipping the whole swipe container to the same rounded rect the Card uses guarantees
        // nothing can ever render outside those bounds, matching Voorraad's InventoryRow.
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(SoftCardShapeCompact),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.shopping_list_delete_cd),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowHeightPx = it.size.height.toFloat() }
                // A long press starts the reorder drag; a plain tap falls through to the
                // Card's own onClick above to open the edit dialog.
                .pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart(rowHeightPx) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            // Matches InventoryRow's list row exactly.
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = SoftCardShapeCompact,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Material3's Checkbox draws its glyph at a fixed intrinsic size no matter what
                // Modifier.size() constrains its layout box to — a plain .size() only shrinks
                // the surrounding space (which is why this previously only affected the gap to
                // the image, not the checkbox itself). scale() is a render-layer transform and
                // is what actually shrinks the drawn checkbox; .size() keeps its footprint in
                // the row compact and proportional to the smaller visual. The leading
                // `padding(end = ...)` has to be the OUTERMOST modifier (i.e. applied before
                // .size()) to actually add extra space after the checkbox's fixed 20dp box,
                // rather than just eating into that box's own content area — it's the gap to
                // the product image right after it, previously 0dp (the two sat flush together).
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.padding(end = 10.dp).size(20.dp).scale(0.7f),
                )
                // No explicit containerColor/iconTint here any more — the coral secondaryContainer
                // this used to pass showed up as an orange-ish tint/ring around every item without
                // its own photo. Falling back to ProductImage's own default (primaryContainer)
                // both fixes that and actually matches Voorraad's InventoryRow, which never
                // overrode this in the first place.
                ProductImage(
                    imageUrl = item.imageUrl,
                    fallbackIcon = category.icon,
                    shape = RoundedCornerShape(8.dp),
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
}

/**
 * Deliberately mirrors InventoryGridTile's structure (background, image aspect ratio,
 * text styles, bottom icon row) field-for-field, so the tile view looks identical between
 * Voorraad and Boodschappenlijst — only the two action icons and the subtitle's fields
 * differ, since "in stock" and "on the list" aren't the same set of actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // Swipe right toggles checked/unchecked — "off the list" is the far more
                // frequent gesture while shopping; the same action is also available via
                // the icon row below, which (unlike this gesture) is part of the tile's
                // resting appearance.
                SwipeToDismissBoxValue.StartToEnd -> onCheckedChange(!item.isChecked)
                // Swipe left deletes — same direction/treatment as Voorraad's InventoryRow
                // and this screen's own list-view row above.
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.clip(SoftCardShapeCompact),
        backgroundContent = {
            val isDelete = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDelete) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    imageVector = when {
                        isDelete -> Icons.Filled.Delete
                        item.isChecked -> Icons.Filled.RadioButtonUnchecked
                        else -> Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    tint = if (isDelete) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SoftCardShapeCompact)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.1f)) {
                // Same fix as ShoppingListRow above — default ProductImage colors, not the
                // coral secondaryContainer that used to read as an orange border/tint.
                ProductImage(
                    imageUrl = item.imageUrl,
                    fallbackIcon = category.icon,
                    shape = SoftImageShape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(stringResource(category.displayNameRes), item.note).joinToString(" · ")
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
                        minQuantity = 1,
                        dense = true,
                        displayText = formatQuantityWithUnit(item.quantity, MeasurementUnit.fromStorageKey(item.unit)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onCheckedChange(!item.isChecked) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (item.isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = if (item.isChecked) {
                                    stringResource(R.string.shopping_list_mark_unchecked_cd)
                                } else {
                                    stringResource(R.string.shopping_list_mark_checked_cd)
                                },
                                tint = if (item.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.shopping_list_delete_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
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
            shape = SoftBadgeShape,
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
    initialStore: String = "",
    initialQuantity: Int = 1,
    initialNote: String = "",
    initialUnit: MeasurementUnit = MeasurementUnit.STUKS,
    imageUrl: String? = null,
    stores: List<StoreEntity>,
    onAddStore: (String) -> Unit,
    onDismiss: () -> Unit,
    onVoiceInputUnavailable: () -> Unit = {},
    onConfirm: (
        name: String,
        category: Category,
        store: String,
        quantity: Int,
        note: String,
        unit: MeasurementUnit,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var store by remember { mutableStateOf(initialStore) }
    var quantity by remember { mutableIntStateOf(initialQuantity) }
    var note by remember { mutableStateOf(initialNote) }
    var unit by remember { mutableStateOf(initialUnit) }

    // Pre-fills the name field with the transcription — never auto-submits, same reasoning as
    // the AI product-recognition camera: speech recognition can mishear, so the household still
    // gets to look at (and correct) the result before it becomes a real list item.
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) name = spoken
        }
    }
    val voicePrompt = stringResource(R.string.shopping_list_voice_input_prompt)

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
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        onVoiceInputUnavailable()
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.shopping_list_voice_input_cd))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CategoryDropdown(
                        selected = category,
                        onSelected = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StoreDropdown(
                        selected = store,
                        stores = stores,
                        onSelected = { store = it },
                        onAddStore = onAddStore,
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

