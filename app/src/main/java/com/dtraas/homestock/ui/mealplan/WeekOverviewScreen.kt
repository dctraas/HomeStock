package com.dtraas.homestock.ui.mealplan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.MealCompletionStatus
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SoftCardShape
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "17 – 23 augustus" for a week fully inside one month; falls back to spelling out both months
 *  across a boundary — same shape as [MealPlanHeader]'s own week-range label (that one is
 *  file-private to MealPlanScreen.kt, so this is a small standalone copy rather than a shared
 *  export, kept in sync by hand since the format itself is stable UI copy, not logic). */
private fun weekRangeLabel(weekStart: LocalDate, locale: Locale): String {
    val weekEnd = weekStart.plusDays(6)
    return if (weekStart.month != weekEnd.month) {
        val startFmt = weekStart.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        val endFmt = weekEnd.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        "$startFmt – $endFmt"
    } else {
        val monthName = weekEnd.format(DateTimeFormatter.ofPattern("MMMM", locale))
        "${weekStart.dayOfMonth} – ${weekEnd.dayOfMonth} $monthName"
    }
}

/**
 * Full-week read of the household's avondeten plan — one row per day (Monday-start), each
 * showing that day's featured dinner (photo, name, and either "opgebruikt"/"weggegooid" once
 * resolved or a live ingredients-in-huis ring while it's still upcoming) or, for a day with
 * nothing planned yet, a "Plannen" suggestion chip. Reached from the calendar icon next to
 * MealPlanScreen's "Meer opties" on the featured avondeten card. Tapping any row (or its
 * suggestion chip) closes back to MealPlanScreen on that exact day, ready to plan or review it —
 * this screen is a read-mostly overview, not a second place to edit a day's plan.
 */
@Composable
fun WeekOverviewScreen(anchorDate: LocalDate, onClose: () -> Unit, onSelectDate: (LocalDate) -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: WeekOverviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WeekOverviewViewModel(application.container.mealPlanRepository, application.container.recipeRepository)
            }
        },
    )
    val weekStart = remember(anchorDate) { anchorDate.with(DayOfWeek.MONDAY) }
    LaunchedEffect(weekStart) { viewModel.load(weekStart) }
    val rows by viewModel.rows.collectAsState()
    val today = remember { LocalDate.now() }
    val locale: Locale = LocalConfiguration.current.locales[0]

    fun jumpTo(date: LocalDate) {
        onSelectDate(date)
        onClose()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentColor = LocalTopAppBarContentColor.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalTopAppBarContainerColor.current)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .padding(bottom = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = contentColor)
                    }
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = stringResource(R.string.week_overview_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = contentColor,
                        )
                        Text(
                            text = weekRangeLabel(weekStart, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnTopAppBarContainerAccent,
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.date.toString() }) { row ->
                    WeekOverviewDayRow(
                        row = row,
                        isToday = row.date == today,
                        locale = locale,
                        onClick = { jumpTo(row.date) },
                    )
                }
                item {
                    val plannedCount = rows.count { it.dinner != null }
                    Text(
                        text = pluralStringResource(R.plurals.week_overview_planned_count_format, plannedCount, plannedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekOverviewDayRow(row: WeekOverviewDayRow, isToday: Boolean, locale: Locale, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = SoftCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = row.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase(locale) },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            val dinner = row.dinner
            if (dinner == null) {
                Text(
                    text = stringResource(R.string.week_overview_evening_free),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                )
                SuggestionChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.week_overview_plan_suggestion)) },
                )
            } else {
                DinnerThumbnail(dinner)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = dinner.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (dinner.status != null) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = when {
                        dinner.status == MealCompletionStatus.EATEN -> stringResource(R.string.product_detail_delete_used_up)
                        dinner.status == MealCompletionStatus.WASTED -> stringResource(R.string.product_detail_delete_wasted)
                        row.totalIngredients > 0 && row.matchedCount >= row.totalIngredients -> stringResource(R.string.meal_plan_all_in_stock)
                        row.totalIngredients > 0 -> stringResource(R.string.meal_plan_recipe_match_format, row.matchedCount, row.totalIngredients)
                        else -> null
                    }
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
                when {
                    dinner.status == MealCompletionStatus.EATEN -> DoneMarker(icon = Icons.Filled.Check, tint = MaterialTheme.colorScheme.primary)
                    dinner.status == MealCompletionStatus.WASTED -> DoneMarker(icon = Icons.Filled.EventBusy, tint = MaterialTheme.colorScheme.error)
                    row.totalIngredients > 0 -> IngredientRing(matched = row.matchedCount, total = row.totalIngredients)
                }
            }
        }
    }
}

/** [dinner]'s photo when it has one (a real Spoonacular/AI recipe), else a generic dinner icon —
 *  same fallback shape as [ProductImage][com.dtraas.homestock.ui.components.ProductImage]
 *  elsewhere, kept local here since this tile's sizing/rounding is specific to this row. */
@Composable
private fun DinnerThumbnail(dinner: PlannedMeal) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
        if (dinner.thumbnailUrl != null) {
            var loadFailed by remember(dinner.thumbnailUrl) { mutableStateOf(false) }
            if (!loadFailed) {
                AsyncImage(
                    model = dinner.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { state -> loadFailed = state is AsyncImagePainter.State.Error },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DinnerThumbnailFallback()
            }
        } else {
            DinnerThumbnailFallback()
        }
    }
}

@Composable
private fun DinnerThumbnailFallback() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
            imageVector = Icons.Filled.RestaurantMenu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small filled circle with a check (opgebruikt) or a crossed-out-event glyph (weggegooid) —
 *  the resolved-outcome equivalent of [IngredientRing] for a dinner that's already been eaten
 *  or thrown away, so this row doesn't keep showing an ingredients ring for a day that's done. */
@Composable
private fun DoneMarker(icon: ImageVector, tint: Color) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/** [matched]/[total] ingredients-in-huis, as a determinate ring with the fraction centered
 *  inside it — same underlying data as [DinnerCard]'s own "alles in huis" check
 *  ([com.dtraas.homestock.data.repository.RecipeRepository.matchedIngredients]), just visualized
 *  per day here instead of as plain text for just the selected one. */
@Composable
private fun IngredientRing(matched: Int, total: Int) {
    val complete = total > 0 && matched >= total
    val color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
        CircularProgressIndicator(
            progress = { if (total > 0) matched.toFloat() / total else 0f },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeWidth = 3.dp,
        )
        Text(
            text = "$matched/$total",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
