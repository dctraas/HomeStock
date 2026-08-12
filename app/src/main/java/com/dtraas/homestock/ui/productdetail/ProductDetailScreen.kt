package com.dtraas.homestock.ui.productdetail

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.NutritionInfo
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.DietLabel
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.CategoryDropdown
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.theme.SoftCardShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    barcode: String,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: ProductDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProductDetailViewModel(
                    barcode = barcode,
                    productRepository = application.container.productRepository,
                    inventoryRepository = application.container.inventoryRepository,
                    shoppingListRepository = application.container.shoppingListRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val isRetryingLookup by viewModel.isRetryingLookup.collectAsState()
    val stillInInventory = uiState.quantityInInventory != null

    val snackbarHostState = remember { SnackbarHostState() }
    val restockedFormat = stringResource(R.string.inventory_restocked_snackbar_format)
    val retryLookupSuccessMessage = stringResource(R.string.product_detail_retry_lookup_success)
    val retryLookupFailureMessage = stringResource(R.string.product_detail_retry_lookup_failure)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Collapsed by default — the plus icon on each header expands it. Product details also
    // starts collapsed; the edit button in the top app bar (see below) expands it and scrolls
    // it into view, which doubles as this screen's "product details page". Every section uses
    // the same [ExpandableSection] helper so expanding *any* of them (not just Product
    // details, which used to be the only one that did this) scrolls its newly-revealed bottom
    // into view — a long card like Voedingswaarden or Ingrediënten no longer gets left cut off
    // below the fold after tapping its "+".
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val productDetailsSection = remember { ExpandableSection(scrollState, coroutineScope) }
    val nutritionSection = remember { ExpandableSection(scrollState, coroutineScope) }
    val ingredientsSection = remember { ExpandableSection(scrollState, coroutineScope) }
    val allergensSection = remember { ExpandableSection(scrollState, coroutineScope) }
    val dietLabelsSection = remember { ExpandableSection(scrollState, coroutineScope) }

    LaunchedEffect(Unit) {
        viewModel.restockEvents.collect { name ->
            snackbarHostState.showSnackbar(restockedFormat.format(name), duration = SnackbarDuration.Short)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.retryLookupSucceeded.collect { succeeded ->
            val message = if (succeeded) retryLookupSuccessMessage else retryLookupFailureMessage
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                // The product's own name is already shown prominently below the hero image
                // right under it — repeating it as the title bar text was redundant, so this
                // stays a generic label instead.
                title = { Text(stringResource(R.string.product_detail_default_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Toevoegen aan boodschappenlijst / Verwijderen / Bewerken now sit together
                    // on the "Voorraad" heading row below instead (right-aligned, equal sizing)
                    // so they're grouped with the stock controls they act on. This top-bar slot
                    // stays only as a fallback for products no longer in inventory, where there
                    // is no Voorraad row to anchor them to.
                    if (!stillInInventory && uiState.product != null) {
                        IconButton(onClick = { productDetailsSection.expand() }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.product_detail_edit_cd))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val product = uiState.product
        val category = Category.fromStorageKey(product?.category)
        val allergens = product?.allergens?.mapNotNull { name -> Allergen.entries.find { it.name == name } }.orEmpty()
        val dietLabels = product?.dietLabels?.mapNotNull { name -> DietLabel.entries.find { it.name == name } }.orEmpty()
        val hasNutritionInfo = product?.nutrition != null || product?.ingredients != null ||
            allergens.isNotEmpty() || dietLabels.isNotEmpty()
        // Products entered manually (barcode not found online) never get a photo or
        // nutrition data — this is how we tell those apart from a genuine OFF match
        // that simply doesn't have all fields, to offer a "look it up again" retry.
        val looksManuallyEntered = product != null && product.imageUrl == null && !hasNutritionInfo

        // Gap before each section header (Voorraad, Voedingswaarden, Ingrediënten, ...) and
        // between a header and its own card below it — shared so e.g. "Voorraad" -> its card
        // and "Ingrediënten" -> its card line up at the same distance.
        val sectionGap = 24.dp
        val headerToCardGap = 12.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            ProductHero(
                product = product,
                category = category,
                showFavorite = stillInInventory,
                isFavorite = uiState.isFavorite,
                onToggleFavorite = viewModel::toggleFavorite,
            )
            Text(
                text = product?.name ?: stringResource(R.string.product_detail_default_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            product?.brand?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip(category)
                val nutriScoreGrade = product?.nutriScoreGrade
                if (nutriScoreGrade != null) {
                    GradeBadge(
                        nutriScoreGrade,
                        stringResource(R.string.product_detail_nutriscore_format, nutriScoreGrade.uppercase(Locale.ROOT)),
                    )
                } else if (product != null) {
                    // No Nutri-Score data for this product — show an explicit "unknown" badge
                    // instead of silently omitting it, so it's clear this isn't a loading gap.
                    NutriScoreUnavailableBadge(stringResource(R.string.product_detail_nutriscore_unavailable))
                }
            }

            if (looksManuallyEntered) {
                TextButton(
                    onClick = viewModel::retryLookup,
                    enabled = !isRetryingLookup,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (isRetryingLookup) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.product_detail_retry_lookup))
                }
            }

            // Voorraad — a bit more breathing room than the other section headers get,
            // specifically because it sits right under the category/Nutri-Score icon row
            // rather than under a card like the rest do.
            if (stillInInventory) {
                // Toevoegen aan boodschappenlijst / Bewerken / Verwijderen live here, right-
                // aligned against the "Voorraad" heading — they all act on this product's stock,
                // so this groups them with the section they belong to instead of the generic top
                // bar. All three share the same 36dp footprint / 20dp glyph so none reads heavier
                // than the others.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = sectionGap + 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.section_stock),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::addToShoppingList, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.PlaylistAdd,
                                contentDescription = stringResource(R.string.product_detail_add_to_shopping_list),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (uiState.product != null) {
                            IconButton(
                                onClick = { productDetailsSection.expand() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.product_detail_edit_cd),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.product_detail_remove),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = headerToCardGap),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = SoftCardShape,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.product_detail_in_stock), style = MaterialTheme.typography.bodyLarge)
                            QuantityStepper(
                                quantity = uiState.quantityInInventory ?: 0,
                                onDecrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 1) - 1) },
                                onIncrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 0) + 1) },
                                dense = true,
                            )
                        }

                        ExpirationStatusRow(expirationDate = uiState.expirationDate)

                        ExpirationRow(
                            expirationDate = uiState.expirationDate,
                            onDateChange = viewModel::setExpirationDate,
                        )

                        MinQuantityRow(
                            minQuantity = uiState.minQuantity,
                            onChange = viewModel::setMinQuantity,
                        )
                    }
                }
            }

            // Product details — editable name/brand/category/unit, collapsed by default. The
            // edit button in the top app bar (where the favorite star used to be) expands this
            // and scrolls all the way to its bottom rather than navigating to a separate
            // screen, since it already lives right here.
            product?.let { p ->
                CollapsibleSectionHeader(
                    title = stringResource(R.string.product_detail_editable_title),
                    expanded = productDetailsSection.expanded,
                    onToggle = productDetailsSection::toggle,
                    modifier = Modifier.padding(top = sectionGap),
                )
                if (productDetailsSection.expanded) {
                    ProductDetailsCard(
                        product = p,
                        category = category,
                        onNameChange = viewModel::updateName,
                        onBrandChange = viewModel::updateBrand,
                        onCategoryChange = viewModel::updateCategory,
                        onUnitChange = viewModel::updateUnit,
                        showNote = stillInInventory,
                        note = uiState.note,
                        onNoteChange = viewModel::setNote,
                        modifier = Modifier.padding(top = headerToCardGap),
                    )
                }
                // Zero-height marker right after the card — its Y offset within the scrollable
                // Column (see [ExpandableSection.bottomOffset]) is what expanding this section
                // scrolls to, so this needs to sit past the card's very last field.
                Spacer(modifier = Modifier.onGloballyPositioned { productDetailsSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }

            // Voedingsinformatie — no overarching group header; each card is its own
            // section with its own header, same treatment as Voorraad above. Collapsed by
            // default, uitklappen via het plusje.
            product?.nutrition?.let { nutrition ->
                CollapsibleSectionHeader(
                    title = stringResource(R.string.product_detail_nutrition_title),
                    expanded = nutritionSection.expanded,
                    onToggle = nutritionSection::toggle,
                    modifier = Modifier.padding(top = sectionGap),
                )
                if (nutritionSection.expanded) {
                    NutritionCard(nutrition, modifier = Modifier.padding(top = headerToCardGap))
                }
                Spacer(modifier = Modifier.onGloballyPositioned { nutritionSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }
            product?.ingredients?.let { ingredients ->
                CollapsibleSectionHeader(
                    title = stringResource(R.string.product_detail_ingredients_title),
                    expanded = ingredientsSection.expanded,
                    onToggle = ingredientsSection::toggle,
                    modifier = Modifier.padding(top = sectionGap),
                )
                if (ingredientsSection.expanded) {
                    IngredientsCard(ingredients, modifier = Modifier.padding(top = headerToCardGap))
                }
                Spacer(modifier = Modifier.onGloballyPositioned { ingredientsSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }
            if (allergens.isNotEmpty()) {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.product_detail_allergens_title),
                    expanded = allergensSection.expanded,
                    onToggle = allergensSection::toggle,
                    modifier = Modifier.padding(top = sectionGap),
                )
                if (allergensSection.expanded) {
                    AllergensCard(allergens, modifier = Modifier.padding(top = headerToCardGap))
                }
                Spacer(modifier = Modifier.onGloballyPositioned { allergensSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }
            if (dietLabels.isNotEmpty()) {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.product_detail_diet_labels_title),
                    expanded = dietLabelsSection.expanded,
                    onToggle = dietLabelsSection::toggle,
                    modifier = Modifier.padding(top = sectionGap),
                )
                if (dietLabelsSection.expanded) {
                    DietLabelsCard(dietLabels, modifier = Modifier.padding(top = headerToCardGap))
                }
                Spacer(modifier = Modifier.onGloballyPositioned { dietLabelsSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.product_detail_delete_dialog_title)) },
                text = { Text(stringResource(R.string.product_detail_delete_dialog_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            viewModel.removeFromInventory()
                            onBack()
                        },
                    ) { Text(stringResource(R.string.product_detail_remove), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
                },
            )
        }
    }
}

/**
 * Tracks one collapsible section's expand state and where its bottom sits within the
 * scrollable Column (via a zero-height marker `Spacer`/`onGloballyPositioned` placed right
 * after the section, see the call sites above) — [toggle]/[expand] flip it on, and expanding
 * also scrolls the section's newly-revealed bottom into view so a long card (Voedingswaarden,
 * Ingrediënten, ...) never gets left cut off below the fold after tapping its "+". The
 * two-frame delay before scrolling lets the expand's own recomposition/layout pass actually
 * update [bottomOffset] first — scrolling synchronously from the click handler would still see
 * the section's old (collapsed) position.
 */
private class ExpandableSection(private val scrollState: ScrollState, private val coroutineScope: CoroutineScope) {
    var expanded by mutableStateOf(false)
        private set
    var bottomOffset by mutableStateOf(0)

    fun toggle() {
        if (expanded) collapse() else expand()
    }

    fun collapse() {
        expanded = false
    }

    fun expand() {
        expanded = true
        coroutineScope.launch {
            withFrameNanos {}
            withFrameNanos {}
            scrollState.animateScrollTo(bottomOffset)
        }
    }
}

/** A plain section title, with a plus/minus toggle to expand or collapse the section below it. */
@Composable
private fun CollapsibleSectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.Remove else Icons.Filled.Add,
            contentDescription = stringResource(
                if (expanded) R.string.product_detail_collapse_cd else R.string.product_detail_expand_cd,
            ),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ProductHero(
    product: ProductEntity?,
    category: Category,
    showFavorite: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val imageUrl = product?.imageUrl
    Box(modifier = Modifier.size(160.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = product?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        // The favorite toggle used to sit in the top app bar; that slot now hosts the edit
        // button, so favorite moved to a badge on the hero image instead — a common spot for
        // a "favorite this" affordance, and it stays close to the product it applies to.
        if (showFavorite) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp),
            ) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.inventory_unmark_favorite_cd else R.string.inventory_mark_favorite_cd,
                        ),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: Category) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(category.displayNameRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Nutri-Score uses an A (best) to E (worst) grading scale. */
private fun gradeColor(grade: String): Color = when (grade.uppercase(Locale.ROOT)) {
    "A" -> Color(0xFF038141)
    "B" -> Color(0xFF85BB2F)
    "C" -> Color(0xFFFECB02)
    "D" -> Color(0xFFEE8100)
    "E" -> Color(0xFFE63E11)
    else -> Color.Gray
}

@Composable
private fun GradeBadge(grade: String, contentDescription: String) {
    Surface(
        shape = CircleShape,
        color = gradeColor(grade),
        modifier = Modifier
            .size(32.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = grade.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
    }
}

/** Shown in place of [GradeBadge] when a product has no known Nutri-Score, so the absence of
 *  data reads as an explicit "unknown" state rather than a blank gap next to the category chip. */
@Composable
private fun NutriScoreUnavailableBadge(contentDescription: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(32.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Filled.HelpOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductDetailsCard(
    product: ProductEntity,
    category: Category,
    onNameChange: (String) -> Unit,
    onBrandChange: (String?) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onUnitChange: (String?) -> Unit,
    showNote: Boolean,
    note: String?,
    onNoteChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(product.barcode) { mutableStateOf(product.name) }
    var brand by remember(product.barcode) { mutableStateOf(product.brand ?: "") }
    var unit by remember(product.barcode) { mutableStateOf(product.unit ?: "") }
    // Keyed on the note itself (not just the barcode) — unlike name/brand/unit, this field
    // lives on the inventory entry rather than the catalog product, so it can legitimately
    // change from outside this card (e.g. removed from inventory) while it stays mounted.
    var noteText by remember(note) { mutableStateOf(note ?: "") }

    // Debounced autosave per field: writes shortly after typing pauses instead of on every
    // keystroke or only once the field loses focus (which back-navigation can't reliably catch).
    LaunchedEffect(name) {
        delay(600)
        if (name != product.name) onNameChange(name)
    }
    LaunchedEffect(brand) {
        delay(600)
        if (brand != (product.brand ?: "")) onBrandChange(brand.trim().ifBlank { null })
    }
    LaunchedEffect(unit) {
        delay(600)
        if (unit != (product.unit ?: "")) onUnitChange(unit.trim().ifBlank { null })
    }
    LaunchedEffect(noteText) {
        delay(600)
        if (noteText != (note ?: "")) onNoteChange(noteText.trim().ifBlank { null })
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text(stringResource(R.string.product_detail_field_brand)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryDropdown(
                selected = category,
                onSelected = onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text(stringResource(R.string.product_detail_field_unit)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Note is a field on the inventory entry, not the catalog product — nothing to
            // save it against for a product that isn't (or no longer) in stock.
            //
            // Deliberately NOT using OutlinedTextField's own floating `label` here like the
            // fields above do: with both `label` and `placeholder` set, Material3 only shows
            // the placeholder once the field is focused (the unfocused, empty label sits
            // exactly where the placeholder would go) — so the title would only sometimes be
            // the small floated label, and the example text would stay hidden until tapped.
            // A persistent title above the field keeps both visible at all times instead.
            if (showNote) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.product_detail_note_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text(stringResource(R.string.product_detail_note_placeholder)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AllergensCard(allergens: List<Allergen>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(allergens) { allergen ->
                    TagChip(
                        label = stringResource(allergen.labelRes),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun DietLabelsCard(labels: List<DietLabel>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(labels) { label ->
                    TagChip(
                        label = stringResource(label.labelRes),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(label: String, color: Color, contentColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun IngredientsCard(ingredients: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = ingredients,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutritionCard(nutrition: NutritionInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            nutrition.energyKcal100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_energy), formatKcal(it))
            }
            nutrition.fat100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_fat), formatGrams(it))
            }
            nutrition.saturatedFat100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_saturated_fat), formatGrams(it), indented = true)
            }
            nutrition.carbohydrates100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_carbohydrates), formatGrams(it))
            }
            nutrition.sugars100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_sugars), formatGrams(it), indented = true)
            }
            nutrition.fiber100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_fiber), formatGrams(it))
            }
            nutrition.proteins100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_proteins), formatGrams(it))
            }
            nutrition.salt100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_salt), formatGrams(it))
            }
        }
    }
}

@Composable
private fun NutritionRow(label: String, value: String, indented: Boolean = false) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = if (indented) 12.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (indented) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = if (indented) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = if (indented) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = if (indented) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatKcal(value: Double): String = String.format(Locale.getDefault(), "%.0f kcal", value)
private fun formatGrams(value: Double): String = String.format(Locale.getDefault(), "%.1f g", value)

private fun formatExpirationDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(date)
}

private fun daysUntilExpiration(millis: Long): Long {
    // The DatePicker below always encodes the picked calendar day as UTC midnight (a Compose
    // Material3 convention, unrelated to the device's timezone), so decoding it must stay in
    // UTC — but "today" has to be the device's actual local date, or this drifts by a day
    // right around local midnight for anyone not in UTC.
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return ChronoUnit.DAYS.between(LocalDate.now(), date)
}

@Composable
private fun ExpirationStatusRow(expirationDate: Long?) {
    val days = expirationDate?.let { daysUntilExpiration(it) }
    val (label, isWarning) = when {
        days == null -> stringResource(R.string.product_detail_status_not_set) to false
        days < 0 -> stringResource(R.string.product_detail_status_expired) to true
        days == 0L -> stringResource(R.string.product_detail_status_today) to true
        days == 1L -> stringResource(R.string.product_detail_status_one_day) to true
        days <= 3 -> stringResource(R.string.product_detail_status_days_format, days) to true
        else -> stringResource(R.string.product_detail_status_fresh) to false
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.product_detail_status_label), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpirationRow(expirationDate: Long?, onDateChange: (Long?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val isNearExpiry = expirationDate != null && daysUntilExpiration(expirationDate) <= 3

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.product_detail_expiration_label), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = expirationDate?.let { formatExpirationDate(it) }
                    ?: stringResource(R.string.product_detail_expiration_set),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isNearExpiry) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showPicker = true },
            )
            if (expirationDate != null) {
                IconButton(onClick = { onDateChange(null) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.product_detail_expiration_clear_cd),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPicker) {
        // The picker encodes every candidate date as UTC midnight, so the cutoff below
        // must be expressed the same way to correctly compare against it.
        val todayUtcMillis = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = expirationDate,
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateChange(state.selectedDateMillis)
                    showPicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun MinQuantityRow(minQuantity: Int?, onChange: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.product_detail_min_quantity_label), style = MaterialTheme.typography.bodyLarge)
        QuantityStepper(
            quantity = minQuantity ?: 0,
            onDecrease = {
                val next = (minQuantity ?: 0) - 1
                onChange(if (next <= 0) null else next)
            },
            onIncrease = { onChange((minQuantity ?: 0) + 1) },
            minQuantity = 0,
            dense = true,
        )
    }
}

