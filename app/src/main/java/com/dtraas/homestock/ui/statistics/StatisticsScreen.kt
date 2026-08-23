package com.dtraas.homestock.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.TopWastedProduct
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.MonthlyWaste
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.theme.CoralSecondaryDark
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimaryDark
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onNavigateToExpiringSoon: () -> Unit = {},
    onNavigateToLowStock: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: StatisticsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StatisticsViewModel(
                    application.container.statisticsRepository,
                    application.container.householdMembersRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        // StatisticsHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // "Verspilling per maand" — eyebrow, €-waarde, delta en de 6-maands-balkjes — zat
            // eerst in een los gekleurde Card bovenaan de scrollende lijst; die verhuist hier
            // naar een echte, vaste groene gradient-header die vanaf de statusbalk doorloopt,
            // met de terugknop/titel-rij erin, per de Claude Design review (artboard 1g).
            StatisticsHeader(
                onBack = onBack,
                monthlyWaste = uiState.monthlyWaste,
                deltaPercent = uiState.wasteDeltaPercent,
                locale = locale,
            )
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    StatusTilesRow(
                        expiringSoonCount = uiState.expiringSoonCount,
                        lowStockCount = uiState.lowStockCount,
                        totalInInventory = uiState.totalInInventory,
                        onExpiringSoonClick = onNavigateToExpiringSoon,
                        onLowStockClick = onNavigateToLowStock,
                        onInStockClick = onNavigateToInventory,
                    )
                }

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
                    item { WasteInsightCard(topProduct = uiState.topWastedProducts.first()) }
                }

                item {
                    Text(stringResource(R.string.statistics_by_actor), style = MaterialTheme.typography.titleMedium)
                }
                if (uiState.memberScans.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.statistics_no_scans_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item { MemberScanRow(uiState.memberScans) }
                }
            }
        }
    }
}

private fun formatPrice(value: Double): String = String.format(Locale.getDefault(), "€%.2f", value)

/**
 * The fixed (non-scrolling) green gradient header — back button + title row, then the screen's
 * headline metric: this month's total approximate waste value, a month-over-month delta, and a
 * 6-bar chart giving the number visible context. Used to be a flat HomeStockTopAppBar with a
 * separately-colored WasteHeroCard underneath, on plain white background; "Verspilling per maand
 * bovenaan moet opgenomen worden in groene header" per the Claude Design review folds both into
 * one true gradient header extending from the status bar (artboard 1g in the uploaded mockup).
 * The chart itself is still the period selector — there's nothing to pick, it's always "the last
 * 6 months".
 */
@Composable
private fun StatisticsHeader(
    onBack: () -> Unit,
    monthlyWaste: List<MonthlyWaste>,
    deltaPercent: Int?,
    locale: Locale,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = contentColor)
            }
            Text(
                text = stringResource(R.string.statistics_title),
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
            )
        }
        val currentMonth = monthlyWaste.lastOrNull()
        val monthName = currentMonth?.month?.let {
            DateTimeFormatter.ofPattern("MMMM", locale).format(it.atDay(1))
        }.orEmpty()
        Text(
            text = stringResource(R.string.statistics_hero_eyebrow_format, monthName.uppercase(locale)),
            style = MaterialTheme.typography.labelSmall,
            color = OnTopAppBarContainerAccent,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = formatPrice(currentMonth?.totalValue ?: 0.0),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (deltaPercent != null) {
            val previousMonthName = monthlyWaste.getOrNull(monthlyWaste.size - 2)?.month?.let {
                DateTimeFormatter.ofPattern("MMMM", locale).format(it.atDay(1))
            }.orEmpty()
            val arrow = if (deltaPercent <= 0) "▼" else "▲"
            Text(
                text = stringResource(
                    R.string.product_detail_stat_price_delta_format,
                    "$arrow ${abs(deltaPercent)}%",
                    previousMonthName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = SageGreenPrimaryDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (monthlyWaste.isNotEmpty()) {
            MiniBarChart(monthlyWaste = monthlyWaste, locale = locale, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun MiniBarChart(monthlyWaste: List<MonthlyWaste>, locale: Locale, modifier: Modifier = Modifier) {
    val maxValue = monthlyWaste.maxOf { it.totalValue }.coerceAtLeast(0.01)
    Row(
        modifier = modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        monthlyWaste.forEachIndexed { index, point ->
            val isCurrent = index == monthlyWaste.lastIndex
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .fillMaxHeight((point.totalValue / maxValue).toFloat().coerceIn(0.06f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (isCurrent) CoralSecondaryDark else Color.White.copy(alpha = 0.22f)),
                )
                Text(
                    text = DateTimeFormatter.ofPattern("MMM", locale).format(point.month.atDay(1)),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnTopAppBarContainerAccent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Three tiles instead of the old seven-equal-stat-cards summary — [expiringSoonCount] and
 * [lowStockCount] are actionable ("go check these"), [totalInInventory] is a plain fact, and
 * each tile navigates to Voorraad with that same filter already applied (see
 * InventoryScreen's showExpiringSoonOnOpen/showLowStockOnOpen).
 */
@Composable
private fun StatusTilesRow(
    expiringSoonCount: Int,
    lowStockCount: Int,
    totalInInventory: Int,
    onExpiringSoonClick: () -> Unit,
    onLowStockClick: () -> Unit,
    onInStockClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusTile(
            count = expiringSoonCount,
            label = stringResource(R.string.statistics_expiring_soon),
            icon = Icons.Filled.EventBusy,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onExpiringSoonClick,
            modifier = Modifier.weight(1f),
        )
        StatusTile(
            count = lowStockCount,
            label = stringResource(R.string.statistics_low_stock),
            icon = Icons.Filled.TrendingDown,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onLowStockClick,
            modifier = Modifier.weight(1f),
        )
        StatusTile(
            count = totalInInventory,
            label = stringResource(R.string.statistics_in_stock),
            icon = Icons.Filled.Inventory2,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onInStockClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusTile(
    count: Int,
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = SoftCardShapeCompact,
        color = containerColor,
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

/** Mirrors the old "Meest verspild" row, but the trailing count is now "N× · €X,XX" — the
 *  euro figure is the reason this list matters at all now that the hero above puts a price on
 *  waste, not just a tally. */
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
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(24.dp),
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
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = stringResource(R.string.statistics_wasted_row_format, product.wastedCount, formatPrice(product.wastedValue)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A single concrete suggestion, derived from whichever product tops the waste ranking — not a
 *  generic tip, so it changes as the household's own habits do. */
@Composable
private fun WasteInsightCard(topProduct: TopWastedProduct) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    R.string.statistics_insight_format,
                    stringResource(Category.fromStorageKey(topProduct.category).displayNameRes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** "Wie doet wat" — one card per household member who's scanned at least once, avatar (photo or
 *  a generic icon when no match was found — see [MemberScanEntry]'s doc) plus their scan count. */
@Composable
private fun MemberScanRow(members: List<MemberScanEntry>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        members.forEach { member ->
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = SoftCardShapeCompact,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (member.photoUrl != null) {
                                AsyncImage(
                                    model = member.photoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Text(
                        text = member.name.ifBlank { stringResource(R.string.activity_actor_unknown) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.statistics_scan_count_format, member.scanCount),
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
