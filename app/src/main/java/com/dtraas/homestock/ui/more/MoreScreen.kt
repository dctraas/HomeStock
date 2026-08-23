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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.export.CsvExporter
import com.dtraas.homestock.data.export.CsvImporter
import com.dtraas.homestock.data.export.InventoryCsvHeaders
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerGradientEnd
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnSageGreenPrimaryContainer
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimaryContainer
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.TopAppBarContainerGradientEnd
import com.dtraas.homestock.work.ExpiryCheckWorker
import com.dtraas.homestock.work.LowStockCheckWorker
import com.dtraas.homestock.work.PremiumTrialCheckWorker
import com.dtraas.homestock.work.WasteSummaryWorker
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class AppLanguage(val tag: String, val labelRes: Int) {
    NL("nl", R.string.more_language_option_nl),
    EN("en", R.string.more_language_option_en),
    DE("de", R.string.more_language_option_de),
    FR("fr", R.string.more_language_option_fr),
    ES("es", R.string.more_language_option_es),
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
    onNavigateToAccountLink: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val notificationPreferences = application.container.notificationPreferences
    val notificationsEnabled by notificationPreferences.expiryNotificationsEnabled.collectAsState()
    val inventoryInsightNotificationsEnabled by notificationPreferences.inventoryInsightNotificationsEnabled.collectAsState()
    val premiumNotificationsEnabled by notificationPreferences.premiumNotificationsEnabled.collectAsState()
    val householdActivityNotificationsEnabled by notificationPreferences.householdActivityNotificationsEnabled.collectAsState()
    val themePreferences = application.container.themePreferences
    val themeMode by themePreferences.themeMode.collectAsState()
    val largeText by themePreferences.largeText.collectAsState()
    val highContrast by themePreferences.highContrast.collectAsState()
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
    val feedbackRepository = application.container.feedbackRepository
    val accountLinkRepository = application.container.accountLinkRepository
    val isAccountLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)
    val currentLanguage = AppLanguage.entries.find { it.tag == LocalConfiguration.current.locales[0].language } ?: AppLanguage.NL

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackSentMessage = stringResource(R.string.more_feedback_sent_confirmation)
    val feedbackErrorMessage = stringResource(R.string.more_feedback_error)

    var showProfileDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStoresDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }

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

    // CSV export (Voorraad) — moved verbatim from the now-gone MoreOptionsScreen.kt: the CSV
    // content has to be built *before* the system's "save to..." picker is launched (it needs a
    // filename up front, but only hands back a Uri once the user has actually picked a location,
    // well after this composable has moved on), so it's held here and written once that callback
    // fires.
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

    fun exportInventory() {
        coroutineScope.launch {
            val items = application.container.inventoryRepository.observeInventoryWithProduct().first()
            pendingExportCsv = CsvExporter.inventoryToCsv(
                items,
                inventoryCsvHeaders,
                categoryLabel = { key -> categoryLabels[key] ?: key },
                unitLabel = { key -> unitLabels[key] ?: (key ?: "") },
                yesLabel = csvYes,
                noLabel = csvNo,
            )
            exportLauncher.launch("voorraad.csv")
        }
    }

    // CSV import (Voorraad) — moved verbatim from the now-gone MoreOptionsScreen.kt: every
    // imported row becomes a brand-new product (synthetic "csv-..." barcode, same convention
    // AI-productherkenning uses for products with no real barcode) rather than trying to match it
    // against an existing one, since a CSV has no barcode column to match on. restoreItem (not
    // recordScan) writes the inventory row directly without logging it to Geschiedenis — a bulk
    // import shouldn't flood the activity log with one entry per row.
    val importErrorMessage = stringResource(R.string.more_import_error)
    val importEmptyMessage = stringResource(R.string.more_import_empty)
    val importSuccessFormat = stringResource(R.string.more_import_success_format)
    val importSkippedFormat = stringResource(R.string.more_import_skipped_format)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val message = try {
                val csv = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (csv == null) {
                    importErrorMessage
                } else {
                    val result = CsvImporter.parseInventoryCsv(csv, categoryKeyByLabel, unitKeyByLabel, csvYes)
                    if (result.rows.isEmpty()) {
                        importEmptyMessage
                    } else {
                        result.rows.forEach { row ->
                            val barcode = "csv-${UUID.randomUUID()}"
                            application.container.productRepository.saveManualProduct(
                                barcode = barcode,
                                name = row.name,
                                category = Category.fromStorageKey(row.categoryKey),
                                brand = row.brand,
                                unit = row.unitKey,
                            )
                            application.container.inventoryRepository.restoreItem(
                                barcode = barcode,
                                quantity = row.quantity,
                                expirationDate = row.expirationDate,
                                minQuantity = row.minQuantity,
                                note = row.note,
                                isFavorite = row.isFavorite,
                            )
                        }
                        val summary = String.format(importSuccessFormat, result.rows.size)
                        if (result.skippedCount > 0) {
                            summary + " " + String.format(importSkippedFormat, result.skippedCount)
                        } else {
                            summary
                        }
                    }
                }
            } catch (e: Exception) {
                importErrorMessage
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    fun importInventory() {
        importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "*/*"))
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
                            SwitchRow(
                                icon = Icons.Filled.Notifications,
                                title = stringResource(R.string.more_notifications_row_title),
                                subtitle = stringResource(R.string.more_expiry_notifications_description),
                                checked = notificationsEnabled,
                                onCheckedChange = ::setNotificationsEnabled,
                            )
                        },
                        {
                            SwitchRow(
                                icon = Icons.Filled.Inventory2,
                                title = stringResource(R.string.more_inventory_insight_notifications_title),
                                subtitle = stringResource(R.string.more_inventory_insight_notifications_subtitle),
                                checked = inventoryInsightNotificationsEnabled,
                                onCheckedChange = ::setInventoryInsightNotificationsEnabled,
                            )
                        },
                        {
                            SwitchRow(
                                icon = Icons.Filled.Groups,
                                title = stringResource(R.string.more_household_activity_notifications_title),
                                subtitle = stringResource(R.string.more_household_activity_notifications_subtitle),
                                checked = householdActivityNotificationsEnabled,
                                onCheckedChange = ::setHouseholdActivityNotificationsEnabled,
                            )
                        },
                        {
                            SwitchRow(
                                icon = Icons.Filled.WorkspacePremium,
                                title = stringResource(R.string.more_premium_notifications_title),
                                subtitle = stringResource(R.string.more_premium_notifications_subtitle),
                                checked = premiumNotificationsEnabled,
                                onCheckedChange = ::setPremiumNotificationsEnabled,
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Language,
                                title = stringResource(R.string.more_language_title),
                                subtitle = stringResource(currentLanguage.labelRes),
                                onClick = { showLanguageDialog = true },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.DarkMode,
                                title = stringResource(R.string.more_theme_title),
                                subtitle = stringResource(themeMode.labelRes()),
                                onClick = { showThemeDialog = true },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Accessibility,
                                title = stringResource(R.string.more_accessibility_title),
                                subtitle = accessibilitySubtitle(largeText, highContrast),
                                onClick = { showAccessibilityDialog = true },
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
                                trailingLabel = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                                onClick = { if (isPremium) showImportExportDialog = true else onNavigateToPremium() },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Feedback,
                                title = stringResource(R.string.more_about_feedback),
                                onClick = { showFeedbackDialog = true },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.StarRate,
                                title = stringResource(R.string.more_about_rate_app),
                                onClick = { openPlayStoreListing(context) },
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.PrivacyTip,
                                title = stringResource(R.string.more_about_privacy_policy),
                                onClick = onNavigateToPrivacyPolicy,
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Description,
                                title = stringResource(R.string.more_about_licenses),
                                onClick = onNavigateToLicenses,
                            )
                        },
                    ),
                )

                // Helemaal aan het einde van de pagina in plaats van vlak onder de header — daar
                // liet de kaart een hoop lege ruimte eronder voordat Huishouden begon.
                PremiumCard(
                    isPremium = isPremium,
                    onClick = onNavigateToPremium,
                    modifier = Modifier.padding(top = 6.dp),
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

    if (showThemeDialog) {
        ThemeDialog(
            selected = themeMode,
            onSelect = { themePreferences.setThemeMode(it) },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = currentLanguage,
            onSelect = { language ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showStoresDialog) {
        StoresDialog(
            stores = stores,
            onAdd = { name -> coroutineScope.launch { storeRepository.addStore(name) } },
            onRemove = { id -> coroutineScope.launch { storeRepository.removeStore(id) } },
            onDismiss = { showStoresDialog = false },
        )
    }

    if (showAccessibilityDialog) {
        AccessibilityDialog(
            largeText = largeText,
            onLargeTextChange = { themePreferences.setLargeText(it) },
            highContrast = highContrast,
            onHighContrastChange = { themePreferences.setHighContrast(it) },
            onDismiss = { showAccessibilityDialog = false },
        )
    }

    if (showImportExportDialog) {
        ImportExportDialog(
            onImport = ::importInventory,
            onExport = ::exportInventory,
            onDismiss = { showImportExportDialog = false },
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onSend = { rating, message ->
                coroutineScope.launch {
                    try {
                        feedbackRepository.submit(rating, message)
                        snackbarHostState.showSnackbar(feedbackSentMessage, duration = SnackbarDuration.Short)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(feedbackErrorMessage, duration = SnackbarDuration.Short)
                    }
                }
            },
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

/** Subtitle for the Toegankelijkheid row — "Uit" when neither toggle is on, otherwise the
 *  enabled option(s) joined so the row itself already shows current state without opening
 *  the dialog. */
@Composable
private fun accessibilitySubtitle(largeText: Boolean, highContrast: Boolean): String {
    val enabled = listOfNotNull(
        stringResource(R.string.more_accessibility_large_text_title).takeIf { largeText },
        stringResource(R.string.more_accessibility_high_contrast_title).takeIf { highContrast },
    )
    return if (enabled.isEmpty()) stringResource(R.string.common_off) else enabled.joinToString(", ")
}

@Composable
private fun AccessibilityDialog(
    largeText: Boolean,
    onLargeTextChange: (Boolean) -> Unit,
    highContrast: Boolean,
    onHighContrastChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_accessibility_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.more_accessibility_large_text_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.more_accessibility_large_text_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = largeText, onCheckedChange = onLargeTextChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.more_accessibility_high_contrast_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.more_accessibility_high_contrast_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = highContrast, onCheckedChange = onHighContrastChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        },
    )
}

/**
 * Single entry point for Voorraad CSV import/export — replaces what used to be two separate
 * export-only rows (Voorraad, Boodschappenlijst) with one row that opens this choice.
 * [onImport]/[onExport] only kick off the picker flow — the actual file I/O runs in the
 * caller's launchers.
 */
@Composable
private fun ImportExportDialog(onImport: () -> Unit, onExport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_data_csv_title)) },
        text = {
            Column {
                // Only Voorraad has import/export today, but this dialog is meant to grow
                // (Boodschappenlijst, etc.) — the heading labels which data these two actions
                // apply to now, rather than implying "Data overzetten" only ever means Voorraad.
                Text(
                    text = stringResource(R.string.inventory_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onImport()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.more_csv_import_action), modifier = Modifier.padding(start = 12.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onExport()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.more_csv_export_action), modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun FeedbackDialog(onSend: (rating: Int, message: String) -> Unit, onDismiss: () -> Unit) {
    var rating by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_about_feedback)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (star in 1..5) {
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.more_feedback_star_cd, star),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text(stringResource(R.string.more_feedback_placeholder)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = rating > 0,
                onClick = {
                    onSend(rating, message)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.more_feedback_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
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
            .background(Brush.verticalGradient(listOf(LocalTopAppBarContainerColor.current, LocalTopAppBarContainerGradientEnd.current)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .padding(bottom = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.more_settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = contentColor,
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.more_theme_option_system
    ThemeMode.LIGHT -> R.string.more_theme_option_light
    ThemeMode.DARK -> R.string.more_theme_option_dark
}

@Composable
private fun ThemeDialog(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_theme_title)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(mode)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = { onSelect(mode); onDismiss() })
                        Text(stringResource(mode.labelRes()), modifier = Modifier.padding(start = 8.dp))
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
private fun LanguageDialog(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_language_title)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(language)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == selected, onClick = { onSelect(language); onDismiss() })
                        Text(stringResource(language.labelRes), modifier = Modifier.padding(start = 8.dp))
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
private fun StoresDialog(
    stores: List<StoreEntity>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newStoreName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_stores_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (stores.isEmpty()) {
                        Text(
                            text = stringResource(R.string.more_stores_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    stores.forEach { store ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(store.name, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { onRemove(store.id) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.more_stores_remove_format, store.name),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newStoreName,
                        onValueChange = { newStoreName = it },
                        label = { Text(stringResource(R.string.store_add_dialog_title)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newStoreName.isNotBlank(),
                        onClick = {
                            onAdd(newStoreName.trim())
                            newStoreName = ""
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(stringResource(R.string.store_add_action))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        },
    )
}
