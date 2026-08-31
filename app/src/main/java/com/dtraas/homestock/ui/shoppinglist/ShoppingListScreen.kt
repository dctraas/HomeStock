package com.dtraas.homestock.ui.shoppinglist

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.dtraas.homestock.ui.components.AddStoreDialog
import com.dtraas.homestock.ui.components.CategoryDropdown
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.MeasurementUnitDropdown
import com.dtraas.homestock.ui.components.ProductImage
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.components.SheetActionRow
import com.dtraas.homestock.ui.components.SheetChip
import com.dtraas.homestock.ui.components.SheetEyebrow
import com.dtraas.homestock.ui.components.SheetPrimaryButton
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.formatQuantityWithUnit
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
fun ShoppingListScreen(onNavigateToShoppingMode: (listId: String?, storeName: String?) -> Unit = { _, _ -> }) {
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
                    application.container.activityLogRepository,
                    application.container.inventoryRepository,
                    defaultListName,
                )
            }
        },
    )
    val groupedByStore by viewModel.groupedByStore.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val sortMode by viewModel.sortModeState.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val activeList by viewModel.activeList.collectAsState()
    val itemCountByListId by viewModel.itemCountByListId.collectAsState()
    val allItems = groupedByStore.values.flatten()
    val hasCheckedItems = allItems.any { it.isChecked }
    val hasUncheckedItems = allItems.any { !it.isChecked }
    var viewMode by remember { mutableStateOf(ShoppingListViewMode.LIST) }
    var showMoreOptions by remember { mutableStateOf(false) }
    // Local, UI-only narrowing by store — ShoppingListViewModel's own groupedByStore always
    // returns every store's group; tapping a chip in StoreChipsRow just hides the other
    // groups here rather than round-tripping through a repository-level filter.
    var selectedStoreFilter by remember { mutableStateOf<String?>(null) }
    // The header's search icon toggles this on, swapping the list-name row for an inline
    // SearchField — same local, UI-only narrowing pattern as selectedStoreFilter above, applied
    // together with it below rather than routed through the ViewModel/repository.
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
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

    // Shared by the swipe-to-delete row and ItemEditSheet's own delete button — both remove
    // the item straight away and offer the same "ongedaan maken" snackbar to undo it. Declared
    // at this outer scope (not inside the Column below) so it's reachable from editingItem's own
    // dialog block, a sibling of that Column rather than nested inside it.
    fun deleteWithUndo(item: ShoppingListItemEntity) {
        viewModel.removeItem(item.id)
        coroutineScope.launch {
            // showSnackbar defaults to SnackbarDuration.Indefinite whenever an actionLabel is
            // set, so without an explicit duration the "ongedaan maken" snackbar would never
            // auto-dismiss.
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

    val historySuggestions by viewModel.historySuggestions.collectAsState()
    val lowStockSuggestions by viewModel.lowStockSuggestions.collectAsState()

    Scaffold(
        // The custom header below already claims the status bar inset itself (see
        // ShoppingListHeader's own windowInsetsPadding) — without this, Scaffold's default
        // contentWindowInsets (safeDrawing, top included since there's no topBar) hands that
        // same inset to `padding` below too, stacking a second status-bar-height gap above the
        // header instead of the header starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The green header carries just the list switcher + meer-opties now — the checked/
            // total progress bar, store/price meta line, and member avatars that used to sit
            // underneath it are gone per explicit request, so the item list itself starts right
            // after this instead. Delen stays gone from here too: it's a plain duplicate of
            // "Boodschappenlijst delen" already inside meer-opties (see ShoppingListMoreOptionsDialog).
            ShoppingListHeader(
                listName = activeList.name,
                onListNameClick = { showListMenu = true },
                listMenuExpanded = showListMenu,
                lists = lists,
                activeListId = activeList.id,
                itemCountByListId = itemCountByListId,
                onDismissListMenu = { showListMenu = false },
                onSelectList = viewModel::selectList,
                onCreateNewList = { showCreateListDialog = true },
                onRenameList = { listToRename = it },
                onDeleteList = { listToDelete = it },
                onMoreOptionsClick = { showMoreOptions = true },
                showSearch = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    showSearch = !showSearch
                    if (!showSearch) searchQuery = ""
                },
            )

            if (groupedByStore.isNotEmpty()) {
                StoreChipsRow(
                    storeCounts = groupedByStore.mapValues { (_, items) -> items.size },
                    totalCount = allItems.size,
                    selectedStore = selectedStoreFilter,
                    onStoreSelected = { selectedStoreFilter = it },
                    // Tighter bottom gap (was 6dp) — combined with the lists' own top content
                    // padding below (also trimmed), leaves more room for the actual products.
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }

            // The store chip row above narrows which groups render below by store; the header's
            // search field (see showSearch/searchQuery) narrows by name — both are local,
            // UI-only filters (see selectedStoreFilter's own doc), applied together here rather
            // than routed through the ViewModel/repository.
            val displayedGroups = run {
                val byStore = selectedStoreFilter?.let { store -> groupedByStore.filterKeys { it == store } } ?: groupedByStore
                val query = searchQuery.trim()
                if (query.isEmpty()) {
                    byStore
                } else {
                    byStore.mapValues { (_, items) -> items.filter { it.name.contains(query, ignoreCase = true) } }
                        .filterValues { it.isNotEmpty() }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (displayedGroups.isEmpty()) {
                EmptyShoppingList(
                    isFiltered = selectedStoreFilter != null,
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

            // Thumb-zone bottom bar: suggestion chips (own history + Voorraad items running
            // low), then a full-width "Winkelmodus" button beside a round "+" button — spraak
            // moved up into the header, same row as the progress bar, to make room here.
            ShoppingListBottomBar(
                historySuggestions = historySuggestions,
                lowStockSuggestions = lowStockSuggestions,
                onHistorySuggestionClick = { name -> viewModel.addItem(name, Category.OVERIG, "", 1) },
                onLowStockSuggestionClick = { suggestion ->
                    viewModel.addItem(suggestion.name, suggestion.category, "", 1)
                },
                // Neemt het huidige winkel-filterchip mee, indien actief, zodat Winkelmodus
                // direct opent voor de winkel die je al aan het bekijken was — anders (bij
                // "Alle winkels") vraagt Winkelmodus het zelf, want daar zie je toch maar één
                // winkel tegelijk.
                onShoppingModeClick = { onNavigateToShoppingMode(activeList.id, selectedStoreFilter) },
                onAddClick = { showAddDialog = true },
            )
        }

        if (showMoreOptions) {
            ShoppingListMoreOptionsDialog(
                listName = activeList.name,
                itemCount = allItems.size,
                storeCount = groupedByStore.keys.count { it.isNotBlank() },
                canRename = activeList.id != null,
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                sortMode = sortMode,
                onSortModeChange = viewModel::onSortModeChange,
                canShare = groupedByStore.isNotEmpty(),
                hasUncheckedItems = hasUncheckedItems,
                uncheckedCount = allItems.count { !it.isChecked },
                hasCheckedItems = hasCheckedItems,
                checkedCount = allItems.count { it.isChecked },
                onShare = ::shareList,
                onCheckAll = viewModel::checkAll,
                onClearChecked = ::clearCheckedWithUndo,
                onRename = { listToRename = activeList },
                onDismiss = { showMoreOptions = false },
            )
        }

        if (showAddDialog) {
            ItemFormDialog(
                title = stringResource(R.string.shopping_list_item_add_title),
                confirmLabel = stringResource(R.string.shopping_list_item_add_confirm_full),
                stores = stores,
                onAddStore = viewModel::addStore,
                onDismiss = { showAddDialog = false },
                onVoiceInputUnavailable = onVoiceInputUnavailable,
                guessFor = viewModel::guessFor,
                historySuggestions = historySuggestions,
                lowStockSuggestions = lowStockSuggestions,
                onQuickAdd = { name, category -> viewModel.addItem(name, category, "", 1) },
                onConfirm = { name, category, store, quantity, note, unit, price ->
                    viewModel.addItem(name, category, store, quantity, note.trim().ifBlank { null }, unit, price)
                    showAddDialog = false
                },
            )
        }

        editingItem?.let { item ->
            ItemEditSheet(
                shoppingItem = item,
                stores = stores,
                onAddStore = viewModel::addStore,
                onDismiss = { editingItem = null },
                onDelete = { deleteWithUndo(item); editingItem = null },
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
        // Top trimmed to 2dp (was 8dp) — less gap before the first sticky StoreHeader, per
        // "regelafstand tussen de winkelchips en de eerste winkelnaam kleiner"; bottom kept at
        // 8dp so the last row still gets breathing room above the bottom bar.
        contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp),
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
        // Top trimmed to 2dp (was 8dp) — less gap before the first sticky StoreHeader, per
        // "regelafstand tussen de winkelchips en de eerste winkelnaam kleiner"; bottom kept at
        // 8dp so the last row still gets breathing room above the bottom bar.
        contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp),
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
            // Transparent — individual items don't need their own fill/border, per design
            // review; the page's own background is enough to separate one row from the next.
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
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
        modifier = modifier.size(36.dp).clickable { onCheckedChange(!checked) },
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The fixed (non-scrolling) green gradient header — replaces the old flat HomeStockTopAppBar.
 * List-name switcher + a search icon + meer-opties on the (centered-title) top row — the search
 * icon swaps that whole row for an inline [SearchField] while active — and, once the active list
 * has items, the progress bar right underneath (back after a round briefly removed it for more
 * vertical room; the household asked for it back). Delen stays gone from here: it was a plain
 * duplicate of "Boodschappenlijst delen" already inside meer-opties (see
 * ShoppingListMoreOptionsDialog), unrelated to the progress bar's return. The spraak
 * (voice-quick-add) button that used to sit on the progress-bar row is gone too, per explicit
 * request — replaced by [MemberAvatarRow], since who's actually in the household read as more
 * useful there than a second, redundant way to add an item (the quick-add row and every item
 * field already offer voice input of their own).
 */
@Composable
private fun ShoppingListHeader(
    listName: String,
    onListNameClick: () -> Unit,
    listMenuExpanded: Boolean,
    lists: List<ShoppingListMeta>,
    activeListId: String?,
    itemCountByListId: Map<String?, Int>,
    onDismissListMenu: () -> Unit,
    onSelectList: (String?) -> Unit,
    onCreateNewList: () -> Unit,
    onRenameList: (ShoppingListMeta) -> Unit,
    onDeleteList: (ShoppingListMeta) -> Unit,
    onMoreOptionsClick: () -> Unit,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            // A touch tighter than before (was 12.dp) — reclaims a little vertical room for
            // the item list below, per "iets meer boodschap items te kunnen tonen".
            .padding(bottom = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showSearch) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = stringResource(R.string.shopping_list_search_placeholder),
                        dense = true,
                        modifier = Modifier.weight(1f),
                        // White pill instead of the default outline styling, same white-on-green
                        // pairing InventoryScreen's own header search field already uses.
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
                    )
                    IconButton(onClick = onToggleSearch) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = contentColor)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onListNameClick)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    Text(
                        text = listName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.shopping_list_switch_list_cd),
                        tint = contentColor,
                    )
                }
                if (listMenuExpanded) {
                    ShoppingListSwitcherSheet(
                        lists = lists,
                        activeListId = activeListId,
                        itemCountByListId = itemCountByListId,
                        onDismiss = onDismissListMenu,
                        onSelect = onSelectList,
                        onCreateNew = onCreateNewList,
                        onRename = onRenameList,
                        onDelete = onDeleteList,
                    )
                }
                Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.shopping_list_search_cd), tint = contentColor)
                    }
                    IconButton(onClick = onMoreOptionsClick) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.shopping_list_more_options_cd), tint = contentColor)
                    }
                }
            }
        }
    }
}

/** Horizontal "Alle winkels" + one chip per store present on the (search-filtered) list, each
 *  labeled with its item count — see [ShoppingListScreen]'s `selectedStoreFilter` for what
 *  selecting one does. */
@Composable
private fun StoreChipsRow(
    storeCounts: Map<String, Int>,
    totalCount: Int,
    selectedStore: String?,
    onStoreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A single store (or none at all) makes the "alle winkels" vs. "dat ene winkel" choice
    // meaningless — same reasoning as InventoryScreen only offering "group by locatie" once
    // there's more than one location in use.
    if (storeCounts.size <= 1) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item {
            FilterChip(
                selected = selectedStore == null,
                onClick = { onStoreSelected(null) },
                label = { Text(stringResource(R.string.shopping_list_store_chip_label_format, stringResource(R.string.shopping_list_all_stores), totalCount)) },
                shape = SoftCardShapeCompact,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        items(storeCounts.entries.toList(), key = { it.key }) { (store, count) ->
            FilterChip(
                selected = selectedStore == store,
                onClick = { onStoreSelected(if (selectedStore == store) null else store) },
                label = {
                    Text(
                        stringResource(
                            R.string.shopping_list_store_chip_label_format,
                            store.ifBlank { stringResource(R.string.store_geen) },
                            count,
                        )
                    )
                },
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
 * Thumb-zone bottom bar: suggestion chips (own history + Voorraad items running low, see
 * [ShoppingListViewModel.historySuggestions]/[ShoppingListViewModel.lowStockSuggestions]) above
 * a page-wide "Winkelmodus" pill (opens [ShoppingModeScreen]) and a round "+" button (opens
 * "Item toevoegen" — [onAddClick]). Spraak used to be this round button; it moved up into
 * [ShoppingListHeader], same row as the checked/total count, on explicit request, freeing this
 * spot for a quicker "add one item" action alongside the new Winkelmodus entry point.
 */
@Composable
private fun ShoppingListBottomBar(
    historySuggestions: List<String>,
    lowStockSuggestions: List<LowStockSuggestion>,
    onHistorySuggestionClick: (String) -> Unit,
    onLowStockSuggestionClick: (LowStockSuggestion) -> Unit,
    onShoppingModeClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    // Wrapped in a rounded-top Surface again ("dezelfde achtergrondkleur als eerder") — but
    // surfaceContainer + rounded top corners rather than the old plain colorScheme.surface +
    // tonalElevation, which painted a flat band whose tone read as a visible seam against the
    // scrolling list's colorScheme.background right above it. surfaceContainer already sits a
    // deliberate step above background in the app's tonal ramp (see the theme's surfaceContainer
    // restoration), and the rounded top corners read as a deliberate panel instead of an abrupt
    // color cut, so the seam doesn't reappear.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (lowStockSuggestions.isNotEmpty() || historySuggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    // Bijna-op items first — restocking is the more actionable suggestion of
                    // the two — then the household's own recent history.
                    items(lowStockSuggestions, key = { "low_${it.barcode}" }) { suggestion ->
                        SuggestionChip(
                            onClick = { onLowStockSuggestionClick(suggestion) },
                            label = { Text(suggestion.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                Icon(Icons.Filled.TrendingDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            shape = SoftCardShapeCompact,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                    items(historySuggestions, key = { "hist_$it" }) { name ->
                        SuggestionChip(
                            onClick = { onHistorySuggestionClick(name) },
                            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            shape = SoftCardShapeCompact,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Solid primary green, fully rounded (a true pill, not just a soft-rounded
                // rect) — per the Claude Design mockup's own close-up of this button, replacing
                // the earlier literal white/black treatment.
                Surface(
                    onClick = onShoppingModeClick,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = stringResource(R.string.shopping_list_bottom_shopping_mode_label),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                FilledIconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_list_quick_add_item_cd))
                }
            }
        }
    }
}

/**
 * Everything that used to live in the always-visible icon row (delen/alles afvinken/
 * afgevinkte wissen/sorteren/weergave) — one overflow sheet, two labelled groups: WEERGAVE
 * (sort mode + tile view, both apply instantly) and the active list's own actions, headed by
 * its name and a live item/store count so this doubles as "which list am I even looking at".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListMoreOptionsDialog(
    listName: String,
    itemCount: Int,
    storeCount: Int,
    canRename: Boolean,
    viewMode: ShoppingListViewMode,
    onViewModeChange: (ShoppingListViewMode) -> Unit,
    sortMode: ShoppingListSortMode,
    onSortModeChange: (ShoppingListSortMode) -> Unit,
    canShare: Boolean,
    hasUncheckedItems: Boolean,
    uncheckedCount: Int,
    hasCheckedItems: Boolean,
    checkedCount: Int,
    onShare: () -> Unit,
    onCheckAll: () -> Unit,
    onClearChecked: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SheetTitle(
                title = listName,
                subtitle = pluralStringResource(R.plurals.shopping_list_item_count_format, itemCount, itemCount) +
                    " · " + pluralStringResource(R.plurals.shopping_list_store_count_format, storeCount, storeCount),
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetEyebrow(text = stringResource(R.string.inventory_view_mode_title))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.shopping_list_sort_cd), style = MaterialTheme.typography.bodyLarge)
                    SortSegmentedControl(selected = sortMode, onSelected = onSortModeChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.inventory_show_as_tiles_cd), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = viewMode == ShoppingListViewMode.GRID,
                        onCheckedChange = { checked ->
                            onViewModeChange(if (checked) ShoppingListViewMode.GRID else ShoppingListViewMode.LIST)
                        },
                    )
                }
            }

            if (canShare || hasUncheckedItems || hasCheckedItems || canRename) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetEyebrow(text = stringResource(R.string.shopping_list_options_this_list_section))
                    if (canShare) {
                        SheetActionRow(
                            icon = Icons.Filled.Share,
                            title = stringResource(R.string.shopping_list_share_cd),
                            onClick = { onDismiss(); onShare() },
                        )
                    }
                    if (hasUncheckedItems) {
                        SheetActionRow(
                            icon = Icons.Filled.DoneAll,
                            title = stringResource(R.string.shopping_list_check_all_cd),
                            subtitle = pluralStringResource(R.plurals.shopping_list_open_items_format, uncheckedCount, uncheckedCount),
                            onClick = { onDismiss(); onCheckAll() },
                            trailing = null,
                        )
                    }
                    if (canRename) {
                        SheetActionRow(
                            icon = Icons.Filled.Edit,
                            title = stringResource(R.string.shopping_list_rename_list_action),
                            onClick = { onDismiss(); onRename() },
                        )
                    }
                    if (hasCheckedItems) {
                        SheetActionRow(
                            icon = Icons.Filled.DeleteSweep,
                            title = stringResource(R.string.shopping_list_clear_checked_cd),
                            subtitle = pluralStringResource(R.plurals.shopping_list_clear_checked_subtitle_format, checkedCount, checkedCount),
                            onClick = { onDismiss(); onClearChecked() },
                            titleColor = MaterialTheme.colorScheme.error,
                            subtitleColor = MaterialTheme.colorScheme.error,
                            iconTileColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.error,
                            trailing = null,
                        )
                    }
                }
            }
        }
    }
}

/** Two-way "Winkelindeling / Handmatig" toggle for [ShoppingListSortMode] — the only two real
 *  modes the app has (see [ShoppingListSortMode]'s own doc); a plain pill pair rather than
 *  Material3's [androidx.compose.material3.SegmentedButton] keeps this in the same visual
 *  language as every chip/pill elsewhere in these sheets. */
@Composable
private fun SortSegmentedControl(selected: ShoppingListSortMode, onSelected: (ShoppingListSortMode) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(modifier = Modifier.padding(3.dp)) {
            ShoppingListSortMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Surface(
                    onClick = { onSelected(mode) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ) {
                    Text(
                        text = stringResource(if (mode == ShoppingListSortMode.MANUAL) R.string.shopping_list_sort_manual_short else mode.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
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

/**
 * Add/edit a shopping list line — one-line-first: the name field carries initial focus, a guess
 * line underneath infers category+unit from what's already in the household's inventory (see
 * [ShoppingListViewModel.guessFor]) so those two dropdowns stay collapsed until someone actually
 * wants to correct them, and a suggestion-chip row at the very bottom (create mode only) means
 * adding several items in a row doesn't cost several sheets — see the 2026-08 dialog review.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    guessFor: (String) -> Pair<Category, MeasurementUnit>? = { null },
    historySuggestions: List<String> = emptyList(),
    lowStockSuggestions: List<LowStockSuggestion> = emptyList(),
    onQuickAdd: (name: String, category: Category) -> Unit = { _, _ -> },
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
    // No guess line, and no "add another" chip row, once editing an item that already has an
    // explicit category/unit the household chose deliberately — both only make sense while
    // typing a brand new name.
    val isCreateMode = initialName.isBlank()

    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var store by remember { mutableStateOf(initialStore) }
    var quantity by remember { mutableIntStateOf(initialQuantity) }
    var note by remember { mutableStateOf(initialNote) }
    var unit by remember { mutableStateOf(initialUnit) }
    var detailsExpanded by remember { mutableStateOf(!isCreateMode) }
    var showAddStoreDialog by remember { mutableStateOf(false) }
    // Price still has no input in this form (removed on request) — this just carries whatever an
    // item already had straight through to onConfirm unchanged, so editing an item that has one
    // from before (e.g. from a receipt scan) doesn't silently wipe it.
    val priceText = initialPrice?.let { formatPrice(it).removePrefix("€") } ?: ""

    val guess = remember(name, isCreateMode) { if (isCreateMode) guessFor(name) else null }
    // The guess is applied, not just displayed — "merely confirmed, not asked" (see the dialog
    // review) — but only while the household hasn't already opened the details section to pick
    // something themselves; re-typing the name after that wouldn't silently overwrite a
    // deliberate correction.
    LaunchedEffect(guess) {
        if (guess != null && !detailsExpanded) {
            category = guess.first
            unit = guess.second
        }
    }

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

    fun confirm() {
        val price = priceText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
        onConfirm(name, category, store, quantity, note, unit, price)
    }

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetTitle(title = title)
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProductImage(
                    imageUrl = imageUrl,
                    fallbackIcon = category.icon,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(44.dp).padding(top = 2.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        placeholder = { Text(stringResource(R.string.common_name)) },
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (guess != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(start = 4.dp, top = 6.dp)
                                .clickable { detailsExpanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = stringResource(
                                    R.string.shopping_list_item_guess_format,
                                    stringResource(guess.first.displayNameRes),
                                    stringResource(guess.second.shortLabelRes),
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SheetEyebrow(text = stringResource(R.string.common_quantity))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    QuantityStepper(
                        quantity = quantity,
                        onDecrease = { quantity = (quantity - unit.step).coerceAtLeast(1) },
                        onIncrease = { quantity += unit.step },
                        minQuantity = 1,
                        displayText = formatQuantityWithUnit(quantity, unit),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Column {
                SheetEyebrow(text = stringResource(R.string.store_dropdown_label), modifier = Modifier.padding(bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SheetChip(label = stringResource(R.string.store_geen), selected = store.isBlank(), onClick = { store = "" })
                    }
                    items(stores) { entity ->
                        SheetChip(
                            label = entity.name,
                            selected = store == entity.name,
                            onClick = { store = entity.name },
                            leadingIcon = {
                                Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                            },
                        )
                    }
                    item {
                        SheetChip(
                            label = stringResource(R.string.store_add_menu_item),
                            selected = false,
                            onClick = { showAddStoreDialog = true },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                            },
                        )
                    }
                }
            }

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SoftCardShapeCompact)
                        .clickable { detailsExpanded = !detailsExpanded }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.shopping_list_item_details_toggle), style = MaterialTheme.typography.bodyLarge)
                    Icon(
                        imageVector = if (detailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (detailsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        CategoryDropdown(
                            selected = category,
                            onSelected = { category = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MeasurementUnitDropdown(
                            selected = unit,
                            onSelected = { unit = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(stringResource(R.string.shopping_list_note_label)) },
                            placeholder = { Text(stringResource(R.string.shopping_list_note_placeholder)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            SheetPrimaryButton(
                text = confirmLabel,
                enabled = name.isNotBlank(),
                onClick = ::confirm,
            )

            if (isCreateMode && (historySuggestions.isNotEmpty() || lowStockSuggestions.isNotEmpty())) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SheetEyebrow(text = stringResource(R.string.shopping_list_item_more_suggestions))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lowStockSuggestions) { suggestion ->
                        SheetChip(
                            label = suggestion.name,
                            selected = false,
                            onClick = { onQuickAdd(suggestion.name, suggestion.category) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                                )
                            },
                        )
                    }
                    items(historySuggestions) { suggestionName ->
                        SheetChip(
                            label = suggestionName,
                            selected = false,
                            onClick = { onQuickAdd(suggestionName, guessFor(suggestionName)?.first ?: Category.OVERIG) },
                        )
                    }
                }
            }
        }
    }

    if (showAddStoreDialog) {
        AddStoreDialog(
            onConfirm = { newStoreName ->
                onAddStore(newStoreName)
                store = newStoreName
                showAddStoreDialog = false
            },
            onDismiss = { showAddStoreDialog = false },
        )
    }
}

private val itemEditDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
private val itemEditTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/**
 * A dedicated edit-only sheet for an existing shopping list line (2026-08 design review) —
 * deliberately separate from [ItemFormDialog] rather than another mode of it: an existing item
 * has real attribution/history to show (who added it, when) and a name that's already settled
 * (tap the pencil to change it) rather than the one-line-first name field a brand-new item
 * starts on, plus a delete action and — back on request — a real editable price field, none of
 * which the create flow needs cluttered with. [shoppingItem]'s own [ShoppingListItemEntity.addedByName]/
 * [ShoppingListItemEntity.addedAt] back the attribution line; a pre-existing item written before
 * that field existed just falls back to the plain "Toegevoegd op" phrasing instead of a
 * fabricated name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditSheet(
    shoppingItem: ShoppingListItemEntity,
    stores: List<StoreEntity>,
    onAddStore: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
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
    var name by remember { mutableStateOf(shoppingItem.name) }
    var isEditingName by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(Category.fromStorageKey(shoppingItem.category)) }
    var store by remember { mutableStateOf(shoppingItem.store) }
    var quantity by remember { mutableIntStateOf(shoppingItem.quantity) }
    var unit by remember { mutableStateOf(MeasurementUnit.fromStorageKey(shoppingItem.unit)) }
    var priceText by remember { mutableStateOf(shoppingItem.price?.let { formatPrice(it).removePrefix("€") } ?: "") }
    var note by remember { mutableStateOf(shoppingItem.note ?: "") }
    var showAddStoreDialog by remember { mutableStateOf(false) }

    val addedZoned = remember(shoppingItem.addedAt) { Instant.ofEpochMilli(shoppingItem.addedAt).atZone(ZoneId.systemDefault()) }
    val today = remember { LocalDate.now() }
    val addedDay = when (addedZoned.toLocalDate()) {
        today -> stringResource(R.string.notifications_date_today)
        today.minusDays(1) -> stringResource(R.string.notifications_date_yesterday)
        else -> itemEditDayFormatter.format(addedZoned)
    }
    val addedTime = remember(shoppingItem.addedAt) { itemEditTimeFormatter.format(addedZoned) }
    val attributionText = shoppingItem.addedByName?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.shopping_list_item_added_by_format, it, addedDay, addedTime) }
        ?: stringResource(R.string.shopping_list_item_added_at_format, addedDay, addedTime)

    fun confirm() {
        val price = priceText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
        onConfirm(name, category, store, quantity, note, unit, price)
    }

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProductImage(
                    imageUrl = shoppingItem.imageUrl,
                    fallbackIcon = category.icon,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(48.dp).padding(top = 2.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = attributionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                IconButton(onClick = { isEditingName = !isEditingName }) {
                    Icon(
                        imageVector = if (isEditingName) Icons.Filled.Check else Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.shopping_list_item_edit_name_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SheetEyebrow(text = stringResource(R.string.common_quantity))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    QuantityStepper(
                        quantity = quantity,
                        onDecrease = { quantity = (quantity - unit.step).coerceAtLeast(1) },
                        onIncrease = { quantity += unit.step },
                        minQuantity = 1,
                        displayText = formatQuantityWithUnit(quantity, unit),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Column {
                SheetEyebrow(text = stringResource(R.string.store_dropdown_label), modifier = Modifier.padding(bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SheetChip(label = stringResource(R.string.store_geen), selected = store.isBlank(), onClick = { store = "" })
                    }
                    items(stores) { entity ->
                        SheetChip(
                            label = entity.name,
                            selected = store == entity.name,
                            onClick = { store = entity.name },
                            leadingIcon = {
                                Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                            },
                        )
                    }
                    item {
                        SheetChip(
                            label = stringResource(R.string.store_add_menu_item),
                            selected = false,
                            onClick = { showAddStoreDialog = true },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    SheetEyebrow(text = stringResource(R.string.category_dropdown_label), modifier = Modifier.padding(bottom = 4.dp))
                    CategoryDropdown(selected = category, onSelected = { category = it }, modifier = Modifier.fillMaxWidth())
                }
                Column(modifier = Modifier.weight(1f)) {
                    SheetEyebrow(text = stringResource(R.string.shopping_list_price_label), modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { input -> if (input.all { it.isDigit() || it == ',' || it == '.' }) priceText = input },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.shopping_list_price_placeholder)) },
                        prefix = { Text("€") },
                        suffix = {
                            Text(
                                text = stringResource(R.string.shopping_list_price_per_unit_format, stringResource(unit.shortLabelRes)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.shopping_list_note_label)) },
                placeholder = { Text(stringResource(R.string.shopping_list_note_placeholder)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shopping_list_delete_cd))
                }
                SheetPrimaryButton(
                    text = stringResource(R.string.shopping_list_save_confirm),
                    enabled = name.isNotBlank(),
                    onClick = ::confirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showAddStoreDialog) {
        AddStoreDialog(
            onConfirm = { newStoreName ->
                onAddStore(newStoreName)
                store = newStoreName
                showAddStoreDialog = false
            },
            onDismiss = { showAddStoreDialog = false },
        )
    }
}

/**
 * The list-switcher — every list the household has (default first, see
 * [ShoppingListViewModel.lists]), each with its own real item count (see
 * [ShoppingListViewModel.itemCountByListId]), a checkmark on the active one, and one full-width
 * "+ Nieuwe lijst" button at the bottom. A bottom sheet (2026-08 dialog review) rather than a
 * `DropdownMenu`, so item counts actually have room to show instead of being one more thing
 * squeezed into a cramped menu row. The default list has no rename/delete "…" menu (it isn't a
 * document [com.dtraas.homestock.data.repository.ShoppingListsRepository] manages, see
 * [ShoppingListMeta]'s doc) — only named lists get it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListSwitcherSheet(
    lists: List<ShoppingListMeta>,
    activeListId: String?,
    itemCountByListId: Map<String?, Int>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreateNew: () -> Unit,
    onRename: (ShoppingListMeta) -> Unit,
    onDelete: (ShoppingListMeta) -> Unit,
) {
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(title = stringResource(R.string.shopping_list_switch_list_cd))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lists.forEach { list ->
                    ShoppingListSwitcherRow(
                        list = list,
                        selected = list.id == activeListId,
                        itemCount = itemCountByListId[list.id] ?: 0,
                        onClick = { onSelect(list.id); onDismiss() },
                        onRename = { onDismiss(); onRename(list) },
                        onDelete = { onDismiss(); onDelete(list) },
                    )
                }
            }
            SheetPrimaryButton(
                text = stringResource(R.string.shopping_list_new_list_action),
                leadingIcon = Icons.Filled.Add,
                onClick = { onDismiss(); onCreateNew() },
            )
        }
    }
}

@Composable
private fun ShoppingListSwitcherRow(
    list: ShoppingListMeta,
    selected: Boolean,
    itemCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SoftCardShapeCompact,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(list.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = pluralStringResource(R.plurals.shopping_list_switcher_item_count_format, itemCount, itemCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (list.id != null) {
                var itemMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { itemMenuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.shopping_list_list_options_cd))
                    }
                    DropdownMenu(expanded = itemMenuExpanded, onDismissRequest = { itemMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_list_rename_list_action)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { itemMenuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_list_delete_list_action)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { itemMenuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** Shared by "nieuwe lijst" and "lijst hernoemen" — same single-field form either way. A bottom
 *  sheet (2026-08 dialog review) rather than an `AlertDialog`, with a row of common list-name
 *  suggestions underneath the field — tapping one just fills the field, it's still freely
 *  editable afterward, same as picking a suggestion anywhere else in the app never locks it in. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(title = title)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listNameSuggestionRes.forEach { suggestionRes ->
                    val suggestion = stringResource(suggestionRes)
                    SheetChip(label = suggestion, selected = name == suggestion, onClick = { name = suggestion })
                }
            }
            SheetPrimaryButton(text = confirmLabel, onClick = { onConfirm(name) }, enabled = name.isNotBlank())
        }
    }
}

private val listNameSuggestionRes = listOf(
    R.string.shopping_list_name_suggestion_weekly,
    R.string.shopping_list_name_suggestion_party,
    R.string.shopping_list_name_suggestion_bbq,
    R.string.shopping_list_name_suggestion_birthday,
    R.string.shopping_list_name_suggestion_camping,
)

