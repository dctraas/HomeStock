package com.dtraas.homestock.ui.mealplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.local.entity.PlannedMeal
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.repository.RecipeSuggestion
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

    // Tapping PlannedMealRow's "toevoegen aan boodschappenlijst" button otherwise gives no
    // feedback that anything happened — this confirms it, and says which of the two outcomes
    // (freshly added, or already there from an earlier tap/another source) it actually was.
    LaunchedEffect(Unit) {
        viewModel.shoppingListAddResult.collect { result ->
            val message = if (result.alreadyOnList) alreadyOnListFormat.format(result.name) else addedFormat.format(result.name)
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // Shared by both the "X" button and the swipe-to-delete gesture on PlannedMealRow — same
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

    val dateLabel = remember(uiState.date, locale) {
        val dayName = uiState.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
        val rest = uiState.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        "$dayName $rest"
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = viewModel::goToPreviousDay) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.meal_plan_previous_day_cd))
                        }
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = viewModel::goToNextDay) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.meal_plan_next_day_cd))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeekStatusStrip(
                selectedDate = uiState.date,
                weekStatus = uiState.weekStatus,
                onSelectDate = viewModel::selectDate,
            )

            // A slot with a planned recipe already has "Kookmodus" via each meal's own row
            // (tap through to RecipeDetailScreen, which already offers it) — this card is only
            // useful while the day still has an empty slot to fill, so it hides itself once
            // every slot has at least one meal.
            val firstEmptySlot = MealSlot.ORDERED.firstOrNull { uiState.plan[it].orEmpty().isEmpty() }
            if (firstEmptySlot != null) {
                WeekFillSuggestionCard(onClick = { viewModel.openPicker(firstEmptySlot) })
            }

            // Avondeten first and visually featured — the one meal of the day a household most
            // reliably plans ahead for — with the other three slots stacked below it in their
            // usual order. Still full-width stacked cards rather than a side-by-side compact
            // grid for those three (as the mockup shows): three cards holding a variable number
            // of planned meals each would size a LazyVerticalGrid row to its tallest cell,
            // which reads worse than just stacking them — a deliberate simplification.
            SlotCard(
                slot = MealSlot.DINNER,
                label = stringResource(MealSlot.DINNER.labelRes),
                planned = uiState.plan[MealSlot.DINNER].orEmpty(),
                isFeatured = true,
                onAddProductClick = { viewModel.openProductPicker(MealSlot.DINNER) },
                onAddMealClick = { viewModel.openPicker(MealSlot.DINNER) },
                onOpenRecipe = onRecipeClick,
                onOpenProduct = onProductClick,
                onRemove = { meal -> removeWithUndo(MealSlot.DINNER, meal) },
                onAddToShoppingList = { meal -> viewModel.addProductToShoppingList(meal.name) },
                onStartCookMode = onNavigateToCookMode,
            )
            MealSlot.ORDERED.filter { it != MealSlot.DINNER }.forEach { slot ->
                SlotCard(
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

/**
 * Monday-start 7-day strip for jumping straight to any day in the current week, rather than
 * only stepping one day at a time via the top bar's chevrons — the selected day gets a filled
 * circle, and any day [MealPlanUiState.weekStatus] reports as having at least one planned meal
 * gets a small dot underneath (see [MealPlanViewModel.loadWeekStatus] for what that status
 * actually reflects and how fresh it is).
 */
@Composable
private fun WeekStatusStrip(selectedDate: LocalDate, weekStatus: Map<LocalDate, Boolean>, onSelectDate: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val weekStart = remember(selectedDate) { selectedDate.with(DayOfWeek.MONDAY) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            val isSelected = date == selectedDate
            val hasPlan = weekStatus[date] == true
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectDate(date) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.size(32.dp).padding(top = 2.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (hasPlan) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

/**
 * "Vul de week uit je voorraad" — a shortcut into the same inventory-matched recipe suggestions
 * [MealPlanViewModel.openPicker] already offers, for the first slot the currently viewed day
 * still has nothing planned in. Hidden once every slot has at least one meal (see its call
 * site) rather than a literal whole-week auto-fill, which nothing in MealPlanRepository builds
 * yet — a deliberate scope simplification of the mockup's copy.
 */
@Composable
private fun WeekFillSuggestionCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        shape = SoftCardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.meal_plan_fill_week_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.meal_plan_fill_week_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Recognizable icon per meal slot, shown in the card header next to its label. */
private val MealSlot.icon: ImageVector
    get() = when (this) {
        MealSlot.BREAKFAST -> Icons.Filled.FreeBreakfast
        MealSlot.LUNCH -> Icons.Filled.LunchDining
        MealSlot.DINNER -> Icons.Filled.DinnerDining
        MealSlot.SNACK -> Icons.Filled.Cookie
    }

/**
 * A slot can now hold zero, one, or several planned meals — a household may want more than
 * one dish lined up for e.g. avondeten — so this renders one row per [PlannedMeal] plus two
 * "add" buttons side by side that are always present, rather than a single card that's either
 * "empty" or "has one recipe". A planned meal is clickable through to the recipe detail screen
 * if it has a [PlannedMeal.recipeId], or the product detail screen if it has a
 * [PlannedMeal.productBarcode] — a hand-typed name with neither has nothing to navigate to.
 * Each meal gets its own small background chip (see [PlannedMealRow]) rather than being
 * separated by a thin divider — that reads as one continuous, cramped list once a slot holds
 * several meals; a stack of clearly bounded rows scales much better to "two, three, four
 * snacks" than a flat list does.
 */
@Composable
private fun SlotCard(
    slot: MealSlot,
    label: String,
    planned: List<PlannedMeal>,
    onAddProductClick: () -> Unit,
    onAddMealClick: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onRemove: (PlannedMeal) -> Unit,
    onAddToShoppingList: (PlannedMeal) -> Unit,
    // Avondeten's own card (see MealPlanScreen's call site) — a bolder tinted header and
    // larger label/icon than the other three slots get, plus a "Kookmodus" shortcut once a
    // recipe is planned, since it's the one meal of the day households most reliably plan
    // ahead for and cook from a recipe.
    isFeatured: Boolean = false,
    onStartCookMode: ((String) -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFeatured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Extra bottom padding on top of the Column's own 8dp spacedBy gap — just this one
            // gap, between the slot title (Ontbijt/Lunch/...) and whatever follows it, reads
            // tighter than the rest of the card's spacing otherwise.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = slot.icon,
                        contentDescription = null,
                        tint = if (isFeatured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isFeatured) 26.dp else 20.dp),
                    )
                    Text(
                        text = label,
                        style = if (isFeatured) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                        color = if (isFeatured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // Only meaningful once a recipe (not a plain product/hand-typed name) is
                // planned — the first one found, in the rare case a household stacks more than
                // one recipe into avondeten.
                val cookableRecipeId = planned.firstOrNull { it.recipeId != null }?.recipeId
                if (isFeatured && onStartCookMode != null && cookableRecipeId != null) {
                    TextButton(onClick = { onStartCookMode(cookableRecipeId) }) {
                        Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.meal_plan_start_cook_mode), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (planned.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    planned.forEach { meal ->
                        PlannedMealRow(
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
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddRow(
                        label = stringResource(R.string.meal_plan_add_product),
                        onClick = onAddProductClick,
                        modifier = Modifier.weight(1f),
                    )
                    AddRow(
                        label = stringResource(R.string.meal_plan_add_recipe),
                        onClick = onAddMealClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // A slot with nothing planned used to look the same as one that did, minus a
                // thumbnail — both had the exact same "+ Product / + Recept" row, just with or
                // without a PlannedMealRow above it, so an empty slot and a filled one only
                // really differed if you read the card closely. This gives "nothing here yet"
                // its own clearly different look instead, with the same two actions inside it.
                EmptySlot(onAddProductClick = onAddProductClick, onAddMealClick = onAddMealClick)
            }
        }
    }
}

@Composable
private fun EmptySlot(onAddProductClick: () -> Unit, onAddMealClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.meal_plan_empty_slot_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddRow(
                label = stringResource(R.string.meal_plan_add_product),
                onClick = onAddProductClick,
                modifier = Modifier.weight(1f),
            )
            AddRow(
                label = stringResource(R.string.meal_plan_add_recipe),
                onClick = onAddMealClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One of [SlotCard]'s two "add" buttons, side by side — inside [EmptySlot] when the slot has
 * nothing planned yet, or directly below the planned meals otherwise. Both share the same plain
 * "+" icon — the label text ("Product" vs. "Recept") is what tells them apart, not the icon.
 */
@Composable
private fun AddRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(SoftImageShape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * A recipe pick or a matched voorraad product may have [PlannedMeal.thumbnailUrl] (from the
 * recipe database or the product's own photo); a manually typed name never does. Rather than
 * just skipping the image slot for those — which used to leave such entries looking bare and
 * unfinished next to a recipe's photo, right when the user asked for this to look nicer
 * specifically for hand-typed meals — they get a colored fallback badge instead, the same
 * "always a visual, real photo or otherwise" treatment ProductImage gives products with no
 * picture elsewhere in the app. The fallback icon itself tells the two non-recipe kinds apart:
 * a shopping-basket icon for any planned product ([PlannedMeal.isProduct] true, whether or not
 * it matched voorraad), fork-and-knife for a plain hand-typed "Recept" name.
 *
 * A product that didn't match voorraad ([PlannedMeal.isProduct] true, [PlannedMeal.productBarcode]
 * null) gets a persistent "toevoegen aan boodschappenlijst" button next to the remove button —
 * available any time, not just right after adding, since the household might only decide it's
 * needed later. Every other kind of entry has nothing to buy, so the button is left out rather
 * than disabled — a recipe/product already in voorraad, or a plain dish name, was never a
 * "missing" thing to shop for in the first place.
 *
 * Sits on its own subtly-tinted background (rather than plain text on the card, separated only
 * by a divider) so each meal reads as one clearly bounded item — this is what keeps a slot with
 * several meals looking like a tidy little stack instead of a run-on list (see [SlotCard]).
 * The thumbnail matches RecipeRow's own size (56dp) rather than ShoppingListRow's smaller 32dp —
 * a planned meal's photo is the whole reason to recognize it at a glance, the same reasoning
 * that already applies to the recipe list this meal was very possibly picked from.
 *
 * Swipeable end-to-start (left, in LTR) to remove, same direction/trash-can treatment as
 * Boodschappenlijst/Voorraad's rows — on top of, not instead of, the explicit "X" button above,
 * since not everyone reaches for a swipe gesture first. `.clip(SoftImageShape)` sits on the
 * [SwipeToDismissBox] itself rather than just the row inside it — the same fix Boodschappenlijst/
 * Voorraad needed for their own swipe rows, since otherwise the errorContainer background can
 * bleed past the row's rounded corners at rest.
 */
@Composable
private fun PlannedMealRow(meal: PlannedMeal, onClick: () -> Unit, onRemove: () -> Unit, onAddToShoppingList: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRemove()
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth().clip(SoftImageShape),
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(enabled = meal.recipeId != null || meal.productBarcode != null, onClick = onClick)
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (meal.thumbnailUrl != null) {
                AsyncImage(
                    model = meal.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (meal.isProduct) Icons.Filled.Inventory2 else Icons.Filled.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            Text(
                text = meal.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            if (meal.isProduct && meal.productBarcode == null) {
                IconButton(onClick = onAddToShoppingList, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.product_detail_add_to_shopping_list),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.meal_plan_clear_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
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
