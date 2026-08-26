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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
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
import com.dtraas.homestock.data.export.ShoppingListCsvHeaders
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.data.repository.FeedbackCategory
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.ProductImage
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.components.QuantityStepper
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
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The three-way choice in the Data-overzetten sheet's export card — which of the household's
 *  own data ends up in the CSV. Distinct from [CsvExporter], which doesn't know about "scope"
 *  at all — it just builds whichever CSV(s) it's asked for; this enum is purely UI/state. */
private enum class ExportScope(val labelRes: Int) {
    // "Voorraad" is exactly inventory_title's own meaning — reused rather than a near-duplicate key.
    INVENTORY(R.string.inventory_title),
    LISTS(R.string.more_data_scope_lists),
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
    val themePreferences = application.container.themePreferences
    val themeMode by themePreferences.themeMode.collectAsState()
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
    val feedbackRepository = application.container.feedbackRepository
    val accountLinkRepository = application.container.accountLinkRepository
    val isAccountLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)
    val currentLanguage = AppLanguage.entries.find { it.tag == LocalConfiguration.current.locales[0].language } ?: AppLanguage.NL

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackSentMessage = stringResource(R.string.more_feedback_sent_confirmation)
    val feedbackErrorMessage = stringResource(R.string.more_feedback_error)

    var showProfileDialog by remember { mutableStateOf(false) }
    var showStoresDialog by remember { mutableStateOf(false) }
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

    fun exportData(scope: ExportScope) {
        coroutineScope.launch {
            val inventoryCsv = if (scope != ExportScope.LISTS) {
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
            val listCsv = if (scope != ExportScope.INVENTORY) {
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
            val (csv, filename) = when (scope) {
                ExportScope.INVENTORY -> requireNotNull(inventoryCsv) to "voorraad.csv"
                ExportScope.LISTS -> requireNotNull(listCsv) to "boodschappenlijst.csv"
                ExportScope.ALL -> CsvExporter.combinedToCsv(
                    requireNotNull(inventoryCsv),
                    requireNotNull(listCsv),
                    inventorySectionTitle,
                    shoppingListSectionTitle,
                ) to "homestock-data.csv"
            }
            pendingExportCsv = csv
            exportLauncher.launch(filename)
        }
    }

    // CSV import (Voorraad) — moved+extended from the now-gone MoreOptionsScreen.kt: picking a
    // file now only *parses* it into pendingImportPreview — nothing is written to Voorraad until
    // the household confirms in the preview sheet (see confirmImport below), matching "je ziet
    // eerst een voorbeeld" from the settings row's own copy. Every confirmed row becomes a
    // brand-new product (synthetic "csv-..." barcode, same convention AI-productherkenning uses
    // for products with no real barcode) rather than trying to match it against an existing one,
    // since a CSV has no barcode column to match on. restoreItem (not recordScan) writes the
    // inventory row directly without logging it to Geschiedenis — a bulk import shouldn't flood
    // the activity log with one entry per row.
    val importErrorMessage = stringResource(R.string.more_import_error)
    val importEmptyMessage = stringResource(R.string.more_import_empty)
    val importSuccessFormat = stringResource(R.string.more_import_success_format)
    val importSkippedFormat = stringResource(R.string.more_import_skipped_format)
    var pendingImportPreview by remember { mutableStateOf<InventoryImportResult?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val errorMessage = try {
                val csv = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (csv == null) {
                    importErrorMessage
                } else {
                    val result = CsvImporter.parseInventoryCsv(csv, categoryKeyByLabel, unitKeyByLabel, csvYes)
                    if (result.rows.isEmpty()) {
                        importEmptyMessage
                    } else {
                        pendingImportPreview = result
                        null
                    }
                }
            } catch (e: Exception) {
                importErrorMessage
            }
            if (errorMessage != null) snackbarHostState.showSnackbar(errorMessage, duration = SnackbarDuration.Short)
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Profiel + huishouden-subtitel + code leefden voorheen op een losse Card boven het
            // scrollende deel; nu zitten ze vast in de groene gradient-header, net als de andere
            // herbouwde schermen deze ronde.
            MoreScreenHeader(
                displayName = displayName,
                photoPath = photoPath,
                householdName = householdName,
                onClick = { showProfileDialog = true },
            )
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
                // had, niet deze kaart.
                PremiumCard(isPremium = isPremium, onClick = onNavigateToPremium)

                SectionHeader(stringResource(R.string.more_section_household))
                SettingsGroup(
                    rows = listOf(
                        {
                            HouseholdMembersRow(
                                members = members,
                                subtitle = pluralStringResource(
                                    R.plurals.more_household_subtitle_format,
                                    memberCount,
                                    memberCount,
                                    householdId ?: "—",
                                ),
                                onClick = onNavigateToHousehold,
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Storefront,
                                title = stringResource(R.string.more_stores_title),
                                subtitle = stringResource(R.string.more_stores_count_format, stores.size),
                                onClick = { showStoresDialog = true },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.BarChart,
                                title = stringResource(R.string.more_statistics_title),
                                subtitle = stringResource(R.string.more_statistics_subtitle),
                                trailingLabel = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                                onClick = { if (isPremium) onNavigateToStatistics() else onNavigateToPremium() },
                            )
                        },
                        {
                            // Hoort inhoudelijk bij Voorraad/Boodschappenlijst-gedrag, niet bij
                            // een apparaat-instelling — de App-sectie hieronder gaat terug naar
                            // precies de oorspronkelijke vijf rijen, dus deze schakelaar (die er
                            // later bijkwam) verhuist hierheen in plaats van te verdwijnen.
                            SwitchRow(
                                icon = Icons.Filled.ShoppingCart,
                                title = stringResource(R.string.more_auto_restock_title),
                                subtitle = stringResource(R.string.more_auto_restock_subtitle),
                                checked = autoRestockEnabled,
                                onCheckedChange = inventoryPreferences::setAutoRestockEnabled,
                            )
                        },
                    ),
                )

                SectionHeader(stringResource(R.string.more_section_preferences))
                SettingsGroup(
                    rows = listOf(
                        {
                            // The four notification toggles used to live inline here, one row
                            // each — folded into their own submenu (per design review) since
                            // that's four full icon+title+subtitle rows just for one App
                            // sub-topic, same "group it behind one row" treatment the App row
                            // below gets for Weergave/Taal/Toegankelijkheid.
                            SettingsRow(
                                icon = Icons.Filled.Notifications,
                                title = stringResource(R.string.more_notifications_menu_title),
                                subtitle = notificationsSubtitle(
                                    notificationsEnabled,
                                    inventoryInsightNotificationsEnabled,
                                    householdActivityNotificationsEnabled,
                                    premiumNotificationsEnabled,
                                ),
                                onClick = { showNotificationsDialog = true },
                            )
                        },
                        {
                            // Weergave, Taal en Toegankelijkheid used to be three separate rows,
                            // each opening its own small AlertDialog — collapsed into this one
                            // row (2026-08 dialog review) since together they're one coherent
                            // "how the app looks and reads" topic, better served by a real
                            // screen (with a live preview) than three disconnected popups.
                            SettingsRow(
                                icon = Icons.Filled.Tune,
                                title = stringResource(R.string.more_app_settings_title),
                                subtitle = stringResource(
                                    R.string.more_app_settings_subtitle_format,
                                    stringResource(themeMode.labelRes()),
                                    "${currentLanguage.flag} ${stringResource(currentLanguage.labelRes)}",
                                ),
                                onClick = onNavigateToApp,
                            )
                        },
                    ),
                )

                SectionHeader(stringResource(R.string.more_section_support))
                SettingsGroup(
                    rows = listOf(
                        {
                            SettingsRow(
                                icon = Icons.Filled.VerifiedUser,
                                title = stringResource(R.string.account_link_row_title),
                                subtitle = if (isAccountLinked) {
                                    stringResource(R.string.account_link_row_subtitle_linked_format, accountLinkRepository.linkedEmail ?: "—")
                                } else {
                                    stringResource(R.string.account_link_row_subtitle_unlinked)
                                },
                                onClick = onNavigateToAccountLink,
                            )
                        },
                        {
                            // Hoorde eerst thuis in de App-sectie tussen de andere apparaat-
                            // instellingen; verhuisd naar Ondersteuning, direct onder Account
                            // koppelen, op uitdrukkelijk verzoek.
                            SettingsRow(
                                icon = Icons.Filled.ImportExport,
                                title = stringResource(R.string.more_data_csv_title),
                                subtitle = stringResource(R.string.more_data_csv_subtitle),
                                trailingLabel = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                                onClick = { if (isPremium) showImportExportDialog = true else onNavigateToPremium() },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Feedback,
                                title = stringResource(R.string.more_about_feedback),
                                subtitle = stringResource(R.string.more_about_feedback_subtitle),
                                onClick = { showFeedbackDialog = true },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.StarRate,
                                title = stringResource(R.string.more_about_rate_app),
                                subtitle = stringResource(R.string.more_about_rate_app_subtitle),
                                onClick = { openPlayStoreListing(context) },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.PrivacyTip,
                                title = stringResource(R.string.more_about_privacy_policy),
                                subtitle = stringResource(R.string.more_about_privacy_policy_subtitle),
                                onClick = onNavigateToPrivacyPolicy,
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Description,
                                title = stringResource(R.string.more_about_licenses),
                                subtitle = stringResource(R.string.more_about_licenses_subtitle),
                                onClick = onNavigateToLicenses,
                            )
                        },
                    ),
                )

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

/** Subtitle for the Meldingen row — how many of the four toggles are on, same "summarize before
 *  you open it" idea as [accessibilitySubtitle]. */
@Composable
private fun notificationsSubtitle(vararg enabled: Boolean): String {
    val onCount = enabled.count { it }
    return if (onCount == 0) {
        stringResource(R.string.common_off)
    } else {
        pluralStringResource(R.plurals.more_notifications_menu_subtitle_format, onCount, onCount, enabled.size)
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
                lastExportTimestamp = lastExportTimestamp,
                onExport = { onExport(); onDismiss() },
            )
            ImportCard(onPickImportFile = { onPickImportFile(); onDismiss() })
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
                        text = exportSubtitleFor(scope, inventoryItemCount, shoppingListItemCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun exportSubtitleFor(scope: ExportScope, inventoryItemCount: Int, shoppingListItemCount: Int): String = when (scope) {
    ExportScope.INVENTORY -> pluralStringResource(R.plurals.more_export_subtitle_inventory_format, inventoryItemCount, inventoryItemCount)
    ExportScope.LISTS -> pluralStringResource(R.plurals.more_export_subtitle_lists_format, shoppingListItemCount, shoppingListItemCount)
    ExportScope.ALL -> stringResource(R.string.more_export_subtitle_all_format, inventoryItemCount, shoppingListItemCount)
}

private fun formatExportDate(timestampMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.LONG).format(Date(timestampMillis))

/** The lighter of the two cards — amber-tinted (same [MaterialTheme.colorScheme.tertiaryContainer]
 *  treatment InventoryScreen's own premium rows use), ending on an outlined button rather than a
 *  second filled one. Importing doesn't happen on tap here — this only opens the system file
 *  picker; [ImportPreviewDialog] is the actual commit step. */
@Composable
private fun ImportCard(onPickImportFile: () -> Unit) {
    Surface(
        shape = SoftCardShapeCompact,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
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
                        text = stringResource(R.string.more_import_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.more_import_info),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onPickImportFile,
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
 * The fixed (non-scrolling) green gradient header — "Instellingen" title, then the profile row:
 * a 56dp squircle avatar (photo, or this device's initials), the device's own name, and a
 * subtitle combining the household's name, member count and join code, so the one thing every
 * household member sets up early (their name) and the household they're in are both visible
 * without opening anything ("Profielnaam moet in de groene header vallen", matching artboard 1f
 * in the uploaded mockup). Replaces the old flat HomeStockTopAppBar plus a separate profile Card
 * that used to sit above the scrolling content.
 */
@Composable
private fun MoreScreenHeader(
    displayName: String?,
    photoPath: String?,
    householdName: String?,
    onClick: () -> Unit,
) {
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
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val trimmedName = displayName?.trim().takeUnless { it.isNullOrEmpty() }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SageGreenPrimaryContainer,
                modifier = Modifier.size(56.dp),
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
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnSageGreenPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = OnSageGreenPrimaryContainer,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = trimmedName ?: stringResource(R.string.more_household_member_unnamed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_default_name),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = OnTopAppBarContainerAccent,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = contentColor,
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
private fun HouseholdMembersRow(members: List<HouseholdMember>, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlappingAvatars(members = members, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(stringResource(R.string.more_household_row_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Up to 3 small circular avatars (photo, or initials on a tinted circle), each overlapping the
 *  previous by 8dp — a quick "who's in this household" glance next to [HouseholdMembersRow]. */
@Composable
private fun OverlappingAvatars(members: List<HouseholdMember>, modifier: Modifier = Modifier) {
    val visible = members.take(3)
    Row(modifier = modifier) {
        visible.forEachIndexed { index, member ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .size(28.dp)
                    .offset(x = (-8 * index).dp),
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
                        val initials = member.displayName?.trim().takeUnless { it.isNullOrEmpty() }?.let { initialsOf(it) }
                        if (initials != null && initials.isNotEmpty()) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
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

