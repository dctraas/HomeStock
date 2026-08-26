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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
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
 * Full-screen "Winkelmodus" — the active list's still-open items grouped by category (walking
 * order through a store's aisles, not by the store field ShoppingListScreen groups by), with
 * a live spend summary and a screen-stays-on toggle so a phone in a cart's pocket doesn't lock
 * mid-aisle. Reached from ShoppingListScreen's bottom bar; instantiates its own
 * [ShoppingListViewModel] the same way [CookModeScreen][com.dtraas.homestock.ui.recipes.CookModeScreen]
 * gets its own [CookModeViewModel][com.dtraas.homestock.ui.recipes.CookModeViewModel] rather than
 * sharing the caller's instance — the underlying data (Firestore-backed repositories) is the same
 * singleton either way, so checking an item here shows up back on ShoppingListScreen immediately.
 * [listId] pre-selects which list to shop (the one the household was already looking at) —
 * see [Destination.ShoppingMode][com.dtraas.homestock.ui.navigation.Destination.ShoppingMode] for
 * how the empty-string "no list" sentinel round-trips through the nav argument.
 *
 * "Klaar met winkelen" just closes back to the list — items stay checked exactly as tapped here,
 * so there's nothing destructive to confirm; clearing them off the list entirely is still its own
 * separate action in ShoppingListScreen's meer-opties, same as before this screen existed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingModeScreen(listId: String?, onClose: () -> Unit) {
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

    val activeList by viewModel.activeList.collectAsState()
    val groupedByStore by viewModel.groupedByStore.collectAsState()
    val allItems = remember(groupedByStore) { groupedByStore.values.flatten() }
    val checkedCount = allItems.count { it.isChecked }
    val totalCount = allItems.size
    val plannedTotal = remember(allItems) {
        allItems.mapNotNull { item -> item.price?.let { it * item.quantity } }.takeIf { it.isNotEmpty() }?.sum()
    }
    val inCartTotal = remember(allItems) {
        allItems.filter { it.isChecked }.mapNotNull { item -> item.price?.let { it * item.quantity } }
            .takeIf { it.isNotEmpty() }?.sum()
    }

    // Store first, then category (aisle) within each store — the household's own per-store
    // gangvolgorde (see StoreEntity.aisleOrder), not Category's fixed sortOrder. groupedByStore
    // is already both store-grouped AND, thanks to forcing AISLE sort mode above, sorted within
    // each store by exactly that per-store order — re-grouping each store's already-sorted list
    // by category here is then just bucketing consecutive same-category runs, in the order
    // they're already in (Kotlin's groupBy preserves first-seen key order), no separate rank
    // lookup needed on this side at all.
    val groupedByStoreThenCategory = remember(groupedByStore) {
        groupedByStore.mapValues { (_, itemsInStore) -> itemsInStore.groupBy { Category.fromStorageKey(it.category) } }
    }
    // Only worth a store-level header when there's more than one store's worth of items on this
    // trip — a single-store list (the common case) goes straight to category headers, since
    // "Albert Heijn" repeated on every screen it's the only store on would just be noise.
    val showStoreHeaders = groupedByStore.size > 1
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
                // The one store this trip is actually for, when there's only one — matches the
                // per-store gangvolgorde these rows are already sorted by; falls back to the
                // list's own name once more than one store is involved, since there's no single
                // store left to name here.
                listName = groupedByStore.keys.filter { it.isNotBlank() }.singleOrNull() ?: activeList.name,
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
                if (allItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.shopping_mode_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        groupedByStoreThenCategory.forEach { (storeName, categoriesInStore) ->
                            if (categoriesInStore.values.all { it.all { item -> item.isChecked } }) return@forEach
                            if (showStoreHeaders) {
                                item(key = "store_${storeName}") {
                                    ShoppingModeStoreHeader(storeName = storeName, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                }
                            }
                            categoriesInStore.entries.forEachIndexed { index, (category, itemsInCategory) ->
                                val unchecked = itemsInCategory.filterNot { it.isChecked }
                                if (unchecked.isEmpty()) return@forEachIndexed
                                item(key = "header_${storeName}_${category.storageKey}") {
                                    ShoppingModeCategoryHeader(
                                        category = category,
                                        aisleNumber = index + 1,
                                        itemCount = itemsInCategory.size,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }
                                items(unchecked, key = { it.id }) { item ->
                                    ShoppingModeItemRow(item = item, onCheckedChange = { checked -> viewModel.setChecked(item.id, checked) })
                                }
                            }
                        }
                        val checkedItems = allItems.filter { it.isChecked }
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
                                    ShoppingModeItemRow(item = item, onCheckedChange = { checked -> viewModel.setChecked(item.id, checked) })
                                }
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

/** The fixed green gradient header: close + title/list-name + screen-on toggle on the top row,
 *  then a progress bar with the checked/total count, then the planned-vs-in-cart spend summary —
 *  same dark-green/white/coral palette as [ShoppingListHeader], just with more on it. */
@Composable
private fun ShoppingModeHeader(
    listName: String,
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
                Text(
                    text = listName,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnTopAppBarContainerAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

/** Store name, bold — only shown at all once this trip spans more than one store (see
 *  [ShoppingModeScreen]'s own `showStoreHeaders`); each store's own aisle groups follow
 *  underneath, gang-numbered from 1 again per store. A local, simpler cousin of
 *  [ShoppingListScreen]'s file-private `StoreHeader` (icon + live item count) — this one has no
 *  count of its own to show, since the aisle headers underneath already carry that per category. */
@Composable
private fun ShoppingModeStoreHeader(storeName: String, modifier: Modifier = Modifier) {
    Text(
        text = storeName.ifBlank { stringResource(R.string.store_geen) },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

/** One aisle's section header: category icon + name, "gang N · M items" trailing — the walking-
 *  order equivalent of [ShoppingModeStoreHeader] one axis over (category instead of store). */
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
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
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
