package com.dtraas.homestock.ui.productdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.NutritionInfo
import com.dtraas.homestock.data.local.entity.PricePoint
import com.dtraas.homestock.data.local.entity.ProductEntity
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.DietLabel
import com.dtraas.homestock.ui.components.CategoryDropdown
import com.dtraas.homestock.ui.components.LocationDropdown
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.TopAppBarContainerGradientEnd
import com.dtraas.homestock.ui.theme.UrgencyTileShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    barcode: String,
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit = {},
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
    // Custom product photos (see below) are a Premium feature, same gating pattern as
    // Statistieken/Recepten/etc — checked here rather than in the repository, since a photo
    // upload is a plain Firestore/Storage write with no server-side enforcement point.
    val isPremium by application.container.householdMembersRepository
        .observeHouseholdIsPremium()
        .collectAsState(initial = false)

    val snackbarHostState = remember { SnackbarHostState() }
    val restockedFormat = stringResource(R.string.inventory_restocked_snackbar_format)
    val retryLookupSuccessMessage = stringResource(R.string.product_detail_retry_lookup_success)
    val retryLookupFailureMessage = stringResource(R.string.product_detail_retry_lookup_failure)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::uploadCustomPhoto) }

    // Collapsed by default — the plus icon on each header expands it. Product details also
    // starts collapsed; the edit button in the overflow menu (see below) expands it and scrolls
    // it into view, which doubles as this screen's "product details page". Every section uses
    // the same [ExpandableSection] helper so expanding *any* of them scrolls its newly-revealed
    // bottom into view — a long card never gets left cut off below the fold after tapping it.
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    // Nutrition + ingredients now share one detail row/section (see [DetailRow] below), as do
    // allergens + diet labels — the design review's "three chevron rows" replaces what used to
    // be four separate accordions.
    val nutritionSection = remember { ExpandableSection(scrollState, coroutineScope) }
    val allergensSection = remember { ExpandableSection(scrollState, coroutineScope) }
    val productDetailsSection = remember { ExpandableSection(scrollState, coroutineScope) }

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
        // ProductDetailHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
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

        // Gap before each detail row and between a header and the card/content below it.
        val sectionGap = 20.dp
        val headerToCardGap = 12.dp

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The green header now runs all the way down through the image/title/subtitle
            // row and the "Nog 2 dagen" status pill — it used to stop at a plain top app bar,
            // leaving that whole row (houdbaarheid included) on white, scrolling background.
            // The stock card below still overlaps its bottom edge by design (see its own
            // offset(y = -14.dp)), but now that overlap lands on the header's own reserved
            // bottom padding instead of on top of the status pill, which is what made the
            // pill unreadable before.
            ProductDetailHeader(
                product = product,
                category = category,
                stillInInventory = stillInInventory,
                expirationDate = uiState.expirationDate,
                isFavorite = uiState.isFavorite,
                looksManuallyEntered = looksManuallyEntered,
                isRetryingLookup = isRetryingLookup,
                onBack = onBack,
                onToggleFavorite = viewModel::toggleFavorite,
                onEditPhotoClick = { if (isPremium) showPhotoDialog = true else onNavigateToPremium() },
                onEditDetailsClick = { productDetailsSection.expand() },
                onRetryLookupClick = viewModel::retryLookup,
                onDeleteClick = { showDeleteConfirm = true },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            ) {
            if (stillInInventory) {
                // Overlaps the header row by design — a bit of visual overlap between the hero
                // area and the stock card, per the review.
                StockCard(
                    quantity = uiState.quantityInInventory ?: 0,
                    unit = product?.unit,
                    minQuantity = uiState.minQuantity,
                    onDecrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 1) - 1) },
                    onIncrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 0) + 1) },
                    // offset, not padding: Modifier.padding() rejects negative values outright
                    // (throws IllegalArgumentException), while offset shifts the draw position
                    // without that restriction — the only way to get this intentional overlap.
                    modifier = Modifier.offset(y = (-14).dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = viewModel::addToShoppingList,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.product_detail_add_to_list_button))
                    }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.product_detail_mark_used_up))
                    }
                }

                Text(
                    text = stringResource(R.string.product_detail_expiration_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = sectionGap),
                )
                ExpirationSection(
                    expirationDate = uiState.expirationDate,
                    category = category,
                    onDateChange = viewModel::setExpirationDate,
                    isPremium = isPremium,
                    onNavigateToPremium = onNavigateToPremium,
                    modifier = Modifier.padding(top = headerToCardGap),
                )
            }

            // Stat tiles — independent of whether the product is still in the current
            // inventory, since both price history and scan history outlive a removal.
            val priceDeltaCaption = product?.priceHistory?.takeIf { it.size > 1 }?.let { history ->
                val delta = history[0].price - history[1].price
                val sign = if (delta >= 0) "+" else "-"
                val monthName = Instant.ofEpochMilli(history[1].date).atZone(ZoneId.systemDefault()).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
                stringResource(R.string.product_detail_stat_price_delta_format, "$sign${formatPrice(abs(delta))}", monthName)
            }
            val scanFrequencyCaption = uiState.avgDaysBetweenScans?.let {
                pluralStringResource(R.plurals.product_detail_stat_scan_frequency_days, it, it)
            }
            val showLastPaidTile = product?.lastPrice != null
            val showScannedTile = uiState.scanCount > 0
            if (showLastPaidTile || showScannedTile) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = sectionGap),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showLastPaidTile) {
                        StatTile(
                            eyebrow = stringResource(R.string.product_detail_field_last_price),
                            value = formatPrice(product?.lastPrice ?: 0.0),
                            caption = priceDeltaCaption,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (showScannedTile) {
                        StatTile(
                            eyebrow = stringResource(R.string.product_detail_stat_scanned_label),
                            value = stringResource(R.string.product_detail_stat_scan_count_format, uiState.scanCount),
                            caption = scanFrequencyCaption,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Detail rows — three chevron rows with dividers, replacing what used to be up to
            // four separate expand/collapse accordions.
            val hasNutritionOrIngredients = product?.nutrition != null || product?.ingredients != null
            if (hasNutritionOrIngredients) {
                DetailRow(
                    title = stringResource(R.string.product_detail_nutrition_ingredients_title),
                    expanded = nutritionSection.expanded,
                    onToggle = nutritionSection::toggle,
                    modifier = Modifier.padding(top = sectionGap),
                )
                if (nutritionSection.expanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = headerToCardGap),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        product?.nutrition?.let { NutritionCard(it) }
                        product?.ingredients?.let { IngredientsCard(it) }
                    }
                }
                Spacer(modifier = Modifier.onGloballyPositioned { nutritionSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }

            val hasAllergensOrDiet = allergens.isNotEmpty() || dietLabels.isNotEmpty()
            if (hasAllergensOrDiet) {
                DetailRow(
                    title = stringResource(R.string.product_detail_allergens_diet_title),
                    expanded = allergensSection.expanded,
                    onToggle = allergensSection::toggle,
                    modifier = Modifier.padding(top = sectionGap),
                )
                if (allergensSection.expanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = headerToCardGap),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (allergens.isNotEmpty()) AllergensCard(allergens)
                        if (dietLabels.isNotEmpty()) DietLabelsCard(dietLabels)
                    }
                }
                Spacer(modifier = Modifier.onGloballyPositioned { allergensSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }

            product?.let { p ->
                DetailRow(
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
                        onLocationChange = viewModel::updateLocation,
                        showInventoryFields = stillInInventory,
                        note = uiState.note,
                        onNoteChange = viewModel::setNote,
                        minQuantity = uiState.minQuantity,
                        onMinQuantityChange = viewModel::setMinQuantity,
                        lastPrice = p.lastPrice,
                        priceHistory = p.priceHistory,
                        onPriceChange = viewModel::setPrice,
                        modifier = Modifier.padding(top = headerToCardGap),
                    )
                }
                // Zero-height marker right after the card — its Y offset within the scrollable
                // Column (see [ExpandableSection.bottomOffset]) is what expanding this section
                // scrolls to, so this needs to sit past the card's very last field.
                Spacer(modifier = Modifier.onGloballyPositioned { productDetailsSection.bottomOffset = it.positionInParent().y.roundToInt() })
            }
            }
        }

        if (showDeleteConfirm) {
            // Only near/past its expiration date is a removal ambiguous enough to ask about —
            // otherwise it's almost certainly ordinary consumption, so don't add a question
            // nobody needs. Same <= 3 days threshold used for isNearExpiry elsewhere.
            val isNearExpiry = uiState.expirationDate?.let { daysUntilExpiration(it) <= 3 } ?: false
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.product_detail_delete_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            if (isNearExpiry) R.string.product_detail_delete_dialog_text_wasted_prompt
                            else R.string.product_detail_delete_dialog_text
                        )
                    )
                },
                confirmButton = {
                    if (isNearExpiry) {
                        Row {
                            TextButton(
                                onClick = {
                                    showDeleteConfirm = false
                                    viewModel.removeFromInventory(wasted = false)
                                    onBack()
                                },
                            ) { Text(stringResource(R.string.product_detail_delete_used_up)) }
                            TextButton(
                                onClick = {
                                    showDeleteConfirm = false
                                    viewModel.removeFromInventory(wasted = true)
                                    onBack()
                                },
                            ) { Text(stringResource(R.string.product_detail_delete_wasted), color = MaterialTheme.colorScheme.error) }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                viewModel.removeFromInventory()
                                onBack()
                            },
                        ) { Text(stringResource(R.string.product_detail_remove), color = MaterialTheme.colorScheme.error) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
                },
            )
        }

        if (showPhotoDialog) {
            PhotoDialog(
                hasCustomPhoto = product?.imageUrl != null,
                onPickPhoto = {
                    showPhotoDialog = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemovePhoto = {
                    showPhotoDialog = false
                    viewModel.removeCustomPhoto()
                },
                onDismiss = { showPhotoDialog = false },
            )
        }
    }
}

/** Choice dialog opened by the photo badge / overflow menu — mirrors MoreScreen's
 *  Importeren/Exporteren dialog shape (two labeled, icon-led rows) for a consistent
 *  "tap an icon, get a small menu of actions" pattern across the app. */
@Composable
private fun PhotoDialog(
    hasCustomPhoto: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_detail_edit_photo_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickPhoto)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.product_detail_choose_photo_action), modifier = Modifier.padding(start = 12.dp))
                }
                if (hasCustomPhoto) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRemovePhoto)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = stringResource(R.string.product_detail_remove_photo_action),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Tracks one collapsible section's expand state and where its bottom sits within the
 * scrollable Column (via a zero-height marker `Spacer`/`onGloballyPositioned` placed right
 * after the section, see the call sites above) — [toggle]/[expand] flip it on, and expanding
 * also scrolls the section's newly-revealed bottom into view so a long card never gets left cut
 * off below the fold after tapping its row. The two-frame delay before scrolling lets the
 * expand's own recomposition/layout pass actually update [bottomOffset] first — scrolling
 * synchronously from the click handler would still see the section's old (collapsed) position.
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

/** One of the three flat "chevron rows" that replace the old plus/minus accordion headers —
 *  label left, a chevron that rotates 90° open on the right, divider underneath. */
@Composable
private fun DetailRow(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/**
 * The fixed (non-scrolling) green gradient header — back/favorite/overflow row, then the
 * product image + title/subtitle + houdbaarheid status pill. It used to end after the plain
 * back/favorite/overflow app bar, leaving the image/title/pill row on white, scrolling
 * background where [StockCard]'s intentional −14dp overlap could land on top of the status
 * pill instead of the header's own reserved bottom padding — see [ProductDetailScreen].
 */
@Composable
private fun ProductDetailHeader(
    product: ProductEntity?,
    category: Category,
    stillInInventory: Boolean,
    expirationDate: Long?,
    isFavorite: Boolean,
    looksManuallyEntered: Boolean,
    isRetryingLookup: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditPhotoClick: () -> Unit,
    onEditDetailsClick: () -> Unit,
    onRetryLookupClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SageGreenPrimary, TopAppBarContainerGradientEnd)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = contentColor)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Favorite + overflow, per the design review — every other action (edit, change
            // photo, retry lookup, delete) lives behind the overflow menu instead of its own
            // top-bar icon.
            if (stillInInventory && product != null) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.inventory_unmark_favorite_cd else R.string.inventory_mark_favorite_cd,
                        ),
                        tint = if (isFavorite) OnTopAppBarContainerAccent else contentColor,
                    )
                }
            }
            if (product != null) {
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.product_detail_overflow_cd), tint = contentColor)
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.product_detail_edit_cd)) },
                            onClick = { showOverflowMenu = false; onEditDetailsClick() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.product_detail_edit_photo_title)) },
                            onClick = { showOverflowMenu = false; onEditPhotoClick() },
                        )
                        if (looksManuallyEntered) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.product_detail_retry_lookup)) },
                                enabled = !isRetryingLookup,
                                onClick = { showOverflowMenu = false; onRetryLookupClick() },
                            )
                        }
                        // Delete moved off the primary-actions row (it must not sit next to
                        // "Op de lijst") and into here — "Opgemaakt" below covers the same
                        // everyday case, this is the explicit fallback entry point.
                        if (stillInInventory) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.product_detail_remove), color = MaterialTheme.colorScheme.error) },
                                onClick = { showOverflowMenu = false; onDeleteClick() },
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
            ProductImage(
                product = product,
                category = category,
                showEditPhoto = product != null,
                onEditPhotoClick = onEditPhotoClick,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = product?.name ?: stringResource(R.string.product_detail_default_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = contentColor,
                )
                if (product != null) {
                    val nutriScoreText = product.nutriScoreGrade
                        ?.let { stringResource(R.string.product_detail_nutriscore_format, it.uppercase(Locale.ROOT)) }
                        ?: stringResource(R.string.product_detail_nutriscore_unavailable)
                    val subtitle = listOfNotNull(product.brand, stringResource(category.displayNameRes), nutriScoreText)
                        .joinToString(" · ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = OnTopAppBarContainerAccent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (stillInInventory) {
                    StatusPill(
                        label = expirationStatusLabel(expirationDate),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** The 96dp header product image (radius 24dp) with a small photo-edit badge overlaid at its
 *  bottom-end corner — replaces the old centered 160dp hero image block. The favorite toggle
 *  that used to live here moved to the top app bar instead, see [ProductDetailScreen]. */
@Composable
private fun ProductImage(
    product: ProductEntity?,
    category: Category,
    showEditPhoto: Boolean,
    onEditPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(96.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxSize(),
        ) {
            val imageUrl = product?.imageUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        if (showEditPhoto) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(26.dp),
            ) {
                IconButton(onClick = onEditPhotoClick, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.product_detail_edit_photo_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** A small rounded coral pill — the header's at-a-glance freshness signal ("Nog 2 dagen"),
 *  reusing the same wording [expirationStatusLabel] also puts on the Houdbaar-tot card below. */
@Composable
private fun StatusPill(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The stock card — overlaps the header above it, eyebrow "IN HUIS" + a large quantity number
 *  on the left, a pill-shaped stepper on the right. */
@Composable
private fun StockCard(
    quantity: Int,
    unit: String?,
    minQuantity: Int?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = SoftCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.product_detail_in_huis_label).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    val metaParts = listOfNotNull(
                        unit?.takeIf { it.isNotBlank() },
                        minQuantity?.let { stringResource(R.string.product_detail_stat_min_format, it) },
                    )
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                QuantityStepper(
                    quantity = quantity,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** A compact neutral stat card — "LAATST BETAALD"/"GESCAND" and similar — eyebrow, big value,
 *  optional muted caption underneath. */
@Composable
private fun StatTile(eyebrow: String, value: String, caption: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShapeCompact,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = eyebrow.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
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
    onLocationChange: (String?) -> Unit,
    showInventoryFields: Boolean,
    note: String?,
    onNoteChange: (String?) -> Unit,
    minQuantity: Int?,
    onMinQuantityChange: (Int?) -> Unit,
    lastPrice: Double?,
    priceHistory: List<PricePoint>,
    onPriceChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(product.barcode) { mutableStateOf(product.name) }
    var brand by remember(product.barcode) { mutableStateOf(product.brand ?: "") }
    var unit by remember(product.barcode) { mutableStateOf(product.unit ?: "") }
    // Keyed on the note itself (not just the barcode) — unlike name/brand/unit, this field
    // lives on the inventory entry rather than the catalog product, so it can legitimately
    // change from outside this card (e.g. removed from inventory) while it stays mounted.
    var noteText by remember(note) { mutableStateOf(note ?: "") }
    // Editable, unlike every other field in this card being read straight from the inventory
    // entry — this one lets a household type in a price directly, on top of the two ways a
    // price otherwise gets here: checking off a priced shopping list item
    // (ShoppingListRepository.setChecked) or a receipt scan. All three go through
    // ProductRepository.addPricePoint, so they build one continuous history regardless of path.
    var priceText by remember(product.barcode) {
        mutableStateOf(lastPrice?.let { formatPrice(it).removePrefix("€") } ?: "")
    }

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
    LaunchedEffect(priceText) {
        delay(600)
        val parsed = priceText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
        if (parsed != null && parsed != lastPrice) onPriceChange(parsed)
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
            LocationDropdown(
                selected = product.location,
                onSelected = onLocationChange,
                modifier = Modifier.fillMaxWidth(),
            )
            // Minimum, price and note are fields on the inventory entry, not the catalog
            // product — nothing to save them against for a product that isn't (or no longer)
            // in stock. The minimum stepper and the price field used to live inline in the
            // header stock card / a top-bar field; this "Gegevens bewerken" section is now
            // their one home, alongside the fields above.
            if (showInventoryFields) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.product_detail_min_quantity_label), style = MaterialTheme.typography.bodyMedium)
                    QuantityStepper(
                        quantity = minQuantity ?: 0,
                        onDecrease = {
                            val next = (minQuantity ?: 0) - 1
                            onMinQuantityChange(if (next <= 0) null else next)
                        },
                        onIncrease = { onMinQuantityChange((minQuantity ?: 0) + 1) },
                        minQuantity = 0,
                        dense = true,
                    )
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.product_detail_field_last_price)) },
                    placeholder = { Text(stringResource(R.string.shopping_list_price_placeholder)) },
                    leadingIcon = { Text("€", style = MaterialTheme.typography.bodyLarge) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (priceHistory.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.product_detail_price_history_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // The first entry is already shown above as "Laatste prijs" — this is
                        // the rest of the trend, oldest of the kept window last.
                        priceHistory.drop(1).forEach { point ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = listOfNotNull(formatPriceDate(point.date), point.store).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = formatPrice(point.price),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
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
                    // Amber, per the design review — distinguishes an allergen tag from a diet
                    // label at a glance instead of both reading as the same generic chip.
                    TagChip(
                        label = stringResource(allergen.labelRes),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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

private fun formatPrice(value: Double): String = String.format(Locale.getDefault(), "€%.2f", value)
private fun formatGrams(value: Double): String = String.format(Locale.getDefault(), "%.1f g", value)

private fun formatExpirationDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(date)
}

/** Unlike [formatExpirationDate], [millis] here is a real moment in time (when a price point
 *  was recorded), not a date-only value encoded at UTC midnight — so this converts using the
 *  device's actual timezone rather than UTC. */
private fun formatPriceDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
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

/** Shared compact wording for "how urgent is this expiration" — drives both the header's
 *  [StatusPill] and the secondary line on [ExpirationSection]'s Houdbaar-tot card, so the two
 *  surfaces never say something different about the same product. */
@Composable
private fun expirationStatusLabel(expirationDate: Long?): String {
    val days = expirationDate?.let { daysUntilExpiration(it) }
    return when {
        days == null -> stringResource(R.string.product_detail_status_not_set)
        days < 0 -> stringResource(R.string.product_detail_status_expired)
        days == 0L -> stringResource(R.string.product_detail_status_today)
        days == 1L -> stringResource(R.string.product_detail_status_one_day)
        days <= 3 -> stringResource(R.string.product_detail_status_days_format, days)
        else -> stringResource(R.string.product_detail_status_fresh)
    }
}

/** Houdbaar tot — a coral-container card (one pinched corner, see [UrgencyTileShape]) with the
 *  date and its status, plus two 52dp square action buttons: scan the date off a photo, or pick
 *  it manually. A small corner badge on the card itself covers "clear the date" (when set) or
 *  "suggest a date from the category" (when not) — kept off the two main buttons so their count
 *  stays exactly what the design review specifies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpirationSection(
    expirationDate: Long?,
    category: Category,
    onDateChange: (Long?) -> Unit,
    isPremium: Boolean,
    onNavigateToPremium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showAiScan by remember { mutableStateOf(false) }
    // A one-tap estimate based on the product's category (see Category.defaultShelfLifeDays'
    // doc) — only offered while nothing's set yet, so it reads as "no idea? here's a guess"
    // rather than second-guessing a date the household already entered themselves.
    val suggestedDays = category.defaultShelfLifeDays

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                shape = UrgencyTileShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = expirationDate?.let { formatExpirationDate(it) }
                            ?: stringResource(R.string.product_detail_status_not_set),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (expirationDate != null) {
                        Text(
                            text = expirationStatusLabel(expirationDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            if (expirationDate != null) {
                IconButton(
                    onClick = { onDateChange(null) },
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.product_detail_expiration_clear_cd),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            } else if (suggestedDays != null) {
                IconButton(
                    onClick = {
                        val suggestedMillis = LocalDate.now().plusDays(suggestedDays.toLong())
                            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        onDateChange(suggestedMillis)
                    },
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = stringResource(R.string.product_detail_expiration_suggest_format, suggestedDays),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        // AI-productherkenning is already premium-only elsewhere in the app (see
        // ScanScreen/AiRecognizeScreen) since the photo leaves the device — this THT-datum scan
        // is the same real per-scan cost, so it follows the same gate: premium households open
        // the camera dialog directly, everyone else goes straight to the paywall.
        FilledIconButton(
            onClick = { if (isPremium) showAiScan = true else onNavigateToPremium() },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.expiration_scan_entry_cd), modifier = Modifier.size(22.dp))
        }
        OutlinedIconButton(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.product_detail_pick_date_cd), modifier = Modifier.size(22.dp))
        }
    }

    if (showAiScan) {
        ExpirationDateScanDialog(
            onDismiss = { showAiScan = false },
            onDateRecognized = { onDateChange(it) },
        )
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
