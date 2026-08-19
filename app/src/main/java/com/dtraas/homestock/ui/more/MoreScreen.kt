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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.R
import com.dtraas.homestock.data.export.CsvExporter
import com.dtraas.homestock.data.export.CsvImporter
import com.dtraas.homestock.data.export.InventoryCsvHeaders
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.work.ExpiryCheckWorker
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
    val themePreferences = application.container.themePreferences
    val themeMode by themePreferences.themeMode.collectAsState()
    val largeText by themePreferences.largeText.collectAsState()
    val highContrast by themePreferences.highContrast.collectAsState()
    val inventoryPreferences = application.container.inventoryPreferences
    val autoRestockEnabled by inventoryPreferences.autoRestockEnabled.collectAsState()
    val householdSession = application.container.householdSession
    val householdId by householdSession.householdId.collectAsState()
    val deviceProfile = application.container.deviceProfile
    val displayName by deviceProfile.displayName.collectAsState()
    val photoPath by deviceProfile.photoPath.collectAsState()
    val feedbackRepository = application.container.feedbackRepository
    val accountLinkRepository = application.container.accountLinkRepository
    val isAccountLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)
    val householdMembersRepository = application.container.householdMembersRepository
    val isPremium by householdMembersRepository.observeHouseholdIsPremium().collectAsState(initial = false)
    val memberCount by householdMembersRepository.observeMemberCount().collectAsState(initial = 0)
    val billingRepository = application.container.billingRepository
    val debugPremiumOverride by billingRepository.debugPremiumOverride.collectAsState()
    val storeRepository = application.container.storeRepository
    val stores by storeRepository.observeStores().collectAsState(initial = emptyList())
    val currentLanguage = AppLanguage.entries.find { it.tag == LocalConfiguration.current.locales[0].language } ?: AppLanguage.NL

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackSentMessage = stringResource(R.string.more_feedback_sent_confirmation)
    val feedbackErrorMessage = stringResource(R.string.more_feedback_error)

    var showProfileDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStoresDialog by remember { mutableStateOf(false) }
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

    // CSV export (Voorraad) — the CSV content has to be built *before* the system's "save to..."
    // picker is launched (it needs a filename up front, but only hands back a Uri once the user
    // has actually picked a location, well after this composable has moved on), so it's held
    // here and written once that callback fires. Premium-gated (see the GEGEVENS row below), and
    // Voorraad-only — Boodschappenlijst export was dropped, it never grew import support either.
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
    // Built once via stringResource (a composable-only call) rather than inside the plain
    // lambdas below, which run outside composition — CsvExporter/CsvImporter are both
    // Compose-independent so they take these as plain parameters instead of resolving them
    // themselves. categoryKeyByLabel/unitKeyByLabel are the reverse lookups CsvImporter needs to
    // turn an imported file's localized column text back into a storage key.
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

    // CSV import (Voorraad) — every imported row becomes a brand-new product (synthetic
    // "csv-..." barcode, same convention AI-productherkenning uses for products with no real
    // barcode) rather than trying to match it against an existing one, since a CSV has no
    // barcode column to match on. restoreItem (not recordScan) writes the inventory row
    // directly without logging it to Geschiedenis — a bulk import shouldn't flood the activity
    // log with one entry per row.
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
        topBar = { HomeStockTopAppBar(title = { Text(stringResource(R.string.more_settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionHeader(stringResource(R.string.more_section_account))
            SettingsGroup(
                rows = listOf(
                    {
                        SettingsRow(
                            icon = Icons.Filled.AccountCircle,
                            title = stringResource(R.string.more_account_row_title),
                            subtitle = displayName,
                            onClick = { showProfileDialog = true },
                        )
                    },
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
                        SettingsRow(
                            icon = Icons.Filled.WorkspacePremium,
                            title = stringResource(R.string.more_premium_title),
                            subtitle = stringResource(if (isPremium) R.string.more_premium_active else R.string.more_premium_promo_subtitle),
                            trailingLabel = if (isPremium) null else stringResource(R.string.more_premium_upgrade_action),
                            onClick = onNavigateToPremium,
                        )
                    },
                ),
            )

            // Huishouden, Winkels and Statistieken all concern the shared household rather
            // than this device's own account or app preferences, so they get their own section
            // between Account and App-instellingen.
            SectionHeader(stringResource(R.string.more_section_household))
            SettingsGroup(
                rows = listOf(
                    {
                        SettingsRow(
                            icon = Icons.Filled.Home,
                            title = stringResource(R.string.more_household_title),
                            // Plural-aware: "1 lid" vs. "2 leden" (and the equivalent in every
                            // other locale) — a plain %d format string can't express that
                            // agreement on its own.
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
                ),
            )

            SectionHeader(stringResource(R.string.more_section_preferences))
            SettingsGroup(
                rows = listOf(
                    {
                        SettingsRow(
                            icon = Icons.Filled.Notifications,
                            title = stringResource(R.string.more_notifications_row_title),
                            subtitle = stringResource(if (notificationsEnabled) R.string.common_on else R.string.common_off),
                            onClick = { showNotificationsDialog = true },
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
                    {
                        SettingsRow(
                            icon = Icons.Filled.ImportExport,
                            title = stringResource(R.string.more_data_csv_title),
                            trailingLabel = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                            onClick = { if (isPremium) showImportExportDialog = true else onNavigateToPremium() },
                        )
                    },
                ),
            )

            SectionHeader(stringResource(R.string.more_section_about))
            SettingsGroup(
                rows = listOf(
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

            Text(
                text = stringResource(R.string.more_about_version_format, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
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

    if (showAccessibilityDialog) {
        AccessibilityDialog(
            largeText = largeText,
            onLargeTextChange = { themePreferences.setLargeText(it) },
            highContrast = highContrast,
            onHighContrastChange = { themePreferences.setHighContrast(it) },
            onDismiss = { showAccessibilityDialog = false },
        )
    }

    if (showNotificationsDialog) {
        NotificationsDialog(
            expiryEnabled = notificationsEnabled,
            onExpiryEnabledChange = ::setNotificationsEnabled,
            autoRestockEnabled = autoRestockEnabled,
            onAutoRestockEnabledChange = inventoryPreferences::setAutoRestockEnabled,
            onDismiss = { showNotificationsDialog = false },
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
 * Wraps a whole section's worth of [SettingsRow]s in one shared card with thin dividers
 * between them, instead of each row being its own separately-shadowed card — a long column of
 * near-identical floating cards read as visual noise the actual content (a handful of plain
 * settings) didn't warrant. [rows] takes a list of composable lambdas rather than a list of
 * plain data so each row can keep its own conditional subtitle/trailingLabel/onClick logic
 * exactly as before — this only changes how they're laid out, not what any of them show.
 */
@Composable
private fun SettingsGroup(rows: List<@Composable () -> Unit>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                row()
                if (index != rows.lastIndex) {
                    // Indented to align under the title/subtitle text, not under the icon —
                    // matches where AccountCard/PremiumPromoCard's own content starts.
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * Generic tappable settings row: icon, title, optional subtitle. Opens a dialog on tap.
 * [trailingLabel] is a short badge-like label at the far end of the row (e.g. "PREMIUM") —
 * separate from [subtitle] since a row only ever needs one or the other, not both stacked.
 *
 * No card of its own any more — see [SettingsGroup], which wraps a whole section's worth of
 * these in one shared card instead. The icon lost its colored circular badge for the same
 * reason: color on this screen is reserved for confirmed/active state (the Premium card, a
 * linked account) rather than repeated on every plain row regardless of what it does.
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

@Composable
private fun NotificationsDialog(
    expiryEnabled: Boolean,
    onExpiryEnabledChange: (Boolean) -> Unit,
    autoRestockEnabled: Boolean,
    onAutoRestockEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_notifications_row_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.more_expiry_notifications_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.more_expiry_notifications_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = expiryEnabled, onCheckedChange = onExpiryEnabledChange)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.more_auto_restock_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.more_auto_restock_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoRestockEnabled, onCheckedChange = onAutoRestockEnabledChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
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

/**
 * Single entry point for Voorraad CSV import/export — replaces what used to be two separate
 * export-only rows (Voorraad, Boodschappenlijst) with one row that opens this choice, per the
 * "Hernoem de optie naar Voorraad importeren/exporteren (CSV)" request. [onImport]/[onExport]
 * only kick off the picker flow — the actual file I/O runs in the caller's launchers.
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

// Privacy policy / open-source licenses now live on their own screens (see LegalScreens.kt) —
// long-form, read-only legal text reads better with a full page to scroll in than cramped into
// a dialog's fixed-height box.
