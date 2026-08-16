package com.dtraas.homestock.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.ActorScanCount
import com.dtraas.homestock.data.local.dao.TopScannedProduct
import com.dtraas.homestock.data.local.dao.TopWastedProduct
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: StatisticsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StatisticsViewModel(application.container.statisticsRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                TimeRangeToggle(selected = uiState.timeRange, onSelect = viewModel::onTimeRangeChange)
            }

            // Voorraadgezondheid — always current, not tied to the time-range toggle above
            // (the toggle only affects "how far back" activity counts look; expiring/low-stock
            // items are a snapshot of right now regardless of that setting).
            item {
                Text(stringResource(R.string.statistics_section_health), style = MaterialTheme.typography.titleMedium)
            }
            item { InventoryHealthRow(expiringSoonCount = uiState.expiringSoonCount, lowStockCount = uiState.lowStockCount) }

            item { SummaryRow(uiState) }

            item {
                Text(stringResource(R.string.statistics_section_range_activity), style = MaterialTheme.typography.titleMedium)
            }
            item { RangeActivityRow(removedInRange = uiState.removedInRange, addedToListInRange = uiState.addedToShoppingListInRange) }

            uiState.busiestWeekday?.let { day ->
                item { BusiestDayCard(day) }
            }

            item {
                Text(stringResource(R.string.statistics_most_scanned), style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.topScannedProducts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.statistics_no_scans_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val maxScanCount = uiState.topScannedProducts.maxOf { it.scanCount }
                itemsIndexed(uiState.topScannedProducts, key = { _, product -> product.barcode }) { index, product ->
                    TopScannedRow(rank = index + 1, product = product, maxCount = maxScanCount)
                }
            }

            // "Meest herhaald gekocht" is answered by "Meest gescand" above — in this app,
            // scanning is how a product gets restocked, so a high scan count already means
            // it's bought over and over. "Meest verspild" below is the genuinely new stat.
            item {
                Text(stringResource(R.string.statistics_most_wasted), style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.topWastedProducts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.statistics_no_waste_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val maxWastedCount = uiState.topWastedProducts.maxOf { it.wastedCount }
                itemsIndexed(uiState.topWastedProducts, key = { _, product -> product.barcode }) { index, product ->
                    TopWastedRow(rank = index + 1, product = product, maxCount = maxWastedCount)
                }
            }

            item {
                Text(stringResource(R.string.statistics_category_distribution), style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.categoryDistribution.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.statistics_no_products_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { CategoryDistributionCard(uiState.categoryDistribution) }
            }

            item {
                Text(stringResource(R.string.statistics_by_actor), style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.scansByActor.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.statistics_no_scans_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val maxActorScanCount = uiState.scansByActor.maxOf { it.scanCount }
                items(uiState.scansByActor, key = { it.actorName ?: "" }) { actorCount ->
                    ActorScanRow(actorCount = actorCount, maxCount = maxActorScanCount)
                }
            }
        }
    }
}

@Composable
private fun TimeRangeToggle(selected: StatisticsTimeRange, onSelect: (StatisticsTimeRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatisticsTimeRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(stringResource(range.labelRes)) },
            )
        }
    }
}

// Used to show total scans and "scans this week/month" here — dropped in favor of category
// count and favorites count, which say more about what's actually in the household's
// voorraad than a raw scan total and a time-boxed count with no comparison point ever could.
@Composable
private fun SummaryRow(uiState: StatisticsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Filled.Inventory2,
            value = uiState.totalInInventory.toString(),
            label = stringResource(R.string.statistics_in_stock),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Category,
            value = uiState.categoryDistribution.size.toString(),
            label = stringResource(R.string.statistics_categories),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Star,
            value = uiState.favoritesCount.toString(),
            label = stringResource(R.string.statistics_favorites),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A snapshot of current stock, not tied to the time-range toggle above — expiring or
 * low-stock items are true right now regardless of which "since" window is selected.
 * Both counts use the theme's error color once they're above zero, since both are
 * actionable ("go check the fridge" / "add these to your list") rather than neutral facts.
 */
@Composable
private fun InventoryHealthRow(expiringSoonCount: Int, lowStockCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Filled.EventBusy,
            value = expiringSoonCount.toString(),
            label = stringResource(R.string.statistics_expiring_soon),
            accentColor = if (expiringSoonCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.TrendingDown,
            value = lowStockCount.toString(),
            label = stringResource(R.string.statistics_low_stock),
            accentColor = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Two more activity counts for the selected time range, alongside "scans" in [SummaryRow]. */
@Composable
private fun RangeActivityRow(removedInRange: Int, addedToListInRange: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Filled.RemoveShoppingCart,
            value = removedInRange.toString(),
            label = stringResource(R.string.statistics_removed),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.PlaylistAddCheck,
            value = addedToListInRange.toString(),
            label = stringResource(R.string.statistics_added_to_list),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BusiestDayCard(day: DayOfWeek) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    R.string.statistics_busiest_day_format,
                    day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        .replaceFirstChar { it.uppercase(Locale.getDefault()) },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun TopScannedRow(rank: Int, product: TopScannedProduct, maxCount: Int) {
    val category = Category.fromStorageKey(product.category)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(28.dp),
            )
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProportionalBar(
                    fraction = product.scanCount.toFloat() / maxCount.toFloat(),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = stringResource(R.string.statistics_scan_count_format, product.scanCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Mirrors [TopScannedRow]'s layout, but tinted toward the error color — this list is a
 *  "you might want to fix this" ranking, not a neutral usage stat. */
@Composable
private fun TopWastedRow(rank: Int, product: TopWastedProduct, maxCount: Int) {
    val category = Category.fromStorageKey(product.category)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.width(28.dp),
            )
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProportionalBar(
                    fraction = product.wastedCount.toFloat() / maxCount.toFloat(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.statistics_wasted_count_format, product.wastedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun ActorScanRow(actorCount: ActorScanCount, maxCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = actorCount.actorName ?: stringResource(R.string.activity_actor_unknown),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProportionalBar(
                    fraction = actorCount.scanCount.toFloat() / maxCount.toFloat(),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = stringResource(R.string.statistics_scan_count_format, actorCount.scanCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryDistributionCard(distribution: List<Pair<Category, Int>>) {
    val maxCount = distribution.maxOf { it.second }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            distribution.forEach { (category, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(stringResource(category.displayNameRes), style = MaterialTheme.typography.bodySmall)
                        ProportionalBar(
                            fraction = count.toFloat() / maxCount.toFloat(),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProportionalBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

