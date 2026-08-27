package com.dtraas.homestock.ui.more

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.export.CsvExporter
import com.dtraas.homestock.data.export.CsvImporter
import com.dtraas.homestock.data.export.ImportedInventoryRow
import com.dtraas.homestock.data.export.InventoryCsvHeaders
import com.dtraas.homestock.data.export.InventoryImportResult
import com.dtraas.homestock.data.export.MealHistoryCsvHeaders
import com.dtraas.homestock.data.export.MealHistoryCsvRow
import com.dtraas.homestock.data.export.RecipeCsvHeaders
import com.dtraas.homestock.data.export.ShoppingListCsvHeaders
import com.dtraas.homestock.data.export.StoreCsvHeaders
import com.dtraas.homestock.data.local.entity.MealCompletionStatus
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MealSlot
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.data.repository.FeedbackCategory
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.RecipeDetail
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.ProductImage
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.components.QuantityStepper
import com.dtraas.homestock.ui.components.SearchField
import com.dtraas.homestock.ui.components.SheetActionRow
import com.dtraas.homestock.ui.components.SheetChip
import com.dtraas.homestock.ui.components.SheetEyebrow
import com.dtraas.homestock.ui.components.SheetPrimaryButton
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.icon
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.ui.theme.LinenBackground
import com.dtraas.homestock.ui.theme.LinenBackgroundDark
import com.dtraas.homestock.ui.theme.LinenInk
import com.dtraas.homestock.ui.theme.LinenInkDark
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnSageGreenPrimaryContainer
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SageGreenPrimaryContainer
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.TopAppBarContainerGradientEnd
import com.dtraas.homestock.work.ExpiryCheckWorker
import com.dtraas.homestock.work.LowStockCheckWorker
import com.dtraas.homestock.work.PremiumTrialCheckWorker
import com.dtraas.homestock.work.WasteSummaryWorker
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The choice in the Data-overzetten sheet's export card — which of the household's own data
 *  ends up in the CSV. Distinct from [CsvExporter], which doesn't know about "scope" at all —
 *  it just builds whichever CSV(s) it's asked for; this enum is purely UI/state.
 *
 *  [importable] marks which scopes [ImportCard]'s "Bestand kiezen" button actually knows how to
 *  read back in — [LISTS]/[MEAL_HISTORY]/[ALL] are export-only (see [CsvExporter.shoppingListToCsv]/
 *  [CsvExporter.mealHistoryToCsv]'s own docs for why an import side wouldn't have a clean meaning
 *  for either), same reasoning both already had before [RECIPES]/[STORES] existed. */
private enum class ExportScope(val labelRes: Int, val importable: Boolean = false) {
    // "Voorraad" is exactly inventory_title's own meaning — reused rather than a near-duplicate key.
    INVENTORY(R.string.inventory_title, importable = true),
    LISTS(R.string.more_data_scope_lists),
    RECIPES(R.string.more_data_scope_recipes, importable = true),
    STORES(R.string.more_data_scope_stores, importable = true),
    MEAL_HISTORY(R.string.more_data_scope_meal_history),
    ALL(R.string.more_data_scope_all),
}

private enum class AppLanguage(val tag: String, val labelRes: Int, val flag: String) {
    NL("nl", R.string.more_language_option_nl, "🇳🇱"),
    EN("en", R.string.more_language_option_en, "🇬🇧"),
    DE("de", R.string.more_language_option_de, "🇩🇪"),
    FR("fr", R.string.more_language_option_fr, "🇫🇷"),
    ES("es", R.string.more_language_option_es, "🇪🇸"),
}

/**
 * Instellingen — profielnaam en huishouden-info leven nu in de eigen groene gradient-header
 * ([MoreScreenHeader], zelfde Keukenlinnen-patroon als Voorraad/Productdetail/Boodschappenlijst/
 * Maaltijdplanner), met daaronder de Premium-kaart en drie secties: Huishouden, App (Meldingen,
 * Taal, Thema, Toegankelijkheid, Data overzetten — het oorspronkelijke rijtje van vijf) en
 * Ondersteuning (Account koppelen, Feedback geven, Beoordeel de app, Privacybeleid, Licenties).
 * Die laatste twee secties woonden een tijd op een apart MoreOptionsScreen achter één "Data,
 * toegankelijkheid & ondersteuning"-rij; dat scherm is hier weer helemaal in opgenomen op
 * uitdrukkelijk verzoek — MoreOptionsScreen.kt bestaat niet meer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    onNavigateToHousehold: () -> Unit = {},
    onNavigateToApp: () -> Unit = {},
    onNavigateToAccountLink: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val notificationPreferences = application.container.notificationPreferences
    val notificationsEnabled by notificationPreferences.expiryNotificationsEnabled.collectAsState()
    val expiryLeadTimeDays by notificationPreferences.expiryLeadTimeDays.collectAsState()
    val expiryNotifyHour by notificationPreferences.expiryNotifyHour.collectAsState()
    val expiryNotifyMinute by notificationPreferences.expiryNotifyMinute.collectAsState()
    val inventoryInsightNotificationsEnabled by notificationPreferences.inventoryInsightNotificationsEnabled.collectAsState()
    val premiumNotificationsEnabled by notificationPreferences.premiumNotificationsEnabled.collectAsState()
    val householdActivityNotificationsEnabled by notificationPreferences.householdActivityNotificationsEnabled.collectAsState()
    val inventoryPreferences = application.container.inventoryPreferences
    val autoRestockEnabled by inventoryPreferences.autoRestockEnabled.collectAsState()
    val householdSession = application.container.householdSession
    val householdId by householdSession.householdId.collectAsState()
    val householdRepository = application.container.householdRepository
    val householdName by householdRepository.observeHouseholdName().collectAsState(initial = null)
    val deviceProfile = application.container.deviceProfile
    val displayName by deviceProfile.displayName.collectAsState()
    val photoPath by deviceProfile.photoPath.collectAsState()
    val householdMembersRepository = application.container.householdMembersRepository
    val isPremium by householdMembersRepository.observeHouseholdIsPremium().collectAsState(initial = false)
    val memberCount by householdMembersRepository.observeMemberCount().collectAsState(initial = 0)
    val members by householdMembersRepository.observeMembers().collectAsState(initial = emptyList())
    val billingRepository = application.container.billingRepository
    val debugPremiumOverride by billingRepository.debugPremiumOverride.collectAsState()
    val storeRepository = application.container.storeRepository
    val stores by storeRepository.observeStores().collectAsState(initial = emptyList())
    val exportPreferences = application.container.exportPreferences
    val lastExportTimestamp by exportPreferences.lastExportTimestamp.collectAsState()
    val shoppingListRepository = application.container.shoppingListRepository
    // Unchecked-only: a store's "N items op de lijst" count in the Winkels sheet is about what
    // still needs a visit, not what's already in the cart — same reasoning as the shopping list
    // screen's own "openstaande items" count elsewhere in the app.
    val shoppingListItemCountByStore by remember {
        shoppingListRepository.observeShoppingList().map { items ->
            items.filter { !it.isChecked }.groupingBy { it.store }.eachCount()
        }
    }.collectAsState(initial = emptyMap())
    val inventoryRepository = application.container.inventoryRepository
    // Live counts for Data overzetten's Exporteren card — "put the app's knowledge in the sheet"
    // means the scope pills say what a household would actually get, not just their labels.
    val inventoryItemCount by remember {
        inventoryRepository.observeInventoryWithProduct().map { it.size }
    }.collectAsState(initial = 0)
    val shoppingListItemCount by remember {
        shoppingListRepository.observeShoppingList().map { it.size }
    }.collectAsState(initial = 0)
    val recipeRepository = application.container.recipeRepository
    val mealPlanRepository = application.container.mealPlanRepository
    // Distinct ids across eigen recepten + favorieten — a hand-entered recipe the household also
    // favorited should count once here, same dedup [CsvExporter.recipesToCsv] itself applies.
    val recipeItemCount by remember {
        combine(recipeRepository.observeCustomRecipes(), recipeRepository.observeFavoriteRecipes()) { custom, favorites ->
            (custom.map { it.meal.id } + favorites.map { it.meal.id }).toSet().size
        }
    }.collectAsState(initial = 0)
    val feedbackRepository = application.container.feedbackRepository
    val accountLinkRepository = application.container.accountLinkRepository
    val isAccountLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackSentMessage = stringResource(R.string.more_feedback_sent_confirmation)
    val feedbackErrorMessage = stringResource(R.string.more_feedback_error)

    var showProfileDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStoresDialog by remember { mutableStateOf(false) }
    var showAisleOrderDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }
    var exportScope by remember { mutableStateOf(ExportScope.INVENTORY) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Whether granted or not, the setting itself stays on; ExpiryCheckWorker re-checks the permission before posting. */ }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.setExpiryNotificationsEnabled(enabled)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ExpiryCheckWorker.runOnce(context)
        }
    }

    // Same request-permission-then-run-once-for-instant-feedback shape as setNotificationsEnabled
    // above, for the three notification types added alongside the expiry one.
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun setInventoryInsightNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.setInventoryInsightNotificationsEnabled(enabled)
        if (enabled) {
            requestNotificationPermissionIfNeeded()
            LowStockCheckWorker.runOnce(context)
            WasteSummaryWorker.runOnce(context)
        }
    }

    fun setPremiumNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.setPremiumNotificationsEnabled(enabled)
        if (enabled) {
            requestNotificationPermissionIfNeeded()
            PremiumTrialCheckWorker.runOnce(context)
        }
    }

    fun setHouseholdActivityNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.setHouseholdActivityNotificationsEnabled(enabled)
        if (enabled) requestNotificationPermissionIfNeeded()
    }

    fun setExpiryLeadTimeDays(days: Int) {
        notificationPreferences.setExpiryLeadTimeDays(days)
    }

    /** Also re-arms [ExpiryCheckWorker] right away, at the freshly chosen time — see its
     *  `schedule`'s doc for why that's safe to call again here. */
    fun setExpiryNotifyTime(hour: Int, minute: Int) {
        notificationPreferences.setExpiryNotifyTime(hour, minute)
        ExpiryCheckWorker.schedule(context, hour, minute)
    }

    // CSV export (Voorraad / Boodschappenlijst / Alles — see ExportScope) — moved+extended from
    // the now-gone MoreOptionsScreen.kt: the CSV content has to be built *before* the system's
    // "save to..." picker is launched (it needs a filename up front, but only hands back a Uri
    // once the user has actually picked a location, well after this composable has moved on),
    // so it's held here and written once that callback fires.
    var pendingExportCsv by remember { mutableStateOf<String?>(null) }
    val exportErrorMessage = stringResource(R.string.more_export_error)
    val exportSuccessMessage = stringResource(R.string.more_export_success)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingExportCsv
        pendingExportCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val message = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                exportPreferences.recordExportNow()
                exportSuccessMessage
            } catch (e: Exception) {
                exportErrorMessage
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
    val categoryLabels = Category.entries.associate { it.storageKey to stringResource(it.displayNameRes) }
    val unitLabels = MeasurementUnit.entries.associate { it.storageKey to stringResource(it.shortLabelRes) }
    val categoryKeyByLabel = categoryLabels.entries.associate { (key, label) -> label to key }
    val unitKeyByLabel = unitLabels.entries.associate { (key, label) -> label to key }
    val csvYes = stringResource(R.string.common_yes)
    val csvNo = stringResource(R.string.common_no)
    val inventoryCsvHeaders = InventoryCsvHeaders(
        name = stringResource(R.string.common_name),
        brand = stringResource(R.string.product_detail_field_brand),
        category = stringResource(R.string.category_dropdown_label),
        quantity = stringResource(R.string.common_quantity),
        unit = stringResource(R.string.product_detail_field_unit),
        expiration = stringResource(R.string.product_detail_expiration_label),
        minQuantity = stringResource(R.string.product_detail_min_quantity_label),
        favorite = stringResource(R.string.more_export_header_favorite),
        note = stringResource(R.string.shopping_list_note_label),
    )
    val shoppingListCsvHeaders = ShoppingListCsvHeaders(
        name = stringResource(R.string.common_name),
        category = stringResource(R.string.category_dropdown_label),
        store = stringResource(R.string.store_dropdown_label),
        quantity = stringResource(R.string.common_quantity),
        unit = stringResource(R.string.product_detail_field_unit),
        note = stringResource(R.string.shopping_list_note_label),
        price = stringResource(R.string.more_export_header_price),
        checked = stringResource(R.string.more_export_header_checked),
    )
    val inventorySectionTitle = stringResource(R.string.inventory_title)
    val shoppingListSectionTitle = stringResource(R.string.shopping_list_title)
    val recipesSectionTitle = stringResource(R.string.more_data_scope_recipes)
    val storesSectionTitle = stringResource(R.string.more_data_scope_stores)
    val mealHistorySectionTitle = stringResource(R.string.more_data_scope_meal_history)
    val recipeCsvHeaders = RecipeCsvHeaders(
        id = stringResource(R.string.more_export_header_recipe_id),
        name = stringResource(R.string.common_name),
        custom = stringResource(R.string.more_export_header_own_recipe),
        favorite = stringResource(R.string.more_export_header_favorite),
        category = stringResource(R.string.category_dropdown_label),
        area = stringResource(R.string.more_export_header_cuisine),
        readyInMinutes = stringResource(R.string.more_export_header_ready_minutes),
        servings = stringResource(R.string.more_export_header_servings),
        ingredients = stringResource(R.string.more_export_header_ingredients),
        instructions = stringResource(R.string.more_export_header_instructions),
    )
    val storeCsvHeaders = StoreCsvHeaders(name = stringResource(R.string.common_name))
    val mealHistoryCsvHeaders = MealHistoryCsvHeaders(
        date = stringResource(R.string.more_export_header_date),
        slot = stringResource(R.string.more_export_header_meal_slot),
        name = stringResource(R.string.common_name),
        status = stringResource(R.string.more_export_header_status),
    )
    val mealSlotLabels = MealSlot.entries.associateWith { stringResource(it.labelRes) }
    val mealStatusEaten = stringResource(R.string.product_detail_delete_used_up)
    val mealStatusWasted = stringResource(R.string.product_detail_delete_wasted)

    fun exportData(scope: ExportScope) {
        coroutineScope.launch {
            val inventoryCsv = if (scope == ExportScope.INVENTORY || scope == ExportScope.ALL) {
                val items = inventoryRepository.observeInventoryWithProduct().first()
                CsvExporter.inventoryToCsv(
                    items,
                    inventoryCsvHeaders,
                    categoryLabel = { key -> categoryLabels[key] ?: key },
                    unitLabel = { key -> unitLabels[key] ?: (key ?: "") },
                    yesLabel = csvYes,
                    noLabel = csvNo,
                )
            } else {
                null
            }
            val listCsv = if (scope == ExportScope.LISTS || scope == ExportScope.ALL) {
                val items = application.container.shoppingListRepository.observeShoppingList().first()
                CsvExporter.shoppingListToCsv(
                    items,
                    shoppingListCsvHeaders,
                    categoryLabel = { key -> categoryLabels[key] ?: key },
                    unitLabel = { key -> unitLabels[key] ?: key },
                    yesLabel = csvYes,
                    noLabel = csvNo,
                )
            } else {
                null
            }
            val recipesCsv = if (scope == ExportScope.RECIPES || scope == ExportScope.ALL) {
                val customDetails = recipeRepository.fetchAllCustomRecipeDetails()
                val favoriteDetails = recipeRepository.fetchAllFavoriteRecipeDetails()
                val merged = LinkedHashMap<String, RecipeDetail>()
                customDetails.forEach { merged[it.id] = it }
                favoriteDetails.forEach { merged.putIfAbsent(it.id, it) }
                CsvExporter.recipesToCsv(
                    merged.values.toList(),
                    customIds = customDetails.map { it.id }.toSet(),
                    favoriteIds = favoriteDetails.map { it.id }.toSet(),
                    headers = recipeCsvHeaders,
                    yesLabel = csvYes,
                    noLabel = csvNo,
                )
            } else {
                null
            }
            val storesCsv = if (scope == ExportScope.STORES || scope == ExportScope.ALL) {
                CsvExporter.storesToCsv(stores, storeCsvHeaders)
            } else {
                null
            }
            val mealHistoryCsv = if (scope == ExportScope.MEAL_HISTORY || scope == ExportScope.ALL) {
                val today = LocalDate.now()
                // Half a year back, nothing forward — a "historie" export is about what already
                // happened, not the days the household hasn't planned yet.
                val history = mealPlanRepository.fetchDateRange(today.minusDays(180), today)
                val historyRows = history.entries.sortedBy { it.key }.flatMap { (date, dayPlan) ->
                    MealSlot.ORDERED.flatMap { slot ->
                        dayPlan[slot].orEmpty().map { meal ->
                            MealHistoryCsvRow(
                                date = date.toString(),
                                slot = mealSlotLabels[slot] ?: slot.storageKey,
                                name = meal.name,
                                status = when (meal.status) {
                                    MealCompletionStatus.EATEN -> mealStatusEaten
                                    MealCompletionStatus.WASTED -> mealStatusWasted
                                    null -> null
                                },
                            )
                        }
                    }
                }
                CsvExporter.mealHistoryToCsv(historyRows, mealHistoryCsvHeaders)
            } else {
                null
            }
            val (csv, filename) = when (scope) {
                ExportScope.INVENTORY -> requireNotNull(inventoryCsv) to "voorraad.csv"
                ExportScope.LISTS -> requireNotNull(listCsv) to "boodschappenlijst.csv"
                ExportScope.RECIPES -> requireNotNull(recipesCsv) to "recepten.csv"
                ExportScope.STORES -> requireNotNull(storesCsv) to "winkels.csv"
                ExportScope.MEAL_HISTORY -> requireNotNull(mealHistoryCsv) to "maaltijden-historie.csv"
                ExportScope.ALL -> CsvExporter.combinedToCsv(
                    listOf(
                        inventorySectionTitle to requireNotNull(inventoryCsv),
                        shoppingListSectionTitle to requireNotNull(listCsv),
                        recipesSectionTitle to requireNotNull(recipesCsv),
                        storesSectionTitle to requireNotNull(storesCsv),
                        mealHistorySectionTitle to requireNotNull(mealHistoryCsv),
                    ),
                ) to "homestock-data.csv"
            }
            pendingExportCsv = csv
            exportLauncher.launch(filename)
        }
    }

    // CSV import — moved+extended from the now-gone MoreOptionsScreen.kt. Only the three scopes
    // [ExportScope.importable] marks (Voorraad/Recepten/Winkels) have a read side at all — see
    // that flag's own doc for why Lijsten/Maaltijden historie/Alles don't. Voorraad alone gets a
    // preview step (see pendingImportPreview/confirmImport below): every row becomes a brand-new
    // product (synthetic "csv-..." barcode, same convention AI-productherkenning uses for
    // products with no real barcode) with quantities/expiration dates a household should get to
    // glance over first. Recepten/Winkels skip that step and commit straight away instead — both
    // upsert by their own real id/name (see RecipeRepository.saveCustomRecipe/addFavorite,
    // StoreRepository.addStore) rather than always minting something new, so re-importing the
    // same file twice is harmless without needing a look-before-you-leap preview either.
    val importErrorMessage = stringResource(R.string.more_import_error)
    val importEmptyMessage = stringResource(R.string.more_import_empty)
    val importSuccessFormat = stringResource(R.string.more_import_success_format)
    val importSkippedFormat = stringResource(R.string.more_import_skipped_format)
    val importRecipeSuccessFormat = stringResource(R.string.more_import_recipe_success_format)
    val importStoreSuccessFormat = stringResource(R.string.more_import_store_success_format)
    var pendingImportPreview by remember { mutableStateOf<InventoryImportResult?>(null) }

    suspend fun importRecipes(csv: String): String {
        val result = CsvImporter.parseRecipesCsv(csv, csvYes)
        if (result.rows.isEmpty()) return importEmptyMessage
        result.rows.forEach { row ->
            if (row.isCustom) {
                val customId = row.id.takeIf { it.startsWith(RecipeRepository.CUSTOM_ID_PREFIX) }
                recipeRepository.saveCustomRecipe(
                    id = customId,
                    name = row.name,
                    category = row.category,
                    area = row.area,
                    readyInMinutes = row.readyInMinutes,
                    servings = row.servings,
                    instructions = row.instructions,
                    ingredients = row.ingredients,
                ).getOrNull()?.let { saved ->
                    if (row.isFavorite) recipeRepository.addFavorite(saved)
                }
            } else if (row.isFavorite && row.id.isNotBlank()) {
                recipeRepository.addFavorite(
                    RecipeDetail(
                        id = row.id,
                        name = row.name,
                        thumbnailUrl = null,
                        category = row.category,
                        area = row.area,
                        instructions = row.instructions,
                        ingredients = row.ingredients,
                        servings = row.servings,
                        readyInMinutes = row.readyInMinutes,
                    ),
                )
            }
        }
        val summary = String.format(importRecipeSuccessFormat, result.rows.size)
        return if (result.skippedCount > 0) summary + " " + String.format(importSkippedFormat, result.skippedCount) else summary
    }

    suspend fun importStores(csv: String): String {
        val names = CsvImporter.parseStoresCsv(csv)
        if (names.isEmpty()) return importEmptyMessage
        names.forEach { storeRepository.addStore(it) }
        return String.format(importStoreSuccessFormat, names.size)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val scopeAtPick = exportScope
        coroutineScope.launch {
            val message = try {
                val csv = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (csv == null) {
                    importErrorMessage
                } else {
                    when (scopeAtPick) {
                        ExportScope.RECIPES -> importRecipes(csv)
                        ExportScope.STORES -> importStores(csv)
                        else -> {
                            val result = CsvImporter.parseInventoryCsv(csv, categoryKeyByLabel, unitKeyByLabel, csvYes)
                            if (result.rows.isEmpty()) {
                                importEmptyMessage
                            } else {
                                pendingImportPreview = result
                                null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                importErrorMessage
            }
            if (message != null) snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    fun pickImportFile() {
        importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "*/*"))
    }

    fun confirmImport() {
        val result = pendingImportPreview ?: return
        pendingImportPreview = null
        coroutineScope.launch {
            result.rows.forEach { row ->
                val barcode = "csv-${UUID.randomUUID()}"
                application.container.productRepository.saveManualProduct(
                    barcode = barcode,
                    name = row.name,
                    category = Category.fromStorageKey(row.categoryKey),
                    brand = row.brand,
                    unit = row.unitKey,
                )
                inventoryRepository.restoreItem(
                    barcode = barcode,
                    quantity = row.quantity,
                    expirationDate = row.expirationDate,
                    minQuantity = row.minQuantity,
                    note = row.note,
                    isFavorite = row.isFavorite,
                )
            }
            val summary = String.format(importSuccessFormat, result.rows.size)
            val message = if (result.skippedCount > 0) {
                summary + " " + String.format(importSkippedFormat, result.skippedCount)
            } else {
                summary
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        // MoreScreenHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Titles used both to label their row below and, via [matches], to decide whether the
        // header's search field keeps that row on screen — pulled out to plain vals rather than
        // calling stringResource(...) twice (once here, once inside the row itself) per entry.
        val householdRowTitle = stringResource(R.string.more_household_row_title)
        val storesTitle = stringResource(R.string.more_stores_title)
        val aisleOrderTitle = stringResource(R.string.more_aisle_order_row_title)
        val statisticsTitle = stringResource(R.string.more_statistics_title)
        val autoRestockTitle = stringResource(R.string.more_auto_restock_title)
        val notificationsTitle = stringResource(R.string.more_notifications_menu_title)
        val appSettingsTitle = stringResource(R.string.more_app_settings_title)
        val accountLinkTitle = stringResource(R.string.account_link_row_title)
        val dataCsvTitle = stringResource(R.string.more_data_csv_title)
        val feedbackTitle = stringResource(R.string.more_about_feedback)
        val rateAppTitle = stringResource(R.string.more_about_rate_app)
        val privacyTitle = stringResource(R.string.more_about_privacy_policy)
        val licensesTitle = stringResource(R.string.more_about_licenses)
        fun matches(title: String) = searchQuery.isBlank() || title.contains(searchQuery, ignoreCase = true)

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Alleen nog de titel + een zoekbalk — profiel/huishouden-identiteit (foto, naam,
            // huishoudcode) verhuisde naar ProfileRow hieronder, als eerste rij in de lijst, om
            // in de header zelf plaats te maken voor het zoekveld.
            MoreScreenHeader(searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Terug naar de oorspronkelijke plek, direct onder de header — "Premium
                // simuleren" (helemaal onderaan, bij Debug) was wat te weinig ruimte eronder
                // had, niet deze kaart. Premium blijft ook zichtbaar tijdens het zoeken — het is
                // geen doorzoekbare instelling, maar een permanente ingang naar het abonnement.
                PremiumCard(isPremium = isPremium, onClick = onNavigateToPremium)

                val householdRows: List<@Composable () -> Unit> = listOfNotNull(
                    // Profiel + huishouden-naam — voorheen vast in de groene header, nu de eerste
                    // rij van de Huishouden-sectie zelf, op uitdrukkelijk verzoek. Altijd
                    // zichtbaar tijdens het zoeken (zoals de Premium-kaart hierboven) — dit is een
                    // identiteitsrij, geen doorzoekbare instelling.
                    {
                        ProfileRow(
                            displayName = displayName,
                            photoPath = photoPath,
                            householdName = householdName,
                            onClick = { showProfileDialog = true },
                        )
                    },
                    if (matches(householdRowTitle)) {
                        {
                            HouseholdMembersRow(
                                subtitle = pluralStringResource(
                                    R.plurals.more_household_subtitle_format,
                                    memberCount,
                                    memberCount,
                                    householdId ?: "—",
                                ),
                                onClick = onNavigateToHousehold,
                            )
                        }
                    } else null,
                    if (matches(storesTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.Storefront,
                                title = storesTitle,
                                // A description of what's configurable here (names and order),
                                // not a live count — same reasoning as Meldingen's row above.
                                subtitle = stringResource(R.string.more_stores_menu_subtitle),
                                onClick = { showStoresDialog = true },
                            )
                        }
                    } else null,
                    if (matches(aisleOrderTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.Reorder,
                                title = aisleOrderTitle,
                                subtitle = stringResource(R.string.more_aisle_order_row_subtitle),
                                onClick = { showAisleOrderDialog = true },
                            )
                        }
                    } else null,
                    if (matches(statisticsTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.BarChart,
                                title = statisticsTitle,
                                subtitle = stringResource(R.string.more_statistics_subtitle),
                                trailingLabel = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                                onClick = { if (isPremium) onNavigateToStatistics() else onNavigateToPremium() },
                            )
                        }
                    } else null,
                    if (matches(autoRestockTitle)) {
                        {
                            // Hoort inhoudelijk bij Voorraad/Boodschappenlijst-gedrag, niet bij
                            // een apparaat-instelling — de App-sectie hieronder gaat terug naar
                            // precies de oorspronkelijke vijf rijen, dus deze schakelaar (die er
                            // later bijkwam) verhuist hierheen in plaats van te verdwijnen.
                            SwitchRow(
                                icon = Icons.Filled.ShoppingCart,
                                title = autoRestockTitle,
                                subtitle = stringResource(R.string.more_auto_restock_subtitle),
                                checked = autoRestockEnabled,
                                onCheckedChange = inventoryPreferences::setAutoRestockEnabled,
                            )
                        }
                    } else null,
                )
                if (householdRows.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.more_section_household))
                    SettingsGroup(rows = householdRows)
                }

                val preferenceRows: List<@Composable () -> Unit> = listOfNotNull(
                    if (matches(notificationsTitle)) {
                        {
                            // The four notification toggles used to live inline here, one row
                            // each — folded into their own submenu (per design review) since
                            // that's four full icon+title+subtitle rows just for one App
                            // sub-topic, same "group it behind one row" treatment the App row
                            // below gets for Weergave/Taal/Toegankelijkheid.
                            SettingsRow(
                                icon = Icons.Filled.Notifications,
                                title = notificationsTitle,
                                // A description of what's configurable here (the app's four
                                // notification categories), not the live on/off count — the
                                // household already sees each toggle's own current state the
                                // moment they open this row.
                                subtitle = stringResource(R.string.more_notifications_menu_subtitle),
                                onClick = { showNotificationsDialog = true },
                            )
                        }
                    } else null,
                    if (matches(appSettingsTitle)) {
                        {
                            // Weergave, Taal en Toegankelijkheid used to be three separate rows,
                            // each opening its own small AlertDialog — collapsed into this one
                            // row (2026-08 dialog review) since together they're one coherent
                            // "how the app looks and reads" topic, better served by a real
                            // screen (with a live preview) than three disconnected popups.
                            SettingsRow(
                                icon = Icons.Filled.Tune,
                                title = appSettingsTitle,
                                // A static description of *what's configurable* here, not the
                                // live values — the household can already see their own current
                                // theme/taal without this row repeating it back to them.
                                subtitle = stringResource(R.string.more_app_settings_subtitle),
                                onClick = onNavigateToApp,
                            )
                        }
                    } else null,
                )
                if (preferenceRows.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.more_section_preferences))
                    SettingsGroup(rows = preferenceRows)
                }

                val supportRows: List<@Composable () -> Unit> = listOfNotNull(
                    if (matches(accountLinkTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.VerifiedUser,
                                title = accountLinkTitle,
                                subtitle = if (isAccountLinked) {
                                    stringResource(R.string.account_link_row_subtitle_linked_format, accountLinkRepository.linkedEmail ?: "—")
                                } else {
                                    stringResource(R.string.account_link_row_subtitle_unlinked)
                                },
                                onClick = onNavigateToAccountLink,
                            )
                        }
                    } else null,
                    if (matches(dataCsvTitle)) {
                        {
                            // Hoorde eerst thuis in de App-sectie tussen de andere apparaat-
                            // instellingen; verhuisd naar Ondersteuning, direct onder Account
                            // koppelen, op uitdrukkelijk verzoek.
                            SettingsRow(
                                icon = Icons.Filled.ImportExport,
                                title = dataCsvTitle,
                                subtitle = stringResource(R.string.more_data_csv_subtitle),
                                trailingLabel = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                                onClick = { if (isPremium) showImportExportDialog = true else onNavigateToPremium() },
                            )
                        }
                    } else null,
                    if (matches(feedbackTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.Feedback,
                                title = feedbackTitle,
                                subtitle = stringResource(R.string.more_about_feedback_subtitle),
                                onClick = { showFeedbackDialog = true },
                            )
                        }
                    } else null,
                    if (matches(rateAppTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.StarRate,
                                title = rateAppTitle,
                                subtitle = stringResource(R.string.more_about_rate_app_subtitle),
                                onClick = { openPlayStoreListing(context) },
                            )
                        }
                    } else null,
                    if (matches(privacyTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.PrivacyTip,
                                title = privacyTitle,
                                subtitle = stringResource(R.string.more_about_privacy_policy_subtitle),
                                onClick = onNavigateToPrivacyPolicy,
                            )
                        }
                    } else null,
                    if (matches(licensesTitle)) {
                        {
                            SettingsRow(
                                icon = Icons.Filled.Description,
                                title = licensesTitle,
                                subtitle = stringResource(R.string.more_about_licenses_subtitle),
                                onClick = onNavigateToLicenses,
                            )
                        }
                    } else null,
                )
                if (supportRows.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.more_section_support))
                    SettingsGroup(rows = supportRows)
                }

                // Zoekopdracht levert niets op in geen enkele sectie — laat dat expliciet zien
                // in plaats van een verwarrend kaal scherm (de Profiel-rij en Premium-kaart
                // blijven wel altijd zichtbaar, zie hierboven — householdRows telt hier daarom
                // vanaf 1, niet 0: die ene rij is nooit een teken dat er iets écht matchte).
                if (searchQuery.isNotBlank() && householdRows.size <= 1 && preferenceRows.isEmpty() && supportRows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.more_settings_search_empty_format, searchQuery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.more_about_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                if (BuildConfig.DEBUG) {
                    SectionHeader(stringResource(R.string.more_section_debug))
                    SettingsGroup(
                        rows = listOf(
                            {
                                DebugPremiumRow(
                                    checked = debugPremiumOverride,
                                    onCheckedChange = billingRepository::setDebugPremiumOverride,
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            displayName = displayName,
            photoPath = photoPath,
            onSaveName = { deviceProfile.setDisplayName(it) },
            onPhotoPicked = { uri ->
                coroutineScope.launch {
                    deviceProfile.setPhotoFromUri(uri)
                    householdMembersRepository.syncCurrentDevicePhoto()
                }
            },
            onRemovePhoto = {
                coroutineScope.launch {
                    deviceProfile.clearPhoto()
                    householdMembersRepository.syncCurrentDevicePhoto()
                }
            },
            onDismiss = { showProfileDialog = false },
        )
    }

    if (showStoresDialog) {
        StoresDialog(
            stores = stores,
            itemCountByStore = shoppingListItemCountByStore,
            onAdd = { name -> coroutineScope.launch { storeRepository.addStore(name) } },
            onRemove = { store ->
                coroutineScope.launch {
                    shoppingListRepository.clearStoreFromItems(store.name)
                    storeRepository.removeStore(store.id)
                }
            },
            onMove = { store, previous, next -> coroutineScope.launch { storeRepository.moveStore(store, previous, next) } },
            onDismiss = { showStoresDialog = false },
        )
    }

    if (showAisleOrderDialog) {
        AisleOrderDialog(
            stores = stores,
            onSetOrder = { store, order -> coroutineScope.launch { storeRepository.setAisleOrder(store, order) } },
            onDismiss = { showAisleOrderDialog = false },
        )
    }

    if (showNotificationsDialog) {
        NotificationsSettingsDialog(
            expiryEnabled = notificationsEnabled,
            onExpiryChange = ::setNotificationsEnabled,
            expiryLeadTimeDays = expiryLeadTimeDays,
            onExpiryLeadTimeChange = ::setExpiryLeadTimeDays,
            expiryNotifyHour = expiryNotifyHour,
            expiryNotifyMinute = expiryNotifyMinute,
            onExpiryNotifyTimeChange = ::setExpiryNotifyTime,
            inventoryInsightEnabled = inventoryInsightNotificationsEnabled,
            onInventoryInsightChange = ::setInventoryInsightNotificationsEnabled,
            householdActivityEnabled = householdActivityNotificationsEnabled,
            onHouseholdActivityChange = ::setHouseholdActivityNotificationsEnabled,
            // The first other device in the household — "Als <naam> iets afvinkt of toevoegt"
            // names someone real instead of the generic "een huisgenoot" the old copy said.
            housemateName = members.firstOrNull { !it.isCurrentDevice }?.displayName?.takeIf { it.isNotBlank() },
            premiumEnabled = premiumNotificationsEnabled,
            onPremiumChange = ::setPremiumNotificationsEnabled,
            onDismiss = { showNotificationsDialog = false },
        )
    }

    if (showImportExportDialog) {
        ImportExportDialog(
            scope = exportScope,
            onScopeChange = { exportScope = it },
            inventoryItemCount = inventoryItemCount,
            shoppingListItemCount = shoppingListItemCount,
            recipeItemCount = recipeItemCount,
            storeItemCount = stores.size,
            lastExportTimestamp = lastExportTimestamp,
            onExport = { exportData(exportScope) },
            onPickImportFile = ::pickImportFile,
            onDismiss = { showImportExportDialog = false },
        )
    }

    pendingImportPreview?.let { preview ->
        ImportPreviewDialog(
            result = preview,
            categoryLabel = { key -> categoryLabels[key] ?: key },
            unitLabel = { key -> key?.let { unitLabels[it] } ?: "" },
            onConfirm = ::confirmImport,
            onDismiss = { pendingImportPreview = null },
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onSend = { category, message, includeDiagnostics ->
                coroutineScope.launch {
                    try {
                        feedbackRepository.submit(category, message, includeDiagnostics)
                        snackbarHostState.showSnackbar(feedbackSentMessage, duration = SnackbarDuration.Short)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(feedbackErrorMessage, duration = SnackbarDuration.Short)
                    }
                }
            },
            onRateApp = { openPlayStoreListing(context) },
            onDismiss = { showFeedbackDialog = false },
        )
    }
}

private fun openPlayStoreListing(context: Context) {
    val uri = Uri.parse("market://details?id=${context.packageName}")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.android.vending") })
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsSettingsDialog(
    expiryEnabled: Boolean,
    onExpiryChange: (Boolean) -> Unit,
    expiryLeadTimeDays: Int,
    onExpiryLeadTimeChange: (Int) -> Unit,
    expiryNotifyHour: Int,
    expiryNotifyMinute: Int,
    onExpiryNotifyTimeChange: (Int, Int) -> Unit,
    inventoryInsightEnabled: Boolean,
    onInventoryInsightChange: (Boolean) -> Unit,
    householdActivityEnabled: Boolean,
    onHouseholdActivityChange: (Boolean) -> Unit,
    housemateName: String?,
    premiumEnabled: Boolean,
    onPremiumChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SheetTitle(title = stringResource(R.string.more_notifications_menu_title))

            // Hero card: Houdbaarheid is the one notification most people actually want, so it
            // gets top billing with its own switch plus, once on, the two knobs that decide
            // exactly when it fires — rather than being just another row among the others below.
            Surface(
                shape = SoftCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(
                                text = stringResource(R.string.more_notifications_row_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = stringResource(R.string.more_notifications_hero_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = expiryEnabled, onCheckedChange = onExpiryChange)
                    }

                    if (expiryEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.more_notifications_lead_time_label), style = MaterialTheme.typography.bodyMedium)
                            LeadTimeSegmentedControl(selected = expiryLeadTimeDays, onSelected = onExpiryLeadTimeChange)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.more_notifications_time_label), style = MaterialTheme.typography.bodyMedium)
                            Surface(
                                onClick = { showTimePicker = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.more_notifications_time_format, expiryNotifyHour, expiryNotifyMinute),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetEyebrow(text = stringResource(R.string.more_notifications_also_section), modifier = Modifier.padding(start = 4.dp))
                Surface(shape = SoftCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SwitchRow(
                            icon = Icons.Filled.Inventory2,
                            title = stringResource(R.string.more_inventory_insight_notifications_title),
                            subtitle = stringResource(R.string.more_inventory_insight_notifications_subtitle),
                            checked = inventoryInsightEnabled,
                            onCheckedChange = onInventoryInsightChange,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchRow(
                            icon = Icons.Filled.Groups,
                            title = stringResource(R.string.more_household_activity_notifications_title),
                            subtitle = housemateName
                                ?.let { stringResource(R.string.more_household_activity_notifications_subtitle_named_format, it) }
                                ?: stringResource(R.string.more_household_activity_notifications_subtitle),
                            checked = householdActivityEnabled,
                            onCheckedChange = onHouseholdActivityChange,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchRow(
                            icon = Icons.Filled.WorkspacePremium,
                            title = stringResource(R.string.more_premium_notifications_title),
                            subtitle = stringResource(R.string.more_premium_notifications_subtitle),
                            checked = premiumEnabled,
                            onCheckedChange = onPremiumChange,
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        HomeStockTimePickerDialog(
            initialHour = expiryNotifyHour,
            initialMinute = expiryNotifyMinute,
            onConfirm = { hour, minute ->
                onExpiryNotifyTimeChange(hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** 1/2/3-dagen picker for the Houdbaarheid hero card's "Waarschuw" row — same pill-segmented
 *  shape as [SortSegmentedControl] in ShoppingListScreen, just with a fixed 3-value int domain
 *  instead of an enum. */
@Composable
private fun LeadTimeSegmentedControl(selected: Int, onSelected: (Int) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(modifier = Modifier.padding(3.dp)) {
            (1..3).forEach { days ->
                val isSelected = days == selected
                Surface(
                    onClick = { onSelected(days) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.more_notifications_lead_time_days_format, days, days),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Material3 [TimePicker] wrapped in a plain [Dialog] — the sheet's "Tijdstip" pill opens this
 *  rather than a full second bottom sheet, since a time picker is already a self-contained,
 *  short-lived choice with its own OK/Annuleren, not another scrollable list of options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeStockTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = SoftCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.more_notifications_time_label),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}


/**
 * "Data overzetten" — two cards of unequal weight (2026-08 dialog review): Exporteren carries
 * the sheet's one full-width filled primary button (cross-cutting rule #2), Importeren a lighter
 * outlined one, since picking a file is only the *start* of importing — [ImportPreviewDialog]
 * is where that action actually commits. [onExport] fires with whatever [scope] is currently
 * selected; [onPickImportFile] only launches the system file picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportExportDialog(
    scope: ExportScope,
    onScopeChange: (ExportScope) -> Unit,
    inventoryItemCount: Int,
    shoppingListItemCount: Int,
    recipeItemCount: Int,
    storeItemCount: Int,
    lastExportTimestamp: Long?,
    onExport: () -> Unit,
    onPickImportFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetTitle(
                title = stringResource(R.string.more_data_csv_title),
                subtitle = stringResource(R.string.more_data_sheet_subtitle),
            )
            ExportCard(
                scope = scope,
                onScopeChange = onScopeChange,
                inventoryItemCount = inventoryItemCount,
                shoppingListItemCount = shoppingListItemCount,
                recipeItemCount = recipeItemCount,
                storeItemCount = storeItemCount,
                lastExportTimestamp = lastExportTimestamp,
                onExport = { onExport(); onDismiss() },
            )
            ImportCard(scope = scope, onPickImportFile = { onPickImportFile(); onDismiss() })
        }
    }
}

/** The heavier of the two cards — outlined, primary/container-green tinted, ends on the sheet's
 *  one filled primary button. The scope pills and the item counts underneath them are the same
 *  number: picking "Lijsten" doesn't just relabel the button, [exportSubtitleFor] below actually
 *  recomputes what's about to be exported. */
@Composable
private fun ExportCard(
    scope: ExportScope,
    onScopeChange: (ExportScope) -> Unit,
    inventoryItemCount: Int,
    shoppingListItemCount: Int,
    recipeItemCount: Int,
    storeItemCount: Int,
    lastExportTimestamp: Long?,
    onExport: () -> Unit,
) {
    Surface(
        shape = SoftCardShapeCompact,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(R.string.more_export_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = exportSubtitleFor(scope, inventoryItemCount, shoppingListItemCount, recipeItemCount, storeItemCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Horizontally scrollable, not wrapping — six scope pills no longer fit one line on
            // every phone width now that Recepten/Winkels/Maaltijden historie joined the
            // original Voorraad/Lijsten/Alles three.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ExportScope.entries.forEach { option ->
                    SheetChip(
                        label = stringResource(option.labelRes),
                        selected = scope == option,
                        onClick = { onScopeChange(option) },
                    )
                }
            }
            SheetPrimaryButton(text = stringResource(R.string.more_export_title), onClick = onExport)
            Text(
                text = lastExportTimestamp?.let { stringResource(R.string.more_export_last_format, formatExportDate(it)) }
                    ?: stringResource(R.string.more_export_last_never),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun exportSubtitleFor(
    scope: ExportScope,
    inventoryItemCount: Int,
    shoppingListItemCount: Int,
    recipeItemCount: Int,
    storeItemCount: Int,
): String = when (scope) {
    ExportScope.INVENTORY -> pluralStringResource(R.plurals.more_export_subtitle_inventory_format, inventoryItemCount, inventoryItemCount)
    ExportScope.LISTS -> pluralStringResource(R.plurals.more_export_subtitle_lists_format, shoppingListItemCount, shoppingListItemCount)
    ExportScope.RECIPES -> pluralStringResource(R.plurals.more_export_subtitle_recipes_format, recipeItemCount, recipeItemCount)
    ExportScope.STORES -> pluralStringResource(R.plurals.more_export_subtitle_stores_format, storeItemCount, storeItemCount)
    // No live count here on purpose — unlike the others, this would mean a fresh 180-day
    // Firestore range read on every recomposition just to label a chip, for a number the
    // household is about to see for real in the exported file moments later anyway.
    ExportScope.MEAL_HISTORY -> stringResource(R.string.more_export_subtitle_meal_history)
    ExportScope.ALL -> stringResource(R.string.more_export_subtitle_all_format, inventoryItemCount, shoppingListItemCount)
}

private fun formatExportDate(timestampMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.LONG).format(Date(timestampMillis))

/** The lighter of the two cards — amber-tinted (same [MaterialTheme.colorScheme.tertiaryContainer]
 *  treatment InventoryScreen's own premium rows use), ending on an outlined button rather than a
 *  second filled one. Importing doesn't happen on tap here — this only opens the system file
 *  picker; [ImportPreviewDialog] is the actual commit step for Voorraad — Recepten/Winkels commit
 *  straight away instead, see [ExportScope.importable]'s own doc for why. The button is disabled
 *  entirely for the three scopes with no read side at all (Lijsten/Maaltijden historie/Alles) —
 *  clearer than letting a household pick a file only to be told afterwards it can't be read back. */
@Composable
private fun ImportCard(scope: ExportScope, onPickImportFile: () -> Unit) {
    val importable = scope.importable
    Surface(
        shape = SoftCardShapeCompact,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (importable) 0.35f else 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(R.string.more_import_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = when (scope) {
                            ExportScope.RECIPES -> stringResource(R.string.more_import_subtitle_recipes)
                            ExportScope.STORES -> stringResource(R.string.more_import_subtitle_stores)
                            else -> stringResource(R.string.more_import_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = when {
                    !importable -> stringResource(R.string.more_import_not_available)
                    scope == ExportScope.INVENTORY -> stringResource(R.string.more_import_info)
                    else -> stringResource(R.string.more_import_info_direct)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onPickImportFile,
                enabled = importable,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Icon(imageVector = Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.more_import_choose_file_action))
            }
        }
    }
}

/**
 * The preview step the "Bestand kiezen" row's own copy promises — nothing from [result] has
 * been written to Voorraad yet; [onConfirm] is the only thing that does, via MoreScreen's
 * confirmImport(). Dismissing (drag, scrim, or system back) discards the parsed rows outright,
 * same as picking a different file would.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreviewDialog(
    result: InventoryImportResult,
    categoryLabel: (String) -> String,
    unitLabel: (String?) -> String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetTitle(
                title = stringResource(R.string.more_import_preview_title),
                subtitle = if (result.skippedCount > 0) {
                    pluralStringResource(R.plurals.more_import_preview_subtitle_format, result.rows.size, result.rows.size) +
                        " " + stringResource(R.string.more_import_preview_skipped_format, result.skippedCount)
                } else {
                    pluralStringResource(R.plurals.more_import_preview_subtitle_format, result.rows.size, result.rows.size)
                },
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(result.rows) { row ->
                    ImportPreviewRow(row = row, categoryLabel = categoryLabel, unitLabel = unitLabel)
                }
            }
            SheetPrimaryButton(text = stringResource(R.string.more_import_preview_confirm), onClick = onConfirm)
        }
    }
}

@Composable
private fun ImportPreviewRow(row: ImportedInventoryRow, categoryLabel: (String) -> String, unitLabel: (String?) -> String) {
    Surface(shape = SoftCardShapeCompact, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = categoryLabel(row.categoryKey),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${row.quantity} ${unitLabel(row.unitKey)}".trim(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A category first, not a star rating — "a category is what makes a report actionable" (2026-08
 * dialog review). The message field's placeholder follows the chosen category so the household
 * knows what's actually useful to write, rather than one generic hint for all three.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackDialog(
    onSend: (category: FeedbackCategory, message: String, includeDiagnostics: Boolean) -> Unit,
    onRateApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf<FeedbackCategory?>(null) }
    var message by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }

    val placeholder = when (category) {
        FeedbackCategory.BUG -> stringResource(R.string.more_feedback_placeholder_bug)
        FeedbackCategory.IDEA -> stringResource(R.string.more_feedback_placeholder_idea)
        FeedbackCategory.COMPLIMENT, null -> stringResource(R.string.more_feedback_placeholder)
    }

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(
                title = stringResource(R.string.more_about_feedback),
                subtitle = stringResource(R.string.more_feedback_category_question),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FeedbackCategoryTile(
                    icon = Icons.Filled.BugReport,
                    label = stringResource(R.string.more_feedback_category_bug),
                    accentColor = MaterialTheme.colorScheme.secondary,
                    selected = category == FeedbackCategory.BUG,
                    onClick = { category = FeedbackCategory.BUG },
                    modifier = Modifier.weight(1f),
                )
                FeedbackCategoryTile(
                    icon = Icons.Filled.Lightbulb,
                    label = stringResource(R.string.more_feedback_category_idea),
                    accentColor = MaterialTheme.colorScheme.primary,
                    selected = category == FeedbackCategory.IDEA,
                    onClick = { category = FeedbackCategory.IDEA },
                    modifier = Modifier.weight(1f),
                )
                FeedbackCategoryTile(
                    icon = Icons.Filled.Favorite,
                    label = stringResource(R.string.more_feedback_category_compliment),
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    selected = category == FeedbackCategory.COMPLIMENT,
                    onClick = { category = FeedbackCategory.COMPLIMENT },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text(placeholder) },
                minLines = 4,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.more_feedback_diagnostics_label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.more_feedback_diagnostics_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = includeDiagnostics, onCheckedChange = { includeDiagnostics = it })
            }

            SheetPrimaryButton(
                text = stringResource(R.string.more_feedback_send),
                enabled = category != null,
                onClick = {
                    category?.let { onSend(it, message, includeDiagnostics) }
                    onDismiss()
                },
            )

            // Kept a distinct action from the feedback channel above — a 1-star complaint and an
            // enthusiastic idea shouldn't share one funnel (see this dialog's own review notes).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onRateApp)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.more_feedback_rate_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp).size(16.dp),
                )
                Text(
                    text = stringResource(R.string.more_about_rate_app),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FeedbackCategoryTile(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = SoftCardShapeCompact,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** First letters of up to the first two words of [name], uppercased — "Jip de Vries" -> "JD".
 *  Falls back to an empty string for a blank/empty [name] (callers show an icon instead). */
private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

/**
 * The fixed (non-scrolling) green gradient header — just the "Instellingen" title and a search
 * field for filtering the settings list below by row title. Used to also pin the profile row
 * (photo/initials, device name, household name+code) here, but that moved to be the scrolling
 * list's own first row ([ProfileRow]) once this needed room for search too — see MoreScreen's
 * call site for how [searchQuery] narrows down which rows stay visible.
 */
@Composable
private fun MoreScreenHeader(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.more_settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = contentColor,
        )
        SearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = stringResource(R.string.more_settings_search_placeholder),
            dense = true,
            // Same white-pill-on-green pairing as Voorraad's own header search field — the
            // default outlined styling would barely read against this gradient.
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = SageGreenPrimary,
                unfocusedTextColor = SageGreenPrimary,
                focusedLeadingIconColor = SageGreenPrimary,
                unfocusedLeadingIconColor = SageGreenPrimary,
                focusedTrailingIconColor = SageGreenPrimary,
                unfocusedTrailingIconColor = SageGreenPrimary,
                cursorColor = SageGreenPrimary,
                focusedPlaceholderColor = SageGreenPrimary.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = SageGreenPrimary.copy(alpha = 0.6f),
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/**
 * Profile identity row — a 40dp avatar (photo, or this device's initials) standing in for the
 * usual leading icon, the device's own name, and the household's name as subtitle. Flat row, no
 * Card/background of its own — same [SettingsRow] structure/padding/typography every other
 * settings row uses, per explicit request ("dezelfde layout als de overige menu items, niet
 * hetzelfde als HomeStock Premium"): this is a row among rows, not a second promotional card.
 * Used to be pinned inside [MoreScreenHeader]'s green gradient; now the scrolling list's own
 * first row instead, once the header needed the room for a settings search field.
 */
@Composable
private fun ProfileRow(
    displayName: String?,
    photoPath: String?,
    householdName: String?,
    onClick: () -> Unit,
) {
    val trimmedName = displayName?.trim().takeUnless { it.isNullOrEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = SageGreenPrimaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (photoPath != null) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (trimmedName != null) {
                    Text(
                        text = initialsOf(trimmedName),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSageGreenPrimaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = OnSageGreenPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = trimmedName ?: stringResource(R.string.more_household_member_unnamed),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_default_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The "Recepten, bonnetjes, AI en statistieken" pitch, always visible near the top rather than
 * as one of several scattered "PREMIUM" labels further down — [TopAppBarContainerGradientEnd]
 * is the same dark-green token the app's gradient headers bottom out to, reused here since a
 * card is exactly the kind of bounded surface that color was designed for. Tapping it always
 * opens [onNavigateToPremium], whether or not the household already has it, so it also works as
 * a "manage my plan" entry point once subscribed.
 */
@Composable
private fun PremiumCard(isPremium: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = TopAppBarContainerGradientEnd),
        shape = SoftCardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = stringResource(R.string.more_premium_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.more_premium_promo_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnTopAppBarContainerAccent,
                )
            }
            if (isPremium) {
                Text(
                    text = stringResource(R.string.more_premium_active),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnTopAppBarContainerAccent,
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.secondary,
                ) {
                    Text(
                        text = stringResource(R.string.more_premium_upgrade_action),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

/**
 * A section's worth of [SettingsRow]s, stacked plainly one after another — no card background
 * and no divider lines between rows, per the design review ("de opties hoeven geen gekleurde
 * achtergrond te hebben of gescheiden te worden door een streep"). [rows] takes a list of
 * composable lambdas rather than a list of plain data so each row can keep its own conditional
 * subtitle/trailingLabel/onClick logic exactly as before — this only changes how they're laid
 * out, not what any of them show.
 */
@Composable
private fun SettingsGroup(rows: List<@Composable () -> Unit>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row -> row() }
    }
}

/**
 * Generic tappable settings row: icon, title, optional subtitle. Opens a dialog (or navigates)
 * on tap. [trailingLabel] is a short badge-like label at the far end of the row (e.g. "PREMIUM").
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingLabel: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            // Always rendered, even as an empty string when there's no subtitle — an empty
            // Text still reserves its style's line height, so every row in a group ends up the
            // same total height instead of a subtitle-less one looking more cramped.
            Text(
                text = subtitle ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailingLabel != null) {
            Text(
                text = trailingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Same shape as [SettingsRow] but the whole action is a single boolean, shown as a trailing
 * [Switch] rather than opening a dialog — used for Meldingen and Auto-aanvullen lijst, both of
 * which used to be two toggles bundled inside one shared dialog; the design review flattens
 * each into its own row so the current state (and how to change it) is visible without a tap.
 */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * "Huisgenoten & code" — same row shape as [SettingsRow], but the leading icon is replaced by
 * up to three overlapping member avatars (see [OverlappingAvatars]) so the household's people
 * are visible at a glance instead of a generic icon, per the design review.
 */
@Composable
private fun HouseholdMembersRow(subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A plain house icon, same size/tint/gap as every other row's leading icon (see
        // SettingsRow) — this used to be a stack of member avatars/initials, but a leading
        // element wider than the other rows' icons (28dp vs 22dp) kept nudging this row's title
        // a few dp further right than its neighbors no matter how the gap around it was tuned.
        // A same-size icon sidesteps that entirely; the household's actual composition/photos
        // are one tap away regardless, inside the screen this row opens.
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(R.string.more_household_row_title), style = MaterialTheme.typography.titleSmall) // "Beheer"
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Debug-only row for [BillingRepository.setDebugPremiumOverride] — same visual weight as a
 * plain [SettingsRow] (monochrome icon, title, no explanatory subtitle) but with a trailing
 * [Switch] instead of a click-through, since flipping it is the entire action.
 */
@Composable
private fun DebugPremiumRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(R.string.more_debug_premium_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Not private — [AppSettingsScreen]'s WEERGAVE preview tiles use this same label set. */
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.more_theme_option_system
    ThemeMode.LIGHT -> R.string.more_theme_option_light
    ThemeMode.DARK -> R.string.more_theme_option_dark
}

/**
 * Instellingen > App — Weergave, Taal and Toegankelijkheid merged into one screen (2026-08
 * dialog review), replacing three separate small `AlertDialog`s that each opened over the other.
 * Every control here writes straight through to the live [ThemePreferences]/locale — the whole
 * app (via [com.dtraas.homestock.ui.theme.HomeStockTheme] in MainActivity) recomposes the moment
 * a setting changes, so the "Voorbeeld" card at the bottom needs no theming logic of its own: by
 * the time it renders, it's already under whatever was just picked above, live.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val themePreferences = application.container.themePreferences
    val themeMode by themePreferences.themeMode.collectAsState()
    val largeText by themePreferences.largeText.collectAsState()
    val highContrast by themePreferences.highContrast.collectAsState()
    val currentLanguage = AppLanguage.entries.find { it.tag == LocalConfiguration.current.locales[0].language } ?: AppLanguage.NL

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.more_app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // "Weergave" — more_theme_title's own meaning already, reused rather than a
                // near-duplicate key (this row used to open ThemeDialog under that exact title).
                SectionHeader(stringResource(R.string.more_theme_title))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEach { mode ->
                        ThemePreviewTile(
                            mode = mode,
                            selected = mode == themeMode,
                            onClick = { themePreferences.setThemeMode(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(stringResource(R.string.more_language_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        SheetChip(
                            label = "${language.flag} ${stringResource(language.labelRes)}",
                            selected = language == currentLanguage,
                            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag)) },
                        )
                    }
                }
            }

            Column {
                SectionHeader(stringResource(R.string.more_accessibility_title))
                SettingsGroup(
                    rows = listOf(
                        {
                            SwitchRow(
                                icon = Icons.Filled.Accessibility,
                                title = stringResource(R.string.more_accessibility_large_text_title),
                                subtitle = stringResource(R.string.more_accessibility_large_text_description),
                                checked = largeText,
                                onCheckedChange = { themePreferences.setLargeText(it) },
                            )
                        },
                        {
                            SwitchRow(
                                icon = Icons.Filled.Accessibility,
                                title = stringResource(R.string.more_accessibility_high_contrast_title),
                                subtitle = stringResource(R.string.more_accessibility_high_contrast_description),
                                checked = highContrast,
                                onCheckedChange = { themePreferences.setHighContrast(it) },
                            )
                        },
                    ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // "Voorbeeld" — more_import_preview_title's own meaning, reused rather than a
                // near-duplicate key.
                SectionHeader(stringResource(R.string.more_import_preview_title))
                AppPreviewCard()
            }
        }
    }
}

/** One WEERGAVE tile — a miniature mock of what that theme actually looks like (a light or dark
 *  swatch with two text-line bars) rather than a plain radio row, so the choice is visible, not
 *  just named. "Systeem" splits the swatch in half to show it isn't committing to either. */
@Composable
private fun ThemePreviewTile(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(SoftCardShapeCompact).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(SoftCardShapeCompact)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = SoftCardShapeCompact,
                ),
        ) {
            when (mode) {
                ThemeMode.LIGHT -> ThemeSwatch(background = LinenBackground, ink = LinenInk, modifier = Modifier.fillMaxSize())
                ThemeMode.DARK -> ThemeSwatch(background = LinenBackgroundDark, ink = LinenInkDark, modifier = Modifier.fillMaxSize())
                ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                    ThemeSwatch(background = LinenBackground, ink = LinenInk, modifier = Modifier.weight(1f).fillMaxHeight())
                    ThemeSwatch(background = LinenBackgroundDark, ink = LinenInkDark, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        Text(
            text = stringResource(mode.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeSwatch(background: Color, ink: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.width(26.dp).height(4.dp).background(ink, RoundedCornerShape(2.dp)))
            Box(Modifier.width(18.dp).height(4.dp).background(ink.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
        }
    }
}

/**
 * The "Voorbeeld" card — a real inventory row (same [ProductImage]/[QuantityStepper] components
 * InventoryScreen's own rows use, not a lookalike) with made-up but realistic content, so
 * Weergave/Toegankelijkheid choices above are judged against something that actually looks like
 * the rest of the app rather than an abstract swatch.
 */
@Composable
private fun AppPreviewCard() {
    var previewQuantity by remember { mutableStateOf(3) }
    Surface(
        shape = SoftCardShapeCompact,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = null,
                fallbackIcon = Category.ZUIVEL.icon,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(stringResource(R.string.more_app_settings_preview_product_name), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.more_app_settings_preview_product_meta),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QuantityStepper(
                quantity = previewQuantity,
                onDecrease = { if (previewQuantity > 0) previewQuantity-- },
                onIncrease = { previewQuantity++ },
                dense = true,
            )
        }
    }
}

/**
 * Rebuilt as a bottom sheet (2026-08 dialog review): a subtitle stating what the order is
 * actually for (it's the shopping list's own store-section order), rows the household can
 * drag to reorder instead of a fixed list, a real "N items op de lijst" count per store instead
 * of a bare name, and delete moved off an instant rim `close` button into a per-row overflow
 * that asks first — see [pendingDelete] below.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StoresDialog(
    stores: List<StoreEntity>,
    itemCountByStore: Map<String, Int>,
    onAdd: (String) -> Unit,
    onRemove: (StoreEntity) -> Unit,
    onMove: (store: StoreEntity, previous: StoreEntity?, next: StoreEntity?) -> Unit,
    onDismiss: () -> Unit,
) {
    var newStoreName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<StoreEntity?>(null) }

    val existingNames = remember(stores) { stores.map { it.name.lowercase() }.toSet() }
    val suggestedChains = remember(existingNames) {
        listOf("Aldi", "Plus", "Dirk", "Coop", "Markt").filter { it.lowercase() !in existingNames }
    }

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(
                title = stringResource(R.string.more_stores_title),
                subtitle = stringResource(R.string.more_stores_subtitle),
            )

            if (stores.isEmpty()) {
                Text(
                    text = stringResource(R.string.more_stores_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ReorderableStoreList(
                    stores = stores,
                    itemCountByStore = itemCountByStore,
                    onMove = onMove,
                    onDeleteRequest = { pendingDelete = it },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetEyebrow(text = stringResource(R.string.more_stores_add_section))
                if (suggestedChains.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestedChains.forEach { chain ->
                            SheetChip(label = "+ $chain", selected = false, onClick = { onAdd(chain) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newStoreName,
                        onValueChange = { newStoreName = it },
                        placeholder = { Text(stringResource(R.string.more_stores_other_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = { onAdd(newStoreName.trim()); newStoreName = "" },
                        enabled = newStoreName.isNotBlank(),
                        modifier = Modifier.size(50.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.store_add_action))
                    }
                }
            }
        }
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        val count = itemCountByStore[deleteTarget.name] ?: 0
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.more_stores_delete_title_format, deleteTarget.name)) },
            text = {
                Text(
                    if (count > 0) {
                        pluralStringResource(R.plurals.more_stores_delete_message_format, count, count)
                    } else {
                        stringResource(R.string.more_stores_delete_message_empty)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(deleteTarget); pendingDelete = null }) {
                    Text(stringResource(R.string.more_stores_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** [store]'s own custom [StoreEntity.aisleOrder] turned into a full, always-11-long [Category]
 *  list — the explicitly ordered ones first, then every category the household hasn't placed
 *  yet, appended in [Category]'s own fixed [Category.sortOrder] order (also the entire list, for
 *  a store that's never been customized at all — [StoreEntity.aisleOrder] empty). Same fallback
 *  merge as `ShoppingListViewModel`'s own AISLE-mode rank map, just materialized as an ordered
 *  list here instead of a rank lookup, since this dialog needs an actual sequence to render and
 *  reorder rather than just something to sort by. */
private fun fullAisleOrderFor(store: StoreEntity): List<Category> {
    val custom = store.aisleOrder.mapNotNull { key -> Category.entries.find { it.storageKey == key } }
    val remaining = Category.entries.sortedBy { it.sortOrder }.filterNot { it in custom }
    return custom + remaining
}

/**
 * Picks which store to edit, then reorders that store's own gangvolgorde (see
 * [StoreEntity.aisleOrder]) — up/down arrows on each of the (fixed, always-11) [Category] rows
 * rather than [ReorderableStoreList]'s drag gesture below: a short, fixed-length list is just as
 * easy to reorder a step at a time, without that machinery. Two steps, not a single combined
 * screen, because a household with several stores needs to pick one before "up"/"down" means
 * anything — same "pick, then edit" shape as [StoresDialog] itself picking a store to rename/
 * delete, just one level deeper here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AisleOrderDialog(
    stores: List<StoreEntity>,
    onSetOrder: (StoreEntity, List<Category>) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingStore by remember { mutableStateOf<StoreEntity?>(null) }

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        val store = editingStore
        if (store == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SheetTitle(
                    title = stringResource(R.string.more_aisle_order_title),
                    subtitle = stringResource(R.string.more_aisle_order_subtitle),
                )
                if (stores.isEmpty()) {
                    Text(
                        text = stringResource(R.string.more_aisle_order_no_stores),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        stores.forEach { entry ->
                            SheetActionRow(
                                icon = Icons.Filled.Storefront,
                                title = entry.name,
                                onClick = { editingStore = entry },
                            )
                        }
                    }
                }
            }
        } else {
            var order by remember(store.id) { mutableStateOf(fullAisleOrderFor(store)) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(sheetContentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { editingStore = null }, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    SheetTitle(
                        title = store.name,
                        subtitle = stringResource(R.string.more_aisle_order_store_subtitle),
                        modifier = Modifier.weight(1f),
                    )
                }
                Column {
                    order.forEachIndexed { index, category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp),
                            )
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(category.displayNameRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            IconButton(
                                onClick = {
                                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    onSetOrder(store, order)
                                },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.more_aisle_order_move_up_cd))
                            }
                            IconButton(
                                onClick = {
                                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    onSetOrder(store, order)
                                },
                                enabled = index < order.lastIndex,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.more_aisle_order_move_down_cd))
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        order = Category.entries.sortedBy { it.sortOrder }
                        onSetOrder(store, order)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.more_aisle_order_reset))
                }
            }
        }
    }
}

/**
 * A flat, freely-swappable drag-to-reorder list — same median-sortOrder swap mechanics as
 * ShoppingListScreen's `ReorderableShoppingList`, simplified since stores have no
 * checked/unchecked or cross-store-boundary rules to respect: any row can swap with any
 * neighbor. [onDeleteRequest] routes into the confirm-first dialog rather than deleting inline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableStoreList(
    stores: List<StoreEntity>,
    itemCountByStore: Map<String, Int>,
    onMove: (StoreEntity, StoreEntity?, StoreEntity?) -> Unit,
    onDeleteRequest: (StoreEntity) -> Unit,
) {
    val orderedStores = remember { mutableStateListOf<StoreEntity>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var draggingRowHeightPx by remember { mutableFloatStateOf(0f) }

    if (draggingId == null) {
        LaunchedEffect(stores) {
            orderedStores.clear()
            orderedStores.addAll(stores)
        }
    }

    fun handleDrag(deltaY: Float) {
        val id = draggingId ?: return
        dragOffsetPx += deltaY
        val rowHeight = draggingRowHeightPx.takeIf { it > 0f } ?: return
        while (true) {
            val index = orderedStores.indexOfFirst { it.id == id }
            if (index < 0) break
            if (dragOffsetPx > rowHeight / 2f && index < orderedStores.lastIndex) {
                orderedStores.add(index, orderedStores.removeAt(index + 1))
                dragOffsetPx -= rowHeight
            } else if (dragOffsetPx < -rowHeight / 2f && index > 0) {
                orderedStores.add(index - 1, orderedStores.removeAt(index))
                dragOffsetPx += rowHeight
            } else {
                break
            }
        }
    }

    fun commitDrag() {
        val id = draggingId
        val index = if (id != null) orderedStores.indexOfFirst { it.id == id } else -1
        if (index >= 0) {
            val store = orderedStores[index]
            val previous = orderedStores.getOrNull(index - 1)
            val next = orderedStores.getOrNull(index + 1)
            if (previous != null || next != null) onMove(store, previous, next)
        }
        draggingId = null
        dragOffsetPx = 0f
        draggingRowHeightPx = 0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        orderedStores.forEach { store ->
            val isDragging = store.id == draggingId
            val count = itemCountByStore[store.name] ?: 0
            val inUse = count > 0
            Surface(
                shape = SoftCardShapeCompact,
                color = if (inUse) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = if (isDragging) 3.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(0, if (isDragging) dragOffsetPx.roundToInt() else 0) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { if (isDragging) draggingRowHeightPx = it.size.height.toFloat() }
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragIndicator,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .pointerInput(store.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingId = store.id; dragOffsetPx = 0f },
                                    onDragEnd = { commitDrag() },
                                    onDragCancel = { commitDrag() },
                                    onDrag = { change, dragAmount -> change.consume(); handleDrag(dragAmount.y) },
                                )
                            },
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (inUse) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            text = store.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (inUse) {
                                pluralStringResource(R.plurals.more_stores_item_count_format, count, count)
                            } else {
                                stringResource(R.string.more_stores_unused)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more_stores_row_options_cd, store.name),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.more_stores_remove_format, store.name), color = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menuExpanded = false; onDeleteRequest(store) },
                            )
                        }
                    }
                }
            }
        }
    }
}

