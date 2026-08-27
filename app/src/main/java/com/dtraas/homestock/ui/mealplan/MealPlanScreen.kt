package com.dtraas.homestock.ui.mealplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.local.entity.MealCompletionStatus
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.SheetChip
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.ui.recipes.GenerateRecipeError
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Instellingen > Beta > Maaltijdplanner — a real, date-based plan (see
 * [com.dtraas.homestock.data.repository.MealPlanRepository]): the top bar shows the selected
 * date with prev/next-day arrows, and each of the four [MealSlot]s can hold a recipe, a plain
 * voorraad product, or a hand-typed name. Tapping a planned recipe navigates to the existing
 * recipe detail screen (see [onRecipeClick]), which already shows matched/missing ingredients
 * and an "add missing to shopping list" action; tapping a planned product that matched voorraad
 * navigates to that product's detail screen (see [onProductClick]) the same way — no need to
 * duplicate either elsewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onRecipeClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onNavigateToCookMode: (String) -> Unit = {},
    onNavigateToWeekOverview: (LocalDate) -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: MealPlanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MealPlanViewModel(
                    application.container.mealPlanRepository,
                    application.container.recipeRepository,
                    application.container.inventoryRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val locale: Locale = LocalConfiguration.current.locales[0]
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val addedFormat = stringResource(R.string.meal_plan_product_added_to_shopping_list_format)
    val alreadyOnListFormat = stringResource(R.string.meal_plan_product_already_on_shopping_list_format)
    val removedFormat = stringResource(R.string.meal_plan_removed_format)
    val undoLabel = stringResource(R.string.common_undo)

    // Tapping CompactPlannedRow's "toevoegen aan boodschappenlijst" button otherwise gives no
    // feedback that anything happened — this confirms it, and says which of the two outcomes
    // (freshly added, or already there from an earlier tap/another source) it actually was.
    LaunchedEffect(Unit) {
        viewModel.shoppingListAddResult.collect { result ->
            val message = if (result.alreadyOnList) alreadyOnListFormat.format(result.name) else addedFormat.format(result.name)
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // Shared by both the "X" button and the swipe-to-delete gesture on CompactPlannedRow — same
    // remove-then-offer-undo pattern as Boodschappenlijst's rows.
    fun removeWithUndo(slot: MealSlot, meal: PlannedMeal) {
        viewModel.removeMeal(slot, meal)
        coroutineScope.launch {
            // showSnackbar defaults to SnackbarDuration.Indefinite whenever an actionLabel is
            // set, so without an explicit duration this would never auto-dismiss.
            val result = snackbarHostState.showSnackbar(
                message = removedFormat.format(meal.name),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreMeal(slot, meal)
            }
        }
    }

    val today = remember { LocalDate.now() }
    val selectedDayLabel = remember(uiState.date, locale) {
        val dayName = uiState.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
        val rest = uiState.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        "$dayName $rest"
    }
    Scaffold(
        // MealPlanHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Closes the plan -> shop loop: diffs every recipe planned anywhere this week against
        // inventory (see MealPlanViewModel.loadMissingIngredientsForWeek) and offers to add the
        // delta in one tap. Pinned outside the scrolling content, not shown at all once there's
        // nothing missing (no reason to occupy space with an empty state here).
        bottomBar = {
            if (uiState.missingIngredientsForWeek.isNotEmpty()) {
                MissingIngredientsBar(
                    count = uiState.missingIngredientsForWeek.size,
                    onAddToList = viewModel::addMissingIngredientsForWeekToShoppingList,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The dagstrip used to sit on plain white background right below a flat topBar —
            // now it's inside the same green gradient block as the "Deze week" title, matching
            // the Claude Design review's "Deze week" header (see MealPlanHeader).
            MealPlanHeader(
                selectedDate = uiState.date,
                weekPlan = uiState.weekPlan,
                onSelectDate = viewModel::selectDate,
                onPreviousWeek = viewModel::goToPreviousWeek,
                onNextWeek = viewModel::goToNextWeek,
                onOpenWeekOverview = { onNavigateToWeekOverview(uiState.date) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = selectedDayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (uiState.date == today) {
                        Text(
                            text = stringResource(R.string.meal_plan_today_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Avondeten first and visually featured — the one meal of the day a household most
                // reliably plans ahead for — with the other three slots below it: Ontbijt/Lunch
                // side by side (per the Claude Design mockup), Tussendoor still full-width.
                DinnerCard(
                    planned = uiState.plan[MealSlot.DINNER].orEmpty(),
                    detail = uiState.dinnerDetail,
                    isLoading = uiState.isDinnerDetailLoading,
                    matchedIngredientCount = uiState.dinnerMatchedIngredientCount,
                    expiringIngredientUsed = uiState.dinnerExpiringIngredientUsed,
                    onOpenRecipe = onRecipeClick,
                    onOpenProduct = onProductClick,
                    onAddClick = { viewModel.openPicker(MealSlot.DINNER) },
                    onSwap = { meal ->
                        removeWithUndo(MealSlot.DINNER, meal)
                        viewModel.openPicker(MealSlot.DINNER)
                    },
                    onRemove = { meal -> removeWithUndo(MealSlot.DINNER, meal) },
                    onAddToShoppingList = { meal -> viewModel.addProductToShoppingList(meal.name) },
                    onAddMissingToShoppingList = viewModel::addMissingIngredientsForDinner,
                    onMarkEaten = { meal -> viewModel.markMealEaten(MealSlot.DINNER, meal) },
                    onMarkWasted = { meal -> viewModel.markMealWasted(MealSlot.DINNER, meal) },
                    onStartCookMode = onNavigateToCookMode,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(MealSlot.BREAKFAST, MealSlot.LUNCH).forEach { slot ->
                        MealSlotTile(
                            slot = slot,
                            label = stringResource(slot.labelRes),
                            planned = uiState.plan[slot].orEmpty(),
                            onAddClick = { viewModel.openPicker(slot) },
                            onOpenRecipe = onRecipeClick,
                            onOpenProduct = onProductClick,
                            onRemove = { meal -> removeWithUndo(slot, meal) },
                            onAddToShoppingList = { meal -> viewModel.addProductToShoppingList(meal.name) },
                            onMarkEaten = { meal -> viewModel.markMealEaten(slot, meal) },
                            onMarkWasted = { meal -> viewModel.markMealWasted(slot, meal) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    val pickerSlot = uiState.pickerSlot
    if (pickerSlot != null) {
        MealPickerDialog(
            titleText = stringResource(R.string.meal_plan_picker_title_format, stringResource(pickerSlot.labelRes), selectedDayLabel),
            mode = uiState.pickerMode,
            onModeChange = viewModel::setPickerMode,
            manualEntryText = uiState.manualEntryText,
            onManualEntryTextChange = viewModel::onManualEntryTextChange,
            onManualEntryAdd = viewModel::addManualMeal,
            isLoading = uiState.isPickerLoading,
            suggestions = uiState.pickerSuggestions,
            favoriteIds = uiState.pickerFavoriteIds,
            onSelectRecipe = viewModel::pickMeal,
            productEntryText = uiState.productEntryText,
            onProductEntryTextChange = viewModel::onProductEntryTextChange,
            onProductEntryAdd = viewModel::addManualProduct,
            isProductLoading = uiState.isProductPickerLoading,
            inventoryItems = uiState.inventoryItems,
            onSelectProduct = viewModel::pickProduct,
            isGeneratingAiMeal = uiState.isGeneratingAiMeal,
            aiMealError = uiState.aiMealError,
            onGenerateAiMeal = { viewModel.generateAiMeal(locale.language) },
            onDismiss = viewModel::dismissPicker,
        )
    }
}

/** Recognizable icon per meal slot, shown next to its label. */
private val MealSlot.icon: ImageVector
    get() = when (this) {
        MealSlot.BREAKFAST -> Icons.Filled.FreeBreakfast
        MealSlot.LUNCH -> Icons.Filled.LunchDining
        MealSlot.DINNER -> Icons.Filled.DinnerDining
        MealSlot.SNACK -> Icons.Filled.Cookie
    }

/**
 * The fixed (non-scrolling) green gradient header — "Deze week" title, the week's date range
 * with prev/next chevrons flanking it, and the tappable dagstrip right underneath spanning the
 * full header width, all inside the same gradient block (per the Claude Design review's header
 * mockup: date chips full-width, the week range in place of a planned-count subtitle, with the
 * week navigation moved up to flank that range instead of squeezing the day strip). Replaces the
 * old flat HomeStockTopAppBar the title/subtitle used to live in on their own, with the dagstrip
 * further down on plain white background.
 */
@Composable
private fun MealPlanHeader(
    selectedDate: LocalDate,
    weekPlan: Map<LocalDate, Map<MealSlot, List<PlannedMeal>>>,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenWeekOverview: () -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.meal_plan_this_week_title),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
            )
            // Altijd zichtbaar, in tegenstelling tot een eerdere plek op de uitgelichte
            // avondeten-kaart — die rendert alleen zodra er een écht recept gepland staat voor
            // de geselecteerde dag, dus stond het icoon soms simpelweg nergens.
            IconButton(onClick = onOpenWeekOverview, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(R.string.meal_plan_week_overview_cd),
                    tint = contentColor,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            IconButton(onClick = onPreviousWeek) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.meal_plan_previous_week_cd), tint = contentColor)
            }
            Text(
                text = remember(selectedDate, locale) { weekRangeLabel(selectedDate, locale) },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OnTopAppBarContainerAccent,
            )
            IconButton(onClick = onNextWeek) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.meal_plan_next_week_cd), tint = contentColor)
            }
        }
        WeekDayStrip(
            selectedDate = selectedDate,
            weekPlan = weekPlan,
            onSelectDate = onSelectDate,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

/** "17 – 23 augustus" for a week fully inside one month; falls back to "28 augustus – 3
 *  september" across a month boundary, and adds the year on each side if the week also crosses
 *  a year boundary (New Year's week) — otherwise a bare day range reads ambiguously without
 *  its month at all. */
private fun weekRangeLabel(selectedDate: LocalDate, locale: Locale): String {
    val weekStart = selectedDate.with(DayOfWeek.MONDAY)
    val weekEnd = weekStart.plusDays(6)
    return when {
        weekStart.year != weekEnd.year -> {
            val startFmt = weekStart.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
            val endFmt = weekEnd.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
            "$startFmt – $endFmt"
        }
        weekStart.month != weekEnd.month -> {
            val startFmt = weekStart.format(DateTimeFormatter.ofPattern("d MMMM", locale))
            val endFmt = weekEnd.format(DateTimeFormatter.ofPattern("d MMMM", locale))
            "$startFmt – $endFmt"
        }
        else -> {
            val monthName = weekEnd.format(DateTimeFormatter.ofPattern("MMMM", locale))
            "${weekStart.dayOfMonth} – ${weekEnd.dayOfMonth} $monthName"
        }
    }
}

/**
 * Monday-start row of 7 day cards for jumping straight to any day in the current week, rather
 * than only stepping one day at a time — the selected day inverts to a solid white pill (dark
 * text/dot, matching the mockup's dagstrip on its green header), any other day is a translucent
 * white pill, and any day with at least one planned meal in [weekPlan] (any slot, not just
 * avondeten — unlike the header's "N van 7 avonden gepland" count) gets a small dot underneath.
 * Week-to-week navigation is the chevrons flanking the week-range label above this strip in
 * [MealPlanHeader] — this strip itself spans the full header width now, edge to edge, per the
 * Claude Design review's header mockup.
 */
@Composable
private fun WeekDayStrip(
    selectedDate: LocalDate,
    weekPlan: Map<LocalDate, Map<MealSlot, List<PlannedMeal>>>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val weekStart = remember(selectedDate) { selectedDate.with(DayOfWeek.MONDAY) }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            val isSelected = date == selectedDate
            val hasPlan = weekPlan[date]?.values?.any { it.isNotEmpty() } == true
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.10f))
                    .clickable { onSelectDate(date) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) SageGreenPrimary.copy(alpha = 0.7f) else OnTopAppBarContainerAccent,
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) SageGreenPrimary else Color.White,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !hasPlan -> Color.Transparent
                                isSelected -> SageGreenPrimary
                                else -> OnTopAppBarContainerAccent
                            }
                        ),
                )
            }
        }
    }
}

/**
 * Avondeten's own card — the one meal of the day households most reliably plan ahead for, so
 * once a real recipe is planned (not a plain product or hand-typed name) it gets a featured
 * treatment: a small thumbnail next to the name, an "N/M in huis" pill and — when tonight's
 * recipe would also use up something close to expiring — an orange "gebruikt X" pill, then a
 * "N missend"/"Kookstand" action line, per the Claude Design mockup (which replaces the earlier
 * full-width photo banner + primary "Kookmodus" button). Falls back to the same compact-row
 * treatment [CompactPlannedRow] gives every other slot when nothing's planned yet, or only a
 * product/hand-typed name is — there's no recipe detail to feature in that case. Multi-meal support (see
 * [PlannedMeal]'s doc) still applies: any planned dinner entries beyond the featured recipe
 * render as compact rows underneath it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DinnerCard(
    planned: List<PlannedMeal>,
    detail: RecipeDetail?,
    isLoading: Boolean,
    matchedIngredientCount: Int?,
    expiringIngredientUsed: String?,
    onOpenRecipe: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onAddClick: () -> Unit,
    onSwap: (PlannedMeal) -> Unit,
    onRemove: (PlannedMeal) -> Unit,
    onAddToShoppingList: (PlannedMeal) -> Unit,
    onAddMissingToShoppingList: () -> Unit,
    onMarkEaten: (PlannedMeal) -> Unit,
    onMarkWasted: (PlannedMeal) -> Unit,
    onStartCookMode: (String) -> Unit,
) {
    val featuredRecipe = planned.firstOrNull { it.recipeId != null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MealSlot.DINNER.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(
                text = stringResource(MealSlot.DINNER.labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            // Even a plain product/typed-name dinner has no [detail], so this only ever shows
            // for a real featured recipe — a plain entry has no serving count to report.
            detail?.servings?.let { servings ->
                Text(
                    text = pluralStringResource(R.plurals.meal_plan_dinner_servings_format, servings, servings),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (featuredRecipe != null) {
            var showOverflow by remember { mutableStateOf(false) }
            val totalIngredients = detail?.ingredients?.size
            val missingCount = if (matchedIngredientCount != null && totalIngredients != null) {
                totalIngredients - matchedIngredientCount
            } else {
                null
            }
            Card(
                onClick = { onOpenRecipe(featuredRecipe.recipeId!!) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = SoftCardShape,
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    if (featuredRecipe.thumbnailUrl != null) {
                        AsyncImage(
                            model = featuredRecipe.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(SoftImageShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(64.dp).clip(SoftImageShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(featuredRecipe.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp).size(14.dp), strokeWidth = 2.dp)
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                if (matchedIngredientCount != null && totalIngredients != null) {
                                    DinnerPill(
                                        text = stringResource(R.string.meal_plan_dinner_stock_pill_format, matchedIngredientCount, totalIngredients),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                if (expiringIngredientUsed != null) {
                                    DinnerPill(
                                        text = stringResource(R.string.meal_plan_dinner_expiring_pill_format, expiringIngredientUsed),
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (missingCount != null && missingCount > 0) {
                                    DinnerActionLink(
                                        icon = Icons.Filled.ShoppingCart,
                                        text = pluralStringResource(R.plurals.meal_plan_dinner_missing_format, missingCount, missingCount),
                                        onClick = onAddMissingToShoppingList,
                                    )
                                }
                                DinnerActionLink(
                                    icon = Icons.Filled.Restaurant,
                                    text = stringResource(R.string.meal_plan_start_cook_mode),
                                    onClick = { onStartCookMode(featuredRecipe.recipeId!!) },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.meal_plan_overflow_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meal_plan_swap_cd)) },
                                leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                                onClick = { showOverflow = false; onSwap(featuredRecipe) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meal_plan_clear_cd)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { showOverflow = false; onRemove(featuredRecipe) },
                            )
                        }
                    }
                }
            }
            // Any further avondeten entries beyond the featured recipe (multi-meal support).
            planned.filterNot { it.id == featuredRecipe.id }.forEach { meal ->
                CompactPlannedRow(
                    meal = meal,
                    onClick = {
                        when {
                            meal.recipeId != null -> onOpenRecipe(meal.recipeId)
                            meal.productBarcode != null -> onOpenProduct(meal.productBarcode)
                        }
                    },
                    onRemove = { onRemove(meal) },
                    onAddToShoppingList = { onAddToShoppingList(meal) },
                    onMarkEaten = { onMarkEaten(meal) },
                    onMarkWasted = { onMarkWasted(meal) },
                )
            }
            EmptySlotAddButton(onAddClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
        } else if (planned.isNotEmpty()) {
            planned.forEach { meal ->
                CompactPlannedRow(
                    meal = meal,
                    onClick = {
                        when {
                            meal.recipeId != null -> onOpenRecipe(meal.recipeId)
                            meal.productBarcode != null -> onOpenProduct(meal.productBarcode)
                        }
                    },
                    onRemove = { onRemove(meal) },
                    onAddToShoppingList = { onAddToShoppingList(meal) },
                    onMarkEaten = { onMarkEaten(meal) },
                    onMarkWasted = { onMarkWasted(meal) },
                )
            }
            EmptySlotAddButton(onAddClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
        } else {
            EmptySlotAddButton(onAddClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
        }
    }
}

/** Small display-only pill for [DinnerCard]'s "8/10 in huis"/"gebruikt spinazie" badges. */
@Composable
private fun DinnerPill(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = RoundedCornerShape(percent = 50), color = containerColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Small icon+text tappable line — [DinnerCard]'s "N missend"/"Kookstand" action row, a much
 *  lower-emphasis pair than the old full-width primary button, per the Claude Design mockup. */
@Composable
private fun DinnerActionLink(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Ontbijt/Lunch's own two-column card treatment, per the Claude Design mockup — a solid-bordered
 * card once something's planned, a dashed one (echoing [EmptySlotAddButton]'s own dashed style)
 * with a "+ Plannen" prompt when the slot is still empty, side by side with its sibling via the
 * caller's [Modifier.weight]. A planned slot still offers the same "add another" affordance
 * underneath its entries — multi-meal support isn't lost, just less
 * prominent than the empty-slot prompt.
 */
@Composable
private fun MealSlotTile(
    slot: MealSlot,
    label: String,
    planned: List<PlannedMeal>,
    onAddClick: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onRemove: (PlannedMeal) -> Unit,
    onAddToShoppingList: (PlannedMeal) -> Unit,
    onMarkEaten: (PlannedMeal) -> Unit,
    onMarkWasted: (PlannedMeal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = SoftCardShapeCompact
    val borderModifier = if (planned.isEmpty()) {
        Modifier.dashedBorder(MaterialTheme.colorScheme.outlineVariant, cornerRadius = 12.dp)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(borderModifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(slot.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (planned.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAddClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AddCircleOutline,
                    contentDescription = stringResource(R.string.meal_plan_add_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.meal_plan_plan_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        } else {
            planned.forEach { meal ->
                CompactPlannedRow(
                    meal = meal,
                    onClick = {
                        when {
                            meal.recipeId != null -> onOpenRecipe(meal.recipeId)
                            meal.productBarcode != null -> onOpenProduct(meal.productBarcode)
                        }
                    },
                    onRemove = { onRemove(meal) },
                    onAddToShoppingList = { onAddToShoppingList(meal) },
                    onMarkEaten = { onMarkEaten(meal) },
                    onMarkWasted = { onMarkWasted(meal) },
                    // This tile sits side by side with its sibling (see MealSlotTile's own doc) —
                    // half the app's width isn't enough room for a name plus action icons on one
                    // line, so the name gets its own full-width line here instead.
                    stacked = true,
                )
            }
            EmptySlotAddButton(onAddClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
        }
    }
}

/** A single 1dp-dashed, 24dp-icon "add" affordance — one plus per slot instead of the old
 *  side-by-side "+ Product"/"+ Recept" pair repeated in every slot. Opens [MealPickerDialog]
 *  directly (in [MealPickerMode.RECIPE]) — the "product or recept?" choice this used to open a
 *  second dialog for is now just the sheet's own "Product" filter chip, per the 2026-08 dialog
 *  review. */
@Composable
private fun EmptySlotAddButton(onClick: () -> Unit, contentDescription: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .dashedBorder(MaterialTheme.colorScheme.outlineVariant, cornerRadius = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircleOutline,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * A recipe pick or a matched voorraad product may have [PlannedMeal.thumbnailUrl]; the compact
 * row deliberately doesn't show it (unlike the old thumbnail-led row this replaces) — avondeten's
 * own [DinnerCard] is now where the app's one photo-forward treatment lives, so every other slot
 * reads as a slim, scannable line instead: name, an optional "toevoegen aan boodschappenlijst"
 * button for a planned product that didn't match voorraad (see [PlannedMeal.isProduct]/
 * [PlannedMeal.productBarcode]'s doc), and remove. Still swipeable end-to-start to remove, same
 * as before, on top of the explicit "X" button.
 *
 * [stacked] puts the name on its own full-width line (wrapping to 2 lines instead of hard-
 * truncating to almost nothing) with the action icons on a second line underneath, instead of
 * everything squeezed onto one line — [MealSlotTile] passes this for Ontbijt/Lunch, whose
 * half-width column (two tiles side by side) leaves too little room for a name plus up to 3
 * icons on one line. [DinnerCard]'s own extra rows are full app width, so they stay single-line.
 */
@Composable
private fun CompactPlannedRow(
    meal: PlannedMeal,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onAddToShoppingList: () -> Unit,
    onMarkEaten: () -> Unit,
    onMarkWasted: () -> Unit,
    stacked: Boolean = false,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRemove()
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.meal_plan_delete_cd),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        val containerModifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = meal.recipeId != null || meal.productBarcode != null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (stacked) 8.dp else 10.dp)
        val nameStyle = MaterialTheme.typography.bodyMedium
        val nameDecoration = if (meal.status != null) TextDecoration.LineThrough else null
        val nameColor = if (meal.status != null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified

        if (stacked) {
            Column(modifier = containerModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = meal.name,
                    style = nameStyle,
                    textDecoration = nameDecoration,
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlannedMealStatusActions(meal, onAddToShoppingList, onMarkEaten, onMarkWasted)
                    RemoveMealIconButton(onRemove)
                }
            }
        } else {
            Row(modifier = containerModifier, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meal.name,
                    style = nameStyle,
                    textDecoration = nameDecoration,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PlannedMealStatusActions(meal, onAddToShoppingList, onMarkEaten, onMarkWasted)
                RemoveMealIconButton(onRemove)
            }
        }
    }
}

/**
 * Opgemaakt/weggegooid (folded into one overflow menu, or — once resolved — a small status
 * label) plus the "toevoegen aan boodschappenlijst" icon, shared between [CompactPlannedRow]'s
 * single-line and [stacked][CompactPlannedRow] layouts. Doesn't include the "X" remove button
 * (see [RemoveMealIconButton]) — that one's always last, and each layout already places it that
 * way itself.
 */
@Composable
private fun PlannedMealStatusActions(
    meal: PlannedMeal,
    onAddToShoppingList: () -> Unit,
    onMarkEaten: () -> Unit,
    onMarkWasted: () -> Unit,
) {
    // Opgebruikt/weggegooid only ever applies to a planned product, and only until it's been
    // resolved — after that a small label replaces the overflow button rather than letting you
    // flip it back and forth (matching Productdetail's own one-way opgebruikt/weggegooid choice,
    // see removeFromInventory's doc). Folded into one overflow menu instead of two dedicated icon
    // buttons — same MoreVert-menu pattern DinnerCard's featured recipe card already uses for
    // swap/verwijder.
    if (meal.isProduct) {
        if (meal.status == null) {
            var showStatusMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showStatusMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.meal_plan_overflow_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.product_detail_delete_used_up)) },
                        leadingIcon = {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = { showStatusMenu = false; onMarkEaten() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.product_detail_delete_wasted)) },
                        leadingIcon = {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { showStatusMenu = false; onMarkWasted() },
                    )
                }
            }
        } else {
            Text(
                text = stringResource(
                    if (meal.status == MealCompletionStatus.EATEN) {
                        R.string.product_detail_delete_used_up
                    } else {
                        R.string.product_detail_delete_wasted
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (meal.status == MealCompletionStatus.EATEN) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    if (meal.isProduct && meal.productBarcode == null && meal.status == null) {
        IconButton(onClick = onAddToShoppingList, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.PlaylistAdd,
                contentDescription = stringResource(R.string.product_detail_add_to_shopping_list),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RemoveMealIconButton(onRemove: () -> Unit) {
    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.meal_plan_clear_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Pinned above the nav bar, only shown once [count] > 0 — the "plan -> shop" loop closer. */
@Composable
private fun MissingIngredientsBar(count: Int, onAddToList: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.meal_plan_missing_ingredients_title, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.meal_plan_missing_ingredients_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onAddToList,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier.height(46.dp).padding(start = 12.dp),
            ) {
                Text(stringResource(R.string.recipes_add_missing_short))
            }
        }
    }
}

/** A dashed rounded-rect outline — Compose has no built-in dashed border, so this draws one
 *  directly via a dash [PathEffect] on a round-rect stroke. Used for [EmptySlotAddButton], the
 *  one place this design calls for a dashed rather than solid outline. */
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
    )
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(cornerRadius.toPx()))
}

/**
 * The merged "add to slot" sheet — one search field doing double duty (live-filters
 * [suggestions]/[inventoryItems] as it's typed, and its own "Toevoegen" submits whatever's typed
 * as a manual/plain entry), a two-way "Recept"/"Product" mode toggle up top (the old
 * AddChooserDialog's job — see [EmptySlotAddButton]'s doc), and, in Recept mode, three filter
 * chips backed by data the app already has (matched-inventory count, favorites, cook time — see
 * [RecipeSuggestion]) plus a pinned "Bedenk een recept" footer that hands the slot straight to
 * [MealPlanViewModel.generateAiMeal] rather than opening yet another sheet.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MealPickerDialog(
    titleText: String,
    mode: MealPickerMode,
    onModeChange: (MealPickerMode) -> Unit,
    manualEntryText: String,
    onManualEntryTextChange: (String) -> Unit,
    onManualEntryAdd: () -> Unit,
    isLoading: Boolean,
    suggestions: List<RecipeSuggestion>,
    favoriteIds: Set<String>,
    onSelectRecipe: (RecipeSuggestion) -> Unit,
    productEntryText: String,
    onProductEntryTextChange: (String) -> Unit,
    onProductEntryAdd: () -> Unit,
    isProductLoading: Boolean,
    inventoryItems: List<InventoryItemWithProduct>,
    onSelectProduct: (InventoryItemWithProduct) -> Unit,
    isGeneratingAiMeal: Boolean,
    aiMealError: GenerateRecipeError?,
    onGenerateAiMeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    var stockOnly by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var quickOnly by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(suggestions, manualEntryText, stockOnly, favoritesOnly, quickOnly, favoriteIds) {
        val query = manualEntryText.trim()
        suggestions.filter { suggestion ->
            (query.isEmpty() || suggestion.meal.name.contains(query, ignoreCase = true)) &&
                (!stockOnly || (suggestion.matchCount ?: 0) > 0) &&
                (!favoritesOnly || suggestion.meal.id in favoriteIds) &&
                (!quickOnly || (suggestion.readyInMinutes != null && suggestion.readyInMinutes <= 20))
        }
    }
    val filteredProducts = remember(productEntryText, inventoryItems) {
        val query = productEntryText.trim()
        if (query.isEmpty()) inventoryItems else inventoryItems.filter { it.name.contains(query, ignoreCase = true) }
    }

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetTitle(title = titleText)
            MealPickerModeToggle(mode = mode, onModeChange = onModeChange)

            when (mode) {
                MealPickerMode.RECIPE -> {
                    OutlinedTextField(
                        value = manualEntryText,
                        onValueChange = onManualEntryTextChange,
                        placeholder = { Text(stringResource(R.string.meal_plan_manual_entry_placeholder)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (manualEntryText.isNotBlank()) {
                            { TextButton(onClick = onManualEntryAdd) { Text(stringResource(R.string.meal_plan_manual_entry_add)) } }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SheetChip(
                            label = stringResource(R.string.recipes_tab_inventory),
                            selected = stockOnly,
                            onClick = { stockOnly = !stockOnly },
                        )
                        SheetChip(
                            label = stringResource(R.string.recipes_tab_favorites),
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                        )
                        SheetChip(
                            label = stringResource(R.string.recipes_generate_wish_preset_fast),
                            selected = quickOnly,
                            onClick = { quickOnly = !quickOnly },
                        )
                    }
                    when {
                        isLoading -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        suggestions.isEmpty() -> Text(
                            text = stringResource(R.string.meal_plan_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        filteredSuggestions.isEmpty() -> Text(
                            text = stringResource(R.string.meal_plan_picker_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn(
                            modifier = Modifier.heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filteredSuggestions, key = { it.meal.id }) { suggestion ->
                                RecipeSuggestionRow(
                                    suggestion = suggestion,
                                    isFavorite = suggestion.meal.id in favoriteIds,
                                    onClick = { onSelectRecipe(suggestion) },
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    if (aiMealError != null) {
                        val (icon, messageRes) = when (aiMealError) {
                            GenerateRecipeError.PREMIUM_REQUIRED -> Icons.Filled.WorkspacePremium to R.string.recipes_generate_ai_failed_premium
                            GenerateRecipeError.NO_CONNECTION -> Icons.Filled.CloudOff to R.string.recipes_generate_ai_failed_no_connection
                            GenerateRecipeError.UNKNOWN -> Icons.Filled.WifiOff to R.string.recipes_generate_ai_failed_unknown
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(stringResource(messageRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    OutlinedButton(
                        onClick = onGenerateAiMeal,
                        enabled = !isGeneratingAiMeal,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (isGeneratingAiMeal) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.recipes_generate_ai_action), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                MealPickerMode.PRODUCT -> {
                    OutlinedTextField(
                        value = productEntryText,
                        onValueChange = onProductEntryTextChange,
                        placeholder = { Text(stringResource(R.string.meal_plan_product_entry_placeholder)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (productEntryText.isNotBlank()) {
                            { TextButton(onClick = onProductEntryAdd) { Text(stringResource(R.string.meal_plan_manual_entry_add)) } }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when {
                        isProductLoading -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        filteredProducts.isEmpty() -> Text(
                            text = stringResource(R.string.meal_plan_product_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn(
                            modifier = Modifier.heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filteredProducts, key = { it.barcode }) { item ->
                                ProductPickerRow(item = item, onClick = { onSelectProduct(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Full-width two-way "Recept"/"Product" pill toggle — same shape as [SortSegmentedControl] in
 *  ShoppingListScreen, just a fixed 2-value domain instead of an enum with more entries. */
@Composable
private fun MealPickerModeToggle(mode: MealPickerMode, onModeChange: (MealPickerMode) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(3.dp)) {
            MealPickerMode.entries.forEach { candidate ->
                val isSelected = candidate == mode
                Surface(
                    onClick = { onModeChange(candidate) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(if (candidate == MealPickerMode.RECIPE) R.string.meal_plan_add_recipe else R.string.meal_plan_add_product),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** A recipe suggestion row — same thumbnail-plus-name shape the old PickerRow had, with a real
 *  in-stock/missing-ingredient (or, failing that, cook-time) meta line underneath the name (see
 *  [recipeSuggestionMetaText]) and a small heart mark for anything already in [isFavorite]. */
@Composable
private fun RecipeSuggestionRow(suggestion: RecipeSuggestion, isFavorite: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (suggestion.meal.thumbnailUrl != null) {
            AsyncImage(
                model = suggestion.meal.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(SoftImageShape),
            )
        } else {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = suggestion.meal.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val metaText = recipeSuggestionMetaText(suggestion)
            if (metaText != null) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
            )
        }
    }
}

/** The row's real meta line, in priority order: matched/total ingredients when this suggestion
 *  came from an inventory match (see [RecipeSuggestion.totalIngredientCount]'s doc), else cook
 *  time when known (cuisine-matched or favorited suggestions carry it — see
 *  [RecipeSuggestion.readyInMinutes]'s doc), else nothing rather than a guess. */
@Composable
private fun recipeSuggestionMetaText(suggestion: RecipeSuggestion): String? = when {
    suggestion.totalIngredientCount != null && suggestion.missingIngredients.isEmpty() ->
        stringResource(R.string.meal_plan_all_in_stock)
    suggestion.totalIngredientCount != null ->
        stringResource(R.string.meal_plan_recipe_match_format, suggestion.matchCount ?: 0, suggestion.totalIngredientCount)
    suggestion.readyInMinutes != null -> stringResource(R.string.recipes_ready_in_minutes_format, suggestion.readyInMinutes)
    else -> null
}

@Composable
private fun ProductPickerRow(item: InventoryItemWithProduct, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(SoftImageShape),
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}
