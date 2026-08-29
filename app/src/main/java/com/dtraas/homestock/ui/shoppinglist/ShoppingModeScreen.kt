package com.dtraas.homestock.ui.shoppinglist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.ShoppingListItemEntity
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.ui.components.formatQuantityWithUnit
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.util.Locale

/** €-formatted total, e.g. "€26,15" — same shape as the rest of this package's [formatPrice]
 *  (private to that file), duplicated rather than exported since it's a one-line format call. */
private fun formatModePrice(value: Double): String = String.format(Locale.getDefault(), "€%.2f", value)

/**
 * Full-screen "Winkelmodus" — one store's still-open items at a time, grouped by category
 * (walking order through that store's own aisles — see [StoreEntity.aisleOrder][com.dtraas.homestock.data.local.entity.StoreEntity.aisleOrder]),
 * with a live spend summary and a screen-stays-on toggle so a phone in a cart's pocket doesn't
 * lock mid-aisle. Reached from ShoppingListScreen's bottom bar; instantiates its own
 * [ShoppingListViewModel] the same way [CookModeScreen][com.dtraas.homestock.ui.recipes.CookModeScreen]
 * gets its own [CookModeViewModel][com.dtraas.homestock.ui.recipes.CookModeViewModel] rather than
 * sharing the caller's instance — the underlying data (Firestore-backed repositories) is the same
 * singleton either way, so checking an item here shows up back on ShoppingListScreen immediately.
 *
 * [listId] pre-selects which list to shop (the one the household was already looking at), and
 * [initialStoreName] pre-selects which of that list's stores — both per
 * [Destination.ShoppingMode][com.dtraas.homestock.ui.navigation.Destination.ShoppingMode]'s own
 * doc for how their "nothing selected" sentinels round-trip through the nav arguments. A list
 * with items in more than one store and no valid pre-selection shows [ShoppingModePicker] first
 * — on explicit request, this screen only ever shows one store's worth of items at a time, never
 * several mixed together the way the old design's optional store-header briefly did.
 *
 * "Klaar met winkelen" just closes back to the list — items stay checked exactly as tapped here,
 * so there's nothing destructive to confirm; clearing them off the list entirely is still its own
 * separate action in ShoppingListScreen's meer-opties, same as before this screen existed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingModeScreen(listId: String?, initialStoreName: String?, onClose: () -> Unit) {
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
    // Only needs to run once, on entry — selectList and onSortModeChange are both plain state
    // writes, not reactive subscriptions, so there's nothing to keep re-applying on every
    // recomposition. Forcing AISLE here — regardless of whatever sort mode ShoppingListScreen's
    // own (separate) ShoppingListViewModel instance happens to be in — is what makes
    // [groupedByStore] below already come back category-ordered per store; this screen has no
    // sort-mode toggle of its own, walking the aisles is the entire point of being here.
    LaunchedEffect(listId) {
        viewModel.selectList(listId)
        viewModel.onSortModeChange(ShoppingListSortMode.AISLE)
    }

    val groupedByStore by viewModel.groupedByStore.collectAsState()
    val storeNames = remember(groupedByStore) { groupedByStore.keys.toList() }
    val knownStores by viewModel.stores.collectAsState()

    // Only ever written when the household actually taps a store in the picker (or "wissel van
    // winkel" resets it back to null) — never auto-populated for the single-store case, so
    // there's no reset-vs-keep tension with groupedByStore's own live updates. selectedStore is
    // only trusted below when it's still a real key in groupedByStore (a pre-selection or a
    // prior pick can go stale if that store's last item gets removed while this screen is open).
    var selectedStore by rememberSaveable { mutableStateOf(initialStoreName) }
    val effectiveStore = selectedStore?.takeIf { it in groupedByStore.keys } ?: storeNames.singleOrNull()

    if (storeNames.isEmpty()) {
        ShoppingModeEmptyScreen(onClose = onClose)
    } else if (effectiveStore == null) {
        ShoppingModePicker(
            // Nog te kopen, niet totaal — dat is wat er telt bij "welke winkel moet ik nog
            // langs", en een winkel waarvan alles al is afgevinkt hoeft niet op te vallen als
            // "veel items".
            storeCounts = groupedByStore.mapValues { (_, items) -> items.count { !it.isChecked } },
            onSelectStore = { selectedStore = it },
            onClose = onClose,
        )
    } else {
        ShoppingModeStoreScreen(
            storeName = effectiveStore,
            store = knownStores.firstOrNull { it.name == effectiveStore },
            items = groupedByStore.getValue(effectiveStore),
            onCheckedChange = { itemId, checked -> viewModel.setChecked(itemId, checked) },
            // Alleen aanbieden om te wisselen als er ook daadwerkelijk iets te wisselen valt.
            onSwitchStore = if (storeNames.size > 1) { { selectedStore = null } } else null,
            onClose = onClose,
        )
    }
}

/** Empty state for a list with nothing (open or checked) on it at all — nothing to pick a store
 *  for either, so this skips [ShoppingModePicker] entirely. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingModeEmptyScreen(onClose: () -> Unit) {
    val contentColor = LocalTopAppBarContentColor.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalTopAppBarContainerColor.current)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.shopping_mode_close_cd), tint = contentColor)
                }
                Text(
                    text = stringResource(R.string.shopping_mode_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.shopping_mode_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }
}

/** "Welke winkel ga je nu langs?" — every store this list has open items in, each with its own
 *  open-item count, tap to start shopping just that one. Shown whenever a list has items in more
 *  than one store and none is (still validly) pre-selected — see [ShoppingModeScreen]'s own doc. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingModePicker(storeCounts: Map<String, Int>, onSelectStore: (String) -> Unit, onClose: () -> Unit) {
    val contentColor = LocalTopAppBarContentColor.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalTopAppBarContainerColor.current)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .padding(bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.shopping_mode_close_cd), tint = contentColor)
                    }
                    Text(
                        text = stringResource(R.string.shopping_mode_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                }
                Text(
                    text = stringResource(R.string.shopping_mode_picker_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnTopAppBarContainerAccent,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(storeCounts.entries.toList(), key = { it.key }) { (storeName, count) ->
                    Surface(
                        onClick = { onSelectStore(storeName) },
                        shape = SoftCardShapeCompact,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(
                                    text = storeName.ifBlank { stringResource(R.string.store_geen) },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.shopping_list_item_count_format, count, count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/** The actual shopping-through-one-store view — everything [ShoppingModeScreen] used to render
 *  directly before it needed a picker step in front of it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingModeStoreScreen(
    storeName: String,
    store: StoreEntity?,
    items: List<ShoppingListItemEntity>,
    onCheckedChange: (itemId: String, checked: Boolean) -> Unit,
    onSwitchStore: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val checkedCount = items.count { it.isChecked }
    val totalCount = items.size
    val plannedTotal = remember(items) {
        items.mapNotNull { item -> item.price?.let { it * item.quantity } }.takeIf { it.isNotEmpty() }?.sum()
    }
    val inCartTotal = remember(items) {
        items.filter { it.isChecked }.mapNotNull { item -> item.price?.let { it * item.quantity } }
            .takeIf { it.isNotEmpty() }?.sum()
    }
    // Category (aisle) groups, in this store's own gangvolgorde — items is already sorted by
    // exactly that order (AISLE sort mode, forced on in ShoppingModeScreen), so grouping it here
    // is just bucketing consecutive same-category runs; Kotlin's groupBy preserves first-seen
    // key order, so no separate rank lookup is needed to get the *grouping* right on this side.
    val categoriesInStore = remember(items) { items.groupBy { Category.fromStorageKey(it.category) } }
    // A rank lookup is still needed for the *displayed* aisle number, though — a path with more
    // than one category (see StoreEntity.aislePaths) must show the same "gang N" for each of
    // them, not a fresh number per category the way a flat index would.
    val rankByCategory = remember(store) { categoryRankFor(store) }
    var checkedSectionExpanded by rememberSaveable { mutableStateOf(false) }

    // On for the whole time this screen is open by default (a shopping trip is exactly the
    // "hands full, phone in a cart's pocket" scenario a screen timeout is most annoying in,
    // same reasoning as CookModeScreen) — but unlike that screen, toggleable here, since a
    // household member standing still comparing prices for a while might prefer to save battery.
    val view = LocalView.current
    var keepScreenOn by rememberSaveable { mutableStateOf(true) }
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ShoppingModeHeader(
                storeName = storeName,
                onSwitchStore = onSwitchStore,
                checkedCount = checkedCount,
                totalCount = totalCount,
                plannedTotal = plannedTotal,
                inCartTotal = inCartTotal,
                keepScreenOn = keepScreenOn,
                onToggleKeepScreenOn = { keepScreenOn = !keepScreenOn },
                onClose = onClose,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Consecutive categories sharing one path (see rankByCategory above) show
                    // the same aisle number instead of each bumping it — only a genuine rank
                    // change advances the count, so a merged "Zuivel + Kaas" path still reads as
                    // one "gang N", not two.
                    var aisleNumber = 0
                    var previousRank: Int? = null
                    categoriesInStore.entries.forEach { (category, itemsInCategory) ->
                        val unchecked = itemsInCategory.filterNot { it.isChecked }
                        if (unchecked.isEmpty()) return@forEach
                        val rank = rankByCategory[category]
                        if (rank != previousRank) {
                            aisleNumber += 1
                            previousRank = rank
                        }
                        // Captured into a val before the item{} lambda — that lambda isn't run
                        // immediately (LazyListScope defers it to actual composition, after this
                        // whole forEach has already finished), so closing over the `var` itself
                        // would have every header read aisleNumber's *final* value instead of
                        // the one at this point in the loop — exactly the "every aisle says the
                        // same number" bug this fixes.
                        val displayedAisleNumber = aisleNumber
                        item(key = "header_${category.storageKey}") {
                            ShoppingModeCategoryHeader(
                                category = category,
                                aisleNumber = displayedAisleNumber,
                                itemCount = itemsInCategory.size,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(unchecked, key = { it.id }) { item ->
                            ShoppingModeItemRow(item = item, onCheckedChange = { checked -> onCheckedChange(item.id, checked) })
                        }
                    }
                    val checkedItems = items.filter { it.isChecked }
                    if (checkedItems.isNotEmpty()) {
                        item(key = "checked_toggle") {
                            CheckedSectionDivider(
                                count = checkedItems.size,
                                expanded = checkedSectionExpanded,
                                onClick = { checkedSectionExpanded = !checkedSectionExpanded },
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        if (checkedSectionExpanded) {
                            items(checkedItems, key = { "checked_${it.id}" }) { item ->
                                ShoppingModeItemRow(item = item, onCheckedChange = { checked -> onCheckedChange(item.id, checked) })
                            }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onClose,
                    shape = SoftCardShapeCompact,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.shopping_mode_done_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** The fixed green gradient header: close + title/store name (+ "wissel van winkel" when there's
 *  more than one to shop) + screen-on toggle on the top row, then a progress bar with the
 *  checked/total count, then the planned-vs-in-cart spend summary — same dark-green/white/coral
 *  palette as [ShoppingListHeader], just with more on it. */
@Composable
private fun ShoppingModeHeader(
    storeName: String,
    onSwitchStore: (() -> Unit)?,
    checkedCount: Int,
    totalCount: Int,
    plannedTotal: Double?,
    inCartTotal: Double?,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    onClose: () -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.shopping_mode_close_cd), tint = contentColor)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.shopping_mode_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = storeName.ifBlank { stringResource(R.string.store_geen) },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnTopAppBarContainerAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (onSwitchStore != null) {
                        Text(
                            text = stringResource(R.string.shopping_mode_switch_store),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable(onClick = onSwitchStore),
                        )
                    }
                }
            }
            Surface(
                onClick = onToggleKeepScreenOn,
                shape = CircleShape,
                color = if (keepScreenOn) OnTopAppBarContainerAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LightMode,
                        contentDescription = stringResource(R.string.shopping_mode_screen_on_cd),
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            if (keepScreenOn) R.string.shopping_mode_screen_on_state_on else R.string.shopping_mode_screen_on_state_off,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = plannedTotal?.let { stringResource(R.string.shopping_mode_planned_format, formatModePrice(it)) } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = OnTopAppBarContainerAccent,
            )
            Text(
                text = "$checkedCount/$totalCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
        LinearProgressIndicator(
            progress = { if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(percent = 50)),
            color = OnTopAppBarContainerAccent,
            trackColor = Color.White.copy(alpha = 0.18f),
        )
        if (inCartTotal != null) {
            Text(
                text = stringResource(R.string.shopping_mode_in_cart_format, formatModePrice(inCartTotal)),
                style = MaterialTheme.typography.bodyMedium,
                color = OnTopAppBarContainerAccent,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** One aisle's section header: category icon + name, "gang N · M items" trailing. */
@Composable
private fun ShoppingModeCategoryHeader(category: Category, aisleNumber: Int, itemCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(category.displayNameRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(
            text = stringResource(
                R.string.shopping_mode_aisle_label_format,
                aisleNumber,
                pluralStringResource(R.plurals.shopping_list_item_count_format, itemCount, itemCount),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One item row: a big rounded checkbox, name + subtitle (note, else price), trailing quantity —
 *  larger touch targets than [ShoppingListRow]'s own compact card, since this screen is meant to
 *  be operated one-handed while pushing a cart, not browsed at a desk. */
@Composable
private fun ShoppingModeItemRow(item: ShoppingListItemEntity, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SoftCardShapeCompact)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onCheckedChange(!item.isChecked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShoppingModeCheckbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            // bodyLarge, not titleSmall — a product name is free text a household typed in, not
            // a heading, and can run long ("Halfvolle yoghurt drinkyoghurt framboos 1 liter").
            // Type.kt's own Baloo 2 (titleX) is meant for short display text, "never for long
            // text"; ShoppingListRow's equivalent name already uses the Nunito (bodyX) family for
            // exactly that reason, so this stayed inconsistent with it — titleSmall here rendered
            // every product name in the chunky display face regardless of length. Kept Bold and
            // sized up from ShoppingListRow's own bodyMedium, matching this screen's own
            // larger-touch-target reasoning above, without switching typeface families.
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = item.note?.takeIf { it.isNotBlank() } ?: item.price?.let(::formatModePrice)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatQuantityWithUnit(item.quantity, MeasurementUnit.fromStorageKey(item.unit)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Rounded-square checkbox (rather than [ShoppingListScreen]'s own circular one) — a deliberate
 *  visual distinction for this screen's larger, thumb-first rows. */
@Composable
private fun ShoppingModeCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = BorderStroke(
            2.dp,
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.size(28.dp).clickable { onCheckedChange(!checked) },
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** "N in de wagen" collapsed-section row with a divider line on either side — centered, same
 *  layout family as [CheckedSectionToggle] in ShoppingListScreen (kept separate rather than
 *  shared since that one is file-private and this screen's version has no store context). */
@Composable
private fun CheckedSectionDivider(count: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(R.string.shopping_list_in_cart_format, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).padding(start = 4.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
