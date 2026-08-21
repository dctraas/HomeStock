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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.dtraas.homestock.data.local.entity.ShoppingListMeta
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
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** €-formatted per-unit price, e.g. "€1,89" — same shape as ProductDetailScreen's own price display. */
private fun formatPrice(value: Double): String = String.format(Locale.getDefault(), "€%.2f", value)

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
    val defaultListName = stringResource(R.string.shopping_list_title)
    val viewModel: ShoppingListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ShoppingListViewModel(
                    application.container.shoppingListRepository,
                    application.container.storeRepository,
                    application.container.shoppingListsRepository,
                    defaultListName,
                )
            }
        },
    )
    val groupedByStore by viewModel.groupedByStore.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val searchQuery by viewModel.searchQueryState.collectAsState()
    val sortMode by viewModel.sortModeState.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val activeList by viewModel.activeList.collectAsState()
    val totalPrice by viewModel.totalPrice.collectAsState()
    val allItems = groupedByStore.values.flatten()
    val hasCheckedItems = allItems.any { it.isChecked }
    val hasUncheckedItems = allItems.any { !it.isChecked }
    var viewMode by remember { mutableStateOf(ShoppingListViewMode.LIST) }
    var showMoreOptions by remember { mutableStateOf(false) }
    // Local, UI-only narrowing by store — ShoppingListViewModel's own groupedByStore always
    // returns every store's group; tapping a chip in StoreChipsRow just hides the other
    // groups here rather than round-tripping through a repository-level filter.
    var selectedStoreFilter by remember { mutableStateOf<String?>(null) }
    // Whether each store's "In de wagen" (already-checked) section is expanded — keyed by
    // store name so multiple stores can be open independently. Deliberately not applied to
    // ReorderableShoppingList's manual drag-order view (see its call site below): hiding
    // items out from under an active drag-to-reorder session would be far more disruptive
    // than the decluttering this is meant to buy while just browsing/shopping.
    val expandedCheckedStores = remember { mutableStateListOf<String>() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingListItemEntity?>(null) }
    var showListMenu by remember { mutableStateOf(false) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var listToRename by remember { mutableStateOf<ShoppingListMeta?>(null) }
    var listToDelete by remember { mutableStateOf<ShoppingListMeta?>(null) }
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

    fun clearCheckedWithUndo() {
        val itemsToRemove = groupedByStore.values.flatten().filter { it.isChecked }
        if (itemsToRemove.isEmpty()) return
        viewModel.clearChecked()
        coroutineScope.launch {
            // Count is only known here, once the user has tapped the icon, so this can't be
            // resolved via pluralStringResource (a @Composable call) like the other
            // pre-resolved snackbar strings above — same reasoning as shareTitle/unitLabels.
            val message = context.resources.getQuantityString(
                R.plurals.shopping_list_cleared_snackbar_format, itemsToRemove.size, itemsToRemove.size,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                itemsToRemove.forEach { viewModel.restoreItem(it) }
            }
        }
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showListMenu = true },
                        ) {
                            Text(
                                text = activeList.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.shopping_list_switch_list_cd),
                            )
                        }
                        // The running total used to repeat here as a topBar subtitle — dropped
                        // now that ShoppingProgressBar shows it right below, more prominently
                        // and right next to the checked/total count it's most useful beside.
                        ShoppingListSwitcherMenu(
                            expanded = showListMenu,
                            lists = lists,
                            activeListId = activeList.id,
                            onDismiss = { showListMenu = false },
                            onSelect = { viewModel.selectList(it) },
                            onCreateNew = { showCreateListDialog = true },
                            onRename = { listToRename = it },
                            onDelete = { listToDelete = it },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ShoppingFloatingActionBar(onAddClick = { showAddDialog = true })
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Single search/add field, always visible — typing narrows the list below as
            // before, and the "+" that appears beside it (only once there's text, so it
            // can't be tapped by accident) quick-adds a new item with that exact name and
            // sensible defaults (Overig/geen winkel/1 stuk) rather than opening the full
            // ItemFormDialog — that dialog is still one tap away via the floating "+" button
            // below for anyone who wants to set a category/winkel/eenheid up front.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            ) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    placeholder = stringResource(R.string.shopping_list_search_add_placeholder),
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.addItem(searchQuery.trim(), Category.OVERIG, "", 1)
                            viewModel.onSearchQueryChange("")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = {
                            viewModel.addItem(searchQuery.trim(), Category.OVERIG, "", 1)
                            viewModel.onSearchQueryChange("")
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_list_quick_add_cd))
                    }
                }
                IconButton(onClick = { showMoreOptions = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.shopping_list_more_options_cd))
                }
            }

            if (groupedByStore.isNotEmpty()) {
                ShoppingProgressBar(
                    checkedCount = allItems.count { it.isChecked },
                    totalCount = allItems.size,
                    totalPrice = totalPrice,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                StoreChipsRow(
                    stores = groupedByStore.keys.toList(),
                    selectedStore = selectedStoreFilter,
                    onStoreSelected = { selectedStoreFilter = it },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }

            // The store chip row above narrows which groups render below — a local,
            // UI-only filter (see selectedStoreFilter's doc), independent of the search
            // query which ShoppingListViewModel.groupedByStore already applied.
            val displayedGroups = selectedStoreFilter?.let { store ->
                groupedByStore.filterKeys { it == store }
            } ?: groupedByStore

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

            if (displayedGroups.isEmpty()) {
                EmptyShoppingList(
                    isFiltered = searchQuery.isNotBlank() || selectedStoreFilter != null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (viewMode == ShoppingListViewMode.LIST && sortMode == ShoppingListSortMode.MANUAL) {
                ReorderableShoppingList(
                    groupedByStore = displayedGroups,
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
                    groupedByStore = displayedGroups,
                    expandedCheckedStores = expandedCheckedStores,
                    onToggleCheckedExpanded = { store ->
                        if (store in expandedCheckedStores) expandedCheckedStores.remove(store) else expandedCheckedStores.add(store)
                    },
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
                    displayedGroups.forEach { (storeName, itemsInStore) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StoreHeader(storeName, itemCount = itemsInStore.size)
                        }
                        val checkedExpanded = storeName in expandedCheckedStores
                        val (unchecked, checked) = itemsInStore.partition { !it.isChecked }
                        items(unchecked, key = { it.id }) { item ->
                            val step = MeasurementUnit.fromStorageKey(item.unit).step
                            ShoppingListGridTile(
                                item = item,
                                onCheckedChange = { checkedValue -> viewModel.setChecked(item.id, checkedValue) },
                                onClick = { editingItem = item },
                                onIncrease = { viewModel.setQuantity(item.id, item.quantity + step) },
                                onDecrease = { viewModel.setQuantity(item.id, (item.quantity - step).coerceAtLeast(1)) },
                                onDelete = { deleteWithUndo(item) },
                            )
                        }
                        if (checked.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CheckedSectionToggle(
                                    count = checked.size,
                                    expanded = checkedExpanded,
                                    onClick = {
                                        if (checkedExpanded) expandedCheckedStores.remove(storeName) else expandedCheckedStores.add(storeName)
                                    },
                                )
                            }
                            if (checkedExpanded) {
                                items(checked, key = { it.id }) { item ->
                                    val step = MeasurementUnit.fromStorageKey(item.unit).step
                                    ShoppingListGridTile(
                                        item = item,
                                        onCheckedChange = { checkedValue -> viewModel.setChecked(item.id, checkedValue) },
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
            }
        }

        if (showMoreOptions) {
            ShoppingListMoreOptionsDialog(
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                sortMode = sortMode,
                onSortModeChange = viewModel::onSortModeChange,
                canShare = groupedByStore.isNotEmpty(),
                hasUncheckedItems = hasUncheckedItems,
                hasCheckedItems = hasCheckedItems,
                onShare = ::shareList,
                onCheckAll = viewModel::checkAll,
                onClearChecked = ::clearCheckedWithUndo,
                onDismiss = { showMoreOptions = false },
            )
        }

        if (showAddDialog) {
            ItemFormDialog(
                title = stringResource(R.string.shopping_list_item_add_title),
                confirmLabel = stringResource(R.string.shopping_list_add_confirm),
                stores = stores,
                onAddStore = viewModel::addStore,
                onDismiss = { showAddDialog = false },
                onVoiceInputUnavailable = onVoiceInputUnavailable,
                onConfirm = { name, category, store, quantity, note, unit, price ->
                    viewModel.addItem(name, category, store, quantity, note.trim().ifBlank { null }, unit, price)
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
                initialPrice = item.price,
                imageUrl = item.imageUrl,
                stores = stores,
                onAddStore = viewModel::addStore,
                onDismiss = { editingItem = null },
                onVoiceInputUnavailable = onVoiceInputUnavailable,
                onConfirm = { name, category, store, quantity, note, unit, price ->
                    viewModel.updateItem(
                        item.copy(
                            name = name,
                            category = category.storageKey,
                            store = store,
                            quantity = quantity,
                            note = note.trim().ifBlank { null },
                            unit = unit.storageKey,
                            price = price,
                        )
                    )
                    editingItem = null
                },
            )
        }

        if (showCreateListDialog) {
            ListNameDialog(
                title = stringResource(R.string.shopping_list_new_list_title),
                confirmLabel = stringResource(R.string.shopping_list_new_list_confirm),
                onDismiss = { showCreateListDialog = false },
                onConfirm = { name ->
                    viewModel.createList(name)
                    showCreateListDialog = false
                },
            )
        }

        listToRename?.let { list ->
            ListNameDialog(
                title = stringResource(R.string.shopping_list_rename_list_title),
                confirmLabel = stringResource(R.string.common_save),
                initialName = list.name,
                onDismiss = { listToRename = null },
                onConfirm = { name ->
                    list.id?.let { viewModel.renameList(it, name) }
                    listToRename = null
                },
            )
        }

        listToDelete?.let { list ->
            AlertDialog(
                onDismissRequest = { listToDelete = null },
                title = { Text(stringResource(R.string.shopping_list_delete_list_title)) },
                text = { Text(stringResource(R.string.shopping_list_delete_list_text_format, list.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            list.id?.let(viewModel::deleteList)
                            listToDelete = null
                        },
                    ) { Text(stringResource(R.string.shopping_list_delete_list_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { listToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
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
 *
 * Each store's already-checked items collapse behind one "In de wagen (N)" toggle row rather
 * than staying inline — [expandedCheckedStores] tracks which stores currently have that
 * section expanded (by store name), so browsing a half-shopped list stays focused on what's
 * still needed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AisleOrderedShoppingList(
    groupedByStore: Map<String, List<ShoppingListItemEntity>>,
    expandedCheckedStores: List<String>,
    onToggleCheckedExpanded: (String) -> Unit,
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
            val (unchecked, checked) = itemsInStore.partition { !it.isChecked }
            items(unchecked, key = { it.id }) { item ->
                ShoppingListRow(
                    item = item,
                    onCheckedChange = { checkedValue -> onCheckedChange(item, checkedValue) },
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
            if (checked.isNotEmpty()) {
                val expanded = storeName in expandedCheckedStores
                item(key = "checked_toggle_$storeName") {
                    CheckedSectionToggle(
                        count = checked.size,
                        expanded = expanded,
                        onClick = { onToggleCheckedExpanded(storeName) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (expanded) {
                    items(checked, key = { it.id }) { item ->
                        ShoppingListRow(
                            item = item,
                            onCheckedChange = { checkedValue -> onCheckedChange(item, checkedValue) },
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
    }
}

/** The "In de wagen (N)" collapsed-section row — used by both [AisleOrderedShoppingList] and
 *  the grid view's per-store checked section. */
@Composable
private fun CheckedSectionToggle(count: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.shopping_list_in_cart_format, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        // as ShoppingListGridTile/InventoryGridTile already do, matching Voorraad's
        // InventoryRow. The swipe-to-delete background (backgroundContent below) only actually
        // paints its errorContainer color while a swipe is in progress now, so this clip is
        // mainly defense-in-depth for that brief window — keeps its square corners from ever
        // poking out past the rounded card shape while it's visible, on top of it not being
        // permanently present behind the card any more (see backgroundContent's own comment).
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(SoftCardShapeCompact),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Transparent at rest (dismissDirection reports Settled whenever offset == 0f, not
            // just "not currently dragging") rather than a permanently-present errorContainer —
            // SwipeToDismissBox always composes backgroundContent regardless of swipe state, so
            // a resting-state color here was never actually confined to "during an active
            // swipe" the way it visually reads; it just always sat directly behind the Card,
            // shadow or no shadow, tinting anything semi-transparent above it (see the Card's
            // elevation comment below). Only paint it once a swipe is actually happening.
            val isSettled = dismissState.dismissDirection == SwipeToDismissBoxValue.Settled
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSettled) Color.Transparent else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (!isSettled) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.shopping_list_delete_cd),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
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
            // Belt-and-braces alongside backgroundContent now being transparent at rest (see
            // above): Card's own drop shadow is semi-transparent, so it shows whatever sits
            // directly behind it — even with nothing painted there any more, still 0dp so
            // there's nothing for a future change to that background to accidentally tint again.
            // ShoppingListGridTile never had this class of bug at all — it uses a plain
            // Column + background, no Card, so no shadow to begin with.
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = SoftCardShapeCompact,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A custom circle rather than Material3's Checkbox — that drew its glyph at a
                // fixed intrinsic size no matter what Modifier.size() constrained its layout
                // box to, so shrinking it to fit this row's compact height also shrank its tap
                // target down to something fiddly to hit accurately. This circle's whole 26dp
                // footprint is the tap target, no separate glyph size to fight.
                ShoppingCheckCircle(
                    checked = item.isChecked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.padding(end = 10.dp),
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
                        text = listOfNotNull(stringResource(category.displayNameRes), item.note, item.price?.let(::formatPrice)).joinToString(" · "),
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
                // No standalone delete icon here any more — unlike Voorraad's InventoryRow,
                // this row never had more than this one trailing icon to begin with, and it
                // was already fully redundant with the swipe-to-delete above (EndToStart, see
                // dismissState). Removing it is the honest version of "declutter the trailing
                // icons": wrapping a single icon in a "···" menu would only have added a tap
                // for no benefit. onDelete stays as a parameter — swipe still calls it.
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
            // Transparent at rest, same reasoning as the list-view row above — dismissDirection
            // is Settled whenever offset == 0f, not just "not currently mid-drag", so without
            // this branch a resting tile always sat on a fully-opaque errorContainer/
            // primaryContainer background regardless of whether anyone was swiping it.
            val isSettled = dismissState.dismissDirection == SwipeToDismissBoxValue.Settled
            val isDelete = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when {
                            isSettled -> Color.Transparent
                            isDelete -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                if (!isSettled) {
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
                val subtitle = listOfNotNull(stringResource(category.displayNameRes), item.note, item.price?.let(::formatPrice)).joinToString(" · ")
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
                    // Delete used to sit here too, next to the check-toggle — dropped as the
                    // same redundant-with-swipe icon as ShoppingListRow's above (this tile's
                    // own EndToStart swipe already deletes it). Check-toggle stays: unlike
                    // delete, it's this whole screen's primary action, not a secondary one to
                    // fold away — it's also swipeable (StartToEnd) but, being the thing you do
                    // dozens of times per trip, keeping it as a direct tap target too matters
                    // more here than it would for an occasional action.
                    ShoppingCheckCircle(checked = item.isChecked, onCheckedChange = onCheckedChange)
                }
            }
        }
    }
}

/** A 26dp circular check control — the tap target *is* the visible circle, unlike the old
 *  scaled-down Material [androidx.compose.material3.Checkbox] this replaces (see call sites'
 *  comments). Filled sage-green with a white check glyph when checked; a bare outline otherwise. */
@Composable
private fun ShoppingCheckCircle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = BorderStroke(2.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.size(26.dp).clickable { onCheckedChange(!checked) },
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * How far through the list the household is — checked/total items, plus the running total
 * (see [ShoppingListViewModel.totalPrice]) when at least one item has a price set. Sits right
 * under the search/add field, above the store chips.
 */
@Composable
private fun ShoppingProgressBar(
    checkedCount: Int,
    totalCount: Int,
    totalPrice: Double?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.shopping_list_progress_format, checkedCount, totalCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (totalPrice != null) {
                Text(
                    text = formatPrice(totalPrice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/** Horizontal "Alle winkels" + one chip per store present on the (search-filtered) list —
 *  see [ShoppingListScreen]'s `selectedStoreFilter` for what selecting one does. */
@Composable
private fun StoreChipsRow(
    stores: List<String>,
    selectedStore: String?,
    onStoreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A single store (or none at all) makes the "alle winkels" vs. "dat ene winkel" choice
    // meaningless — same reasoning as InventoryScreen only offering "group by locatie" once
    // there's more than one location in use.
    if (stores.size <= 1) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item {
            FilterChip(
                selected = selectedStore == null,
                onClick = { onStoreSelected(null) },
                label = { Text(stringResource(R.string.shopping_list_all_stores)) },
                shape = SoftCardShapeCompact,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        items(stores) { store ->
            FilterChip(
                selected = selectedStore == store,
                onClick = { onStoreSelected(if (selectedStore == store) null else store) },
                label = { Text(store.ifBlank { stringResource(R.string.store_geen) }) },
                leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = SoftCardShapeCompact,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/**
 * Replaces the old single "+" FAB: "Winkelmodus" (a shortcut into Winkelindeling-sort, see its
 * call site) as a wide pill, with the coral "+" — still opening the full [ItemFormDialog] with
 * category/winkel/eenheid fields, unlike the quick-add field above — beside it.
 */
@Composable
private fun ShoppingFloatingActionBar(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // "Winkelmodus" used to sit here too, as a second pill next to "+" — removed on request
    // (see this commit's message for where it could go instead). The lone remaining action
    // widens into the same full-width coral pill Voorraad's "Scannen" uses, rather than
    // leaving a small square button floating off-center on its own.
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = SoftCardShapeCompact,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.shopping_list_add_item_cd),
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Everything that used to live in the always-visible icon row (delen/alles afvinken/
 * afgevinkte wissen/sorteren/weergave) — folded into one overflow sheet, same pattern as
 * InventoryScreen's MoreOptionsDialog. [ShoppingListSortMenuButton] is reused as-is.
 */
@Composable
private fun ShoppingListMoreOptionsDialog(
    viewMode: ShoppingListViewMode,
    onViewModeChange: (ShoppingListViewMode) -> Unit,
    sortMode: ShoppingListSortMode,
    onSortModeChange: (ShoppingListSortMode) -> Unit,
    canShare: Boolean,
    hasUncheckedItems: Boolean,
    hasCheckedItems: Boolean,
    onShare: () -> Unit,
    onCheckAll: () -> Unit,
    onClearChecked: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_list_more_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ShoppingOptionRow(label = stringResource(R.string.shopping_list_sort_cd)) {
                    ShoppingListSortMenuButton(selected = sortMode, onSelected = onSortModeChange)
                }
                ShoppingOptionRow(label = stringResource(R.string.inventory_show_as_tiles_cd)) {
                    IconButton(
                        onClick = {
                            onViewModeChange(if (viewMode == ShoppingListViewMode.LIST) ShoppingListViewMode.GRID else ShoppingListViewMode.LIST)
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (viewMode == ShoppingListViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (canShare || hasUncheckedItems || hasCheckedItems) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                if (canShare) {
                    ShoppingOptionRow(label = stringResource(R.string.shopping_list_share_cd)) {
                        IconButton(onClick = { onDismiss(); onShare() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                if (hasUncheckedItems) {
                    ShoppingOptionRow(label = stringResource(R.string.shopping_list_check_all_cd)) {
                        IconButton(onClick = { onDismiss(); onCheckAll() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                if (hasCheckedItems) {
                    ShoppingOptionRow(label = stringResource(R.string.shopping_list_clear_checked_cd)) {
                        IconButton(onClick = { onDismiss(); onClearChecked() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        },
    )
}

/** One row of [ShoppingListMoreOptionsDialog]: a label on the left, the control on the right. */
@Composable
private fun ShoppingOptionRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        trailing()
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
    initialPrice: Double? = null,
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
        price: Double?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var store by remember { mutableStateOf(initialStore) }
    var quantity by remember { mutableIntStateOf(initialQuantity) }
    var note by remember { mutableStateOf(initialNote) }
    var unit by remember { mutableStateOf(initialUnit) }
    // Neither Opmerking nor Prijs have an input in this form anymore (removed on request) — this
    // just carries whatever an item already had straight through to onConfirm unchanged, so
    // editing an item that has one from before (e.g. from a receipt scan) doesn't silently wipe
    // it. Formatted the same way the field itself used to parse it, so that round-trip is exact.
    val priceText = initialPrice?.let { formatPrice(it).removePrefix("€") } ?: ""

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
                    // Opmerking and Prijs (and the "Meer opties" toggle that used to hold them)
                    // are gone from this form on request — see the priceText comment above for
                    // what still happens to an item that already has one of these set.
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = priceText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
                    onConfirm(name, category, store, quantity, note, unit, price)
                },
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

/**
 * The list-switcher dropdown, anchored to the title in the top app bar — every list the
 * household has (default first, see [ShoppingListViewModel.lists]), a checkmark on the active
 * one, and "+ Nieuwe lijst" at the bottom. The default list has no rename/delete menu (it isn't
 * a document [com.dtraas.homestock.data.repository.ShoppingListsRepository] manages, see
 * [ShoppingListMeta]'s doc) — only named lists get the "…" overflow.
 */
@Composable
private fun ShoppingListSwitcherMenu(
    expanded: Boolean,
    lists: List<ShoppingListMeta>,
    activeListId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreateNew: () -> Unit,
    onRename: (ShoppingListMeta) -> Unit,
    onDelete: (ShoppingListMeta) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        lists.forEach { list ->
            DropdownMenuItem(
                text = { Text(list.name) },
                leadingIcon = {
                    if (list.id == activeListId) Icon(Icons.Filled.Check, contentDescription = null)
                },
                trailingIcon = if (list.id != null) {
                    {
                        var itemMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { itemMenuExpanded = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.shopping_list_list_options_cd))
                            }
                            DropdownMenu(expanded = itemMenuExpanded, onDismissRequest = { itemMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shopping_list_rename_list_action)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        itemMenuExpanded = false
                                        onDismiss()
                                        onRename(list)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shopping_list_delete_list_action)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        itemMenuExpanded = false
                                        onDismiss()
                                        onDelete(list)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                onClick = {
                    onSelect(list.id)
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.shopping_list_new_list_action)) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = {
                onDismiss()
                onCreateNew()
            },
        )
    }
}

/** Shared by "nieuwe lijst" and "lijst hernoemen" — same single-field form either way. */
@Composable
private fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

