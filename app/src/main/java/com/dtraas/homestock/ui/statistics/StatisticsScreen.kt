package com.dtraas.homestock.ui.statistics

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.local.dao.TopScannedProduct
import com.dtraas.homestock.data.local.dao.TopWastedProduct
import com.dtraas.homestock.data.model.ActivityType
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.MonthlyWaste
import com.dtraas.homestock.data.repository.YearlyWaste
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.theme.CoralSecondaryDark
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onNavigateToExpiringSoon: () -> Unit = {},
    // Kept for HomeStockNavHost call-site compatibility — the redesigned tile row (per the
    // Claude Design mockup) no longer has a dedicated low-stock tile, "Bespaard" took its place.
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
                    application.container.householdRepository,
                    application.container.activityLogRepository,
                    application.container.householdSession,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    var activityShareExpanded by rememberSaveable { mutableStateOf(false) }
    var scannedHistoryExpanded by rememberSaveable { mutableStateOf(false) }

    if (showGoalDialog) {
        GoalEditDialog(
            currentGoal = uiState.wasteGoal,
            onDismiss = { showGoalDialog = false },
            onSave = { goal -> viewModel.setWasteGoal(goal); showGoalDialog = false },
            onClear = { viewModel.setWasteGoal(null); showGoalDialog = false },
        )
    }

    Scaffold(
        // StatisticsHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StatisticsHeader(
                onBack = onBack,
                period = uiState.period,
                onPeriodChange = viewModel::onPeriodChange,
                monthlyWaste = uiState.monthlyWaste,
                yearlyWaste = uiState.yearlyWaste,
                deltaPercent = uiState.wasteDeltaPercent,
                productCount = uiState.wasteProductCountThisMonth,
                wasteGoal = uiState.wasteGoal,
                onEditGoal = { showGoalDialog = true },
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    StatusTilesRow(
                        expiringSoonCount = uiState.expiringSoonCount,
                        savedValueThisMonth = uiState.savedValueThisMonth,
                        totalInInventory = uiState.totalInInventory,
                        categoryCount = uiState.categoryCount,
                        onExpiringSoonClick = onNavigateToExpiringSoon,
                        onInStockClick = onNavigateToInventory,
                    )
                }

                item { TopWastedCard(products = uiState.topWastedProducts) }

                item {
                    ActivityShareRow(
                        shareEntries = uiState.activityShare,
                        memberScans = uiState.memberScans,
                        expanded = activityShareExpanded,
                        onToggle = { activityShareExpanded = !activityShareExpanded },
                    )
                }

                item {
                    ScannedHistoryRow(
                        topScanned = uiState.topScannedProducts,
                        recentActivity = uiState.recentActivity,
                        expanded = scannedHistoryExpanded,
                        onToggle = { scannedHistoryExpanded = !scannedHistoryExpanded },
                    )
                }
            }
        }
    }
}

private fun formatPrice(value: Double): String = String.format(Locale.getDefault(), "€%.2f", value)

/**
 * The fixed (non-scrolling) green gradient header — back button + title row with the Maand/Jaar
 * period pills, then the screen's headline metric: this period's total approximate waste value,
 * a delta badge vs. the previous period, a subtitle with the product count and the household's
 * self-set waste-value goal (or a prompt to set one), and a bar chart matching the chosen period.
 */
@Composable
private fun StatisticsHeader(
    onBack: () -> Unit,
    period: StatisticsPeriod,
    onPeriodChange: (StatisticsPeriod) -> Unit,
    monthlyWaste: List<MonthlyWaste>,
    yearlyWaste: List<YearlyWaste>,
    deltaPercent: Int?,
    productCount: Int,
    wasteGoal: Double?,
    onEditGoal: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().offset(x = (-12).dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = contentColor)
            }
            Text(
                text = stringResource(R.string.statistics_title),
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            PeriodToggle(period = period, onPeriodChange = onPeriodChange)
        }
        val eyebrowLabel = when (period) {
            StatisticsPeriod.MONTH -> monthlyWaste.lastOrNull()?.month?.let {
                DateTimeFormatter.ofPattern("MMMM", locale).format(it.atDay(1))
            }.orEmpty().uppercase(locale)
            StatisticsPeriod.YEAR -> yearlyWaste.lastOrNull()?.year?.toString().orEmpty()
        }
        Text(
            text = stringResource(R.string.statistics_hero_eyebrow_format, eyebrowLabel),
            style = MaterialTheme.typography.labelSmall,
            color = OnTopAppBarContainerAccent,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        val currentValue = when (period) {
            StatisticsPeriod.MONTH -> monthlyWaste.lastOrNull()?.totalValue
            StatisticsPeriod.YEAR -> yearlyWaste.lastOrNull()?.totalValue
        } ?: 0.0
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = formatPrice(currentValue),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
            )
            if (deltaPercent != null) {
                DeltaBadge(deltaPercent = deltaPercent, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = pluralStringResource(R.plurals.statistics_hero_product_count_format, productCount, productCount),
                style = MaterialTheme.typography.bodyMedium,
                color = OnTopAppBarContainerAccent,
            )
            Text(text = " · ", style = MaterialTheme.typography.bodyMedium, color = OnTopAppBarContainerAccent)
            Text(
                text = if (wasteGoal != null) {
                    stringResource(R.string.statistics_hero_goal_format, formatPrice(wasteGoal))
                } else {
                    stringResource(R.string.statistics_hero_set_goal_cta)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OnTopAppBarContainerAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onEditGoal),
            )
        }
        val points = when (period) {
            StatisticsPeriod.MONTH -> monthlyWaste.map { DateTimeFormatter.ofPattern("MMM", locale).format(it.month.atDay(1)) to it.totalValue }
            StatisticsPeriod.YEAR -> yearlyWaste.map { it.year.toString() to it.totalValue }
        }
        if (points.isNotEmpty()) {
            MiniBarChart(points = points, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

/** "Maand"/"Jaar" segmented pill pair in the header's title row. */
@Composable
private fun PeriodToggle(period: StatisticsPeriod, onPeriodChange: (StatisticsPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(2.dp),
    ) {
        PeriodPill(
            label = stringResource(R.string.statistics_period_month),
            selected = period == StatisticsPeriod.MONTH,
            onClick = { onPeriodChange(StatisticsPeriod.MONTH) },
        )
        PeriodPill(
            label = stringResource(R.string.statistics_period_year),
            selected = period == StatisticsPeriod.YEAR,
            onClick = { onPeriodChange(StatisticsPeriod.YEAR) },
        )
    }
}

@Composable
private fun PeriodPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** Rounded "▼ 31% minder" chip next to the hero price — [deltaPercent] is signed (negative =
 *  less waste), the strings themselves already carry the direction arrow and wording. */
@Composable
private fun DeltaBadge(deltaPercent: Int, modifier: Modifier = Modifier) {
    val text = if (deltaPercent <= 0) {
        stringResource(R.string.statistics_delta_less_format, abs(deltaPercent))
    } else {
        stringResource(R.string.statistics_delta_more_format, deltaPercent)
    }
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MiniBarChart(points: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val maxValue = points.maxOf { it.second }.coerceAtLeast(0.01)
    Row(
        modifier = modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        points.forEachIndexed { index, (label, value) ->
            val isCurrent = index == points.lastIndex
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .fillMaxHeight((value / maxValue).toFloat().coerceIn(0.06f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (isCurrent) CoralSecondaryDark else Color.White.copy(alpha = 0.22f)),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnTopAppBarContainerAccent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Simple AlertDialog with a single decimal text field — sets or clears
 *  [com.dtraas.homestock.data.repository.HouseholdRepository.setWasteGoal]. */
@Composable
private fun GoalEditDialog(currentGoal: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit, onClear: () -> Unit) {
    var text by rememberSaveable(currentGoal) {
        mutableStateOf(currentGoal?.let { String.format(Locale.getDefault(), "%.0f", it) }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statistics_goal_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.statistics_goal_dialog_label)) },
                leadingIcon = { Text("€", style = MaterialTheme.typography.bodyLarge) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { text.replace(',', '.').toDoubleOrNull()?.let(onSave) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                if (currentGoal != null) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.statistics_goal_dialog_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/** Three plain neutral tiles — "Verloopt" and "Voorraad" navigate to Voorraad with that filter
 *  already applied (see InventoryScreen's showExpiringSoonOnOpen), "Bespaard" is informational
 *  (the value of everything used up rather than wasted this month) and has no destination. */
@Composable
private fun StatusTilesRow(
    expiringSoonCount: Int,
    savedValueThisMonth: Double,
    totalInInventory: Int,
    categoryCount: Int,
    onExpiringSoonClick: () -> Unit,
    onInStockClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusTile(
            label = stringResource(R.string.statistics_expiring_soon),
            value = expiringSoonCount.toString(),
            caption = stringResource(R.string.statistics_tile_expiring_caption),
            onClick = onExpiringSoonClick,
            locale = locale,
            modifier = Modifier.weight(1f),
        )
        StatusTile(
            label = stringResource(R.string.statistics_tile_saved_label),
            value = formatPrice(savedValueThisMonth),
            caption = stringResource(R.string.statistics_tile_saved_caption),
            onClick = null,
            locale = locale,
            modifier = Modifier.weight(1f),
        )
        StatusTile(
            label = stringResource(R.string.statistics_in_stock),
            value = totalInInventory.toString(),
            caption = pluralStringResource(R.plurals.statistics_tile_stock_caption_format, categoryCount, categoryCount),
            onClick = onInStockClick,
            locale = locale,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusTile(
    label: String,
    value: String,
    caption: String,
    onClick: (() -> Unit)?,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)) {
            Text(
                text = label.uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = SoftCardShapeCompact, color = MaterialTheme.colorScheme.surfaceContainerHigh) { content() }
    } else {
        Surface(modifier = modifier, shape = SoftCardShapeCompact, color = MaterialTheme.colorScheme.surfaceContainerHigh) { content() }
    }
}

/** "Wat je het vaakst weggooit" — one bordered card holding the ranked list plus a concrete
 *  tip derived from the top entry, matching the Claude Design mockup's single-card layout
 *  (this used to be a section heading + separate cards). */
@Composable
private fun TopWastedCard(products: List<TopWastedProduct>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = SoftCardShapeCompact,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.statistics_most_wasted),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.statistics_most_wasted_period),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (products.isEmpty()) {
                Text(
                    text = stringResource(R.string.statistics_no_waste_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                return@Column
            }
            val maxWastedCount = products.maxOf { it.wastedCount }
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                products.forEach { product -> TopWastedRow(product = product, maxCount = maxWastedCount) }
            }
            WasteTipBox(topProduct = products.first(), modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** One ranked row inside [TopWastedCard]: category icon, name + proportional bar, trailing
 *  "N× · €X,XX" — no card of its own now that it lives inside one shared card. */
@Composable
private fun TopWastedRow(product: TopWastedProduct, maxCount: Int) {
    val category = Category.fromStorageKey(product.category)
    Row(verticalAlignment = Alignment.CenterVertically) {
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

/** A single concrete suggestion, derived from whichever product tops the waste ranking — not a
 *  generic tip, so it changes as the household's own habits do. */
@Composable
private fun WasteTipBox(topProduct: TopWastedProduct, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(
                R.string.statistics_insight_format,
                stringResource(Category.fromStorageKey(topProduct.category).displayNameRes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** "Dennis 62% · Marieke 38% — van alle wijzigingen deze maand" — tapping expands the same
 *  per-member breakdown cards the old "Wie doet wat" section always showed. */
@Composable
private fun ActivityShareRow(
    shareEntries: List<ActivityShareEntry>,
    memberScans: List<MemberScanEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        Surface(
            onClick = onToggle,
            shape = SoftCardShapeCompact,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (shareEntries.isEmpty()) {
                        Text(stringResource(R.string.statistics_activity_share_empty), style = MaterialTheme.typography.titleSmall)
                    } else {
                        val combined = shareEntries.joinToString(" · ") { entry ->
                            val name = entry.name.ifBlank { stringResource(R.string.activity_actor_unknown) }
                            "$name ${entry.percent}%"
                        }
                        Text(combined, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(R.string.statistics_activity_share_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.statistics_by_actor), style = MaterialTheme.typography.titleSmall)
                if (memberScans.isEmpty()) {
                    Text(
                        text = stringResource(R.string.statistics_no_scans_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    MemberScanRow(members = memberScans, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/** "Meest gescand, activiteit & geschiedenis" — tapping expands the most-scanned ranking (never
 *  shown anywhere else in the app before this screen) plus a short recent-activity feed. */
@Composable
private fun ScannedHistoryRow(
    topScanned: List<TopScannedProduct>,
    recentActivity: List<ActivityLogWithProduct>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        Surface(
            onClick = onToggle,
            shape = SoftCardShapeCompact,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.statistics_scanned_history_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.statistics_scanned_section_title), style = MaterialTheme.typography.titleSmall)
                if (topScanned.isEmpty()) {
                    Text(
                        text = stringResource(R.string.statistics_no_scans_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topScanned.forEach { ScannedProductRow(it) }
                    }
                }
                Text(
                    text = stringResource(R.string.statistics_recent_activity_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (recentActivity.isEmpty()) {
                    Text(
                        text = stringResource(R.string.statistics_recent_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recentActivity.forEach { RecentActivityRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedProductRow(product: TopScannedProduct) {
    val category = Category.fromStorageKey(product.category)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Text(
            text = stringResource(R.string.statistics_scan_count_format, product.scanCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun recentActivityIcon(type: ActivityType): ImageVector = when (type) {
    ActivityType.SCANNED -> Icons.Filled.QrCodeScanner
    ActivityType.QUANTITY_CHANGED -> Icons.Filled.Tune
    ActivityType.REMOVED -> Icons.Filled.Delete
    ActivityType.ADDED_TO_SHOPPING_LIST -> Icons.Filled.AddShoppingCart
    ActivityType.WASTED -> Icons.Filled.DeleteSweep
}

@Composable
private fun RecentActivityRow(entry: ActivityLogWithProduct) {
    val type = remember(entry.type) { ActivityType.fromStorageKey(entry.type) }
    val time = remember(entry.timestamp) {
        DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = recentActivityIcon(type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = "${entry.productName} · ${entry.detail}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.actorName ?: stringResource(R.string.activity_actor_unknown)} · $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Wie doet wat" — one card per household member who's scanned at least once, avatar (photo or
 *  a generic icon when no match was found — see [MemberScanEntry]'s doc) plus their scan count. */
@Composable
private fun MemberScanRow(members: List<MemberScanEntry>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
