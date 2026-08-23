package com.dtraas.homestock.ui.mealplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.graphics.Brush
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
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerGradientEnd
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftCardShape
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
    // "N van 7 avonden gepland" counts avondeten specifically (not any-slot, unlike the day
    // strip's own dots below) — the one meal households most reliably plan ahead for, per the
    // design review.
    val plannedDinnerCount = remember(uiState.weekPlan) {
        uiState.weekPlan.values.count { it[MealSlot.DINNER]?.isNotEmpty() == true }
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
                plannedDinnerCount = plannedDinnerCount,
                selectedDate = uiState.date,
                weekPlan = uiState.weekPlan,
                onSelectDate = viewModel::selectDate,
                onPreviousWeek = viewModel::goToPreviousWeek,
                onNextWeek = viewModel::goToNextWeek,
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
                // reliably plans ahead for — with the other three slots stacked below it in their
                // usual order.
                DinnerCard(
                    planned = uiState.plan[MealSlot.DINNER].orEmpty(),
                    detail = uiState.dinnerDetail,
                    isLoading = uiState.isDinnerDetailLoading,
                    allInStock = uiState.dinnerAllIngredientsInStock,
                    onOpenRecipe = onRecipeClick,
                    onOpenProduct = onProductClick,
                    onAddProductClick = { viewModel.openProductPicker(MealSlot.DINNER) },
                    onAddMealClick = { viewModel.openPicker(MealSlot.DINNER) },
                    onSwap = { meal ->
                        removeWithUndo(MealSlot.DINNER, meal)
                        viewModel.openPicker(MealSlot.DINNER)
                    },
                    onRemove = { meal -> removeWithUndo(MealSlot.DINNER, meal) },
                    onAddToShoppingList = { meal -> viewModel.addProductToShoppingList(meal.name) },
                    onStartCookMode = onNavigateToCookMode,
                )
                MealSlot.ORDERED.filter { it != MealSlot.DINNER }.forEach { slot ->
                    CompactSlotCard(
                        slot = slot,
                        label = stringResource(slot.labelRes),
                        planned = uiState.plan[slot].orEmpty(),
                        onAddProductClick = { viewModel.openProductPicker(slot) },
                        onAddMealClick = { viewModel.openPicker(slot) },
                        onOpenRecipe = onRecipeClick,
                        onOpenProduct = onProductClick,
                        onRemove = { meal -> removeWithUndo(slot, meal) },
                        onAddToShoppingList = { meal -> viewModel.addProductToShoppingList(meal.name) },
                    )
                }
            }
        }
    }

    val pickerSlot = uiState.pickerSlot
    if (pickerSlot != null) {
        MealPickerDialog(
            titleText = stringResource(R.string.meal_plan_picker_title_format, stringResource(pickerSlot.labelRes)),
            manualEntryText = uiState.manualEntryText,
            onManualEntryTextChange = viewModel::onManualEntryTextChange,
            onManualEntryAdd = viewModel::addManualMeal,
            isLoading = uiState.isPickerLoading,
            suggestions = uiState.pickerSuggestions,
            onSelect = viewModel::pickMeal,
            onDismiss = viewModel::dismissPicker,
        )
    }

    val productPickerSlot = uiState.productPickerSlot
    if (productPickerSlot != null) {
        ProductPickerDialog(
            titleText = stringResource(R.string.meal_plan_product_picker_title_format, stringResource(productPickerSlot.labelRes)),
            entryText = uiState.productEntryText,
            onEntryTextChange = viewModel::onProductEntryTextChange,
            onEntryAdd = viewModel::addManualProduct,
            isLoading = uiState.isProductPickerLoading,
            inventoryItems = uiState.inventoryItems,
            onSelect = viewModel::pickProduct,
            onDismiss = viewModel::dismissProductPicker,
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
 * The fixed (non-scrolling) green gradient header — "Deze week" + the "N van 7 avonden gepland"
 * count, and the tappable dagstrip right underneath, all inside the same gradient block ("De
 * dagstrip met tapbare kaarten moet in de groene header vallen", per the Claude Design review;
 * see also artboard 1e in the uploaded mockup, which this header's layout mirrors). Replaces the
 * old flat HomeStockTopAppBar the title/subtitle used to live in on their own, with the dagstrip
 * further down on plain white background.
 */
@Composable
private fun MealPlanHeader(
    plannedDinnerCount: Int,
    selectedDate: LocalDate,
    weekPlan: Map<LocalDate, Map<MealSlot, List<PlannedMeal>>>,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(LocalTopAppBarContainerColor.current, LocalTopAppBarContainerGradientEnd.current)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 14.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.meal_plan_this_week_title),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
            )
            Text(
                text = pluralStringResource(R.plurals.meal_plan_dinners_planned, plannedDinnerCount, plannedDinnerCount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OnTopAppBarContainerAccent,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            IconButton(onClick = onPreviousWeek) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.meal_plan_previous_week_cd), tint = contentColor)
            }
            WeekDayStrip(
                selectedDate = selectedDate,
                weekPlan = weekPlan,
                onSelectDate = onSelectDate,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNextWeek) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.meal_plan_next_week_cd), tint = contentColor)
            }
        }
    }
}

/**
 * Monday-start row of 7 day cards for jumping straight to any day in the current week, rather
 * than only stepping one day at a time — the selected day inverts to a solid white pill (dark
 * text/dot, matching the mockup's dagstrip on its green header), any other day is a translucent
 * white pill, and any day with at least one planned meal in [weekPlan] (any slot, not just
 * avondeten — unlike the header's "N van 7 avonden gepland" count) gets a small dot underneath.
 * Week-to-week navigation is the chevrons flanking this strip in [MealPlanHeader].
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
 * once a real recipe is planned (not a plain product or hand-typed name) it gets a full photo
 * card with cook-time/servings/"alles in huis" and a "Kookmodus" shortcut, rather than the same
 * compact treatment as [CompactSlotCard]'s other three slots. Falls back to that same compact
 * treatment when nothing's planned yet, or only a product/hand-typed name is — there's no recipe
 * detail to feature in that case. Multi-meal support (see [PlannedMeal]'s doc) still applies:
 * any planned dinner entries beyond the featured recipe render as compact rows underneath it.
 */
@Composable
private fun DinnerCard(
    planned: List<PlannedMeal>,
    detail: RecipeDetail?,
    isLoading: Boolean,
    allInStock: Boolean,
    onOpenRecipe: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onAddProductClick: () -> Unit,
    onAddMealClick: () -> Unit,
    onSwap: (PlannedMeal) -> Unit,
    onRemove: (PlannedMeal) -> Unit,
    onAddToShoppingList: (PlannedMeal) -> Unit,
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
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (featuredRecipe != null) {
            var showOverflow by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = SoftCardShape,
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(118.dp)) {
                        if (featuredRecipe.thumbnailUrl != null) {
                            AsyncImage(
                                model = featuredRecipe.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(SoftImageShape).clickable { onOpenRecipe(featuredRecipe.recipeId!!) },
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(SoftImageShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .clickable { onOpenRecipe(featuredRecipe.recipeId!!) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Restaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        ) {
                            Text(
                                text = stringResource(MealSlot.DINNER.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(featuredRecipe.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val metaParts = listOfNotNull(
                            detail?.readyInMinutes?.let { stringResource(R.string.recipes_ready_in_minutes_format, it) },
                            detail?.servings?.let { stringResource(R.string.meal_plan_servings_format, it) },
                            if (allInStock) stringResource(R.string.meal_plan_all_in_stock) else null,
                        )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 6.dp).size(14.dp), strokeWidth = 2.dp)
                        } else if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = { onStartCookMode(featuredRecipe.recipeId!!) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.meal_plan_start_cook_mode), modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedIconButton(onClick = { onSwap(featuredRecipe) }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.meal_plan_swap_cd))
                            }
                            Box {
                                OutlinedIconButton(onClick = { showOverflow = true }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.meal_plan_overflow_cd))
                                }
                                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.meal_plan_clear_cd)) },
                                        onClick = { showOverflow = false; onRemove(featuredRecipe) },
                                    )
                                }
                            }
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
                )
            }
            EmptySlotAddButton(onAddProductClick, onAddMealClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
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
                )
            }
            EmptySlotAddButton(onAddProductClick, onAddMealClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
        } else {
            EmptySlotAddButton(onAddProductClick, onAddMealClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
        }
    }
}

/** Ontbijt/Lunch/Tussendoor — the same compact treatment throughout, no featured-recipe photo
 *  card the way avondeten gets (see [DinnerCard]). */
@Composable
private fun CompactSlotCard(
    slot: MealSlot,
    label: String,
    planned: List<PlannedMeal>,
    onAddProductClick: () -> Unit,
    onAddMealClick: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onRemove: (PlannedMeal) -> Unit,
    onAddToShoppingList: (PlannedMeal) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(slot.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
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
            )
        }
        EmptySlotAddButton(onAddProductClick, onAddMealClick, contentDescription = stringResource(R.string.meal_plan_add_cd))
    }
}

/** A single 1dp-dashed, 24dp-icon "add" affordance — one plus per slot instead of the old
 *  side-by-side "+ Product"/"+ Recept" pair repeated in every slot. Tapping it opens
 *  [AddChooserDialog] to pick which of the two flows this tap actually meant. */
@Composable
private fun EmptySlotAddButton(onAddProductClick: () -> Unit, onAddMealClick: () -> Unit, contentDescription: String) {
    var showChooser by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .dashedBorder(MaterialTheme.colorScheme.outlineVariant, cornerRadius = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { showChooser = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircleOutline,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
    if (showChooser) {
        AddChooserDialog(
            onPickProduct = { showChooser = false; onAddProductClick() },
            onPickRecipe = { showChooser = false; onAddMealClick() },
            onDismiss = { showChooser = false },
        )
    }
}

/** Two labeled, icon-led rows — same shape as MoreScreen's Importeren/Exporteren dialog and
 *  ProductDetailScreen's PhotoDialog — for the one-plus-per-slot button's "product or recept?" choice. */
@Composable
private fun AddChooserDialog(onPickProduct: () -> Unit, onPickRecipe: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meal_plan_add_chooser_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onPickProduct).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.meal_plan_add_product), modifier = Modifier.padding(start = 12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onPickRecipe).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.meal_plan_add_recipe), modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * A recipe pick or a matched voorraad product may have [PlannedMeal.thumbnailUrl]; the compact
 * row deliberately doesn't show it (unlike the old thumbnail-led row this replaces) — avondeten's
 * own [DinnerCard] is now where the app's one photo-forward treatment lives, so every other slot
 * reads as a slim, scannable line instead: name, an optional "toevoegen aan boodschappenlijst"
 * button for a planned product that didn't match voorraad (see [PlannedMeal.isProduct]/
 * [PlannedMeal.productBarcode]'s doc), and remove. Still swipeable end-to-start to remove, same
 * as before, on top of the explicit "X" button.
 */
@Composable
private fun CompactPlannedRow(meal: PlannedMeal, onClick: () -> Unit, onRemove: () -> Unit, onAddToShoppingList: () -> Unit) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = meal.recipeId != null || meal.productBarcode != null, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = meal.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (meal.isProduct && meal.productBarcode == null) {
                IconButton(onClick = onAddToShoppingList, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.product_detail_add_to_shopping_list),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.meal_plan_clear_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
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

/** Offers both ways to add a meal to a slot: typing one by hand, or picking from recipe suggestions below it. */
@Composable
private fun MealPickerDialog(
    titleText: String,
    manualEntryText: String,
    onManualEntryTextChange: (String) -> Unit,
    onManualEntryAdd: () -> Unit,
    isLoading: Boolean,
    suggestions: List<RecipeSuggestion>,
    onSelect: (RecipeSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.meal_plan_manual_entry_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualEntryText,
                        onValueChange = onManualEntryTextChange,
                        placeholder = { Text(stringResource(R.string.meal_plan_manual_entry_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onManualEntryAdd,
                        enabled = manualEntryText.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.meal_plan_manual_entry_add))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = stringResource(R.string.meal_plan_suggestions_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
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
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(suggestions, key = { it.meal.id }) { suggestion ->
                            PickerRow(suggestion = suggestion, onClick = { onSelect(suggestion) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun PickerRow(suggestion: RecipeSuggestion, onClick: () -> Unit) {
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
        }
        Text(
            text = suggestion.meal.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}

/**
 * Offers both ways to add a product to a slot: typing a name (checked against voorraad on
 * submit — see [MealPlanViewModel.addManualProduct]), or picking one straight from the list
 * below, which is [inventoryItems] filtered live by whatever's typed so far — typing "melk"
 * narrows to matching voorraad items as a live preview of whether that exact product exists,
 * before even tapping "Toevoegen".
 */
@Composable
private fun ProductPickerDialog(
    titleText: String,
    entryText: String,
    onEntryTextChange: (String) -> Unit,
    onEntryAdd: () -> Unit,
    isLoading: Boolean,
    inventoryItems: List<InventoryItemWithProduct>,
    onSelect: (InventoryItemWithProduct) -> Unit,
    onDismiss: () -> Unit,
) {
    val matches = remember(entryText, inventoryItems) {
        val query = entryText.trim()
        if (query.isEmpty()) inventoryItems else inventoryItems.filter { it.name.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.meal_plan_product_entry_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = entryText,
                        onValueChange = onEntryTextChange,
                        placeholder = { Text(stringResource(R.string.meal_plan_product_entry_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onEntryAdd,
                        enabled = entryText.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.meal_plan_manual_entry_add))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = stringResource(R.string.meal_plan_product_suggestions_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    matches.isEmpty() -> Text(
                        text = stringResource(R.string.meal_plan_product_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(matches, key = { it.barcode }) { item ->
                            ProductPickerRow(item = item, onClick = { onSelect(item) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
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
