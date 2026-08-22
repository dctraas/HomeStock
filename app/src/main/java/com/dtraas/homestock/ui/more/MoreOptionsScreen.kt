package com.dtraas.homestock.ui.more

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.export.CsvExporter
import com.dtraas.homestock.data.export.CsvImporter
import com.dtraas.homestock.data.export.InventoryCsvHeaders
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Instellingen > "Data, toegankelijkheid & ondersteuning" — the catch-all the Claude Design
 * review's [MoreScreen] rewrite collapses everything rare behind: CSV import/export,
 * toegankelijkheid, account koppelen, feedback, app-beoordeling, en de juridische pagina's. None
 * of this logic is new — it's moved here verbatim from what used to be five separate dialogs and
 * rows directly on [MoreScreen], which is now reserved for the handful of things people actually
 * touch often (profile header, Premium, huishouden, meldingen, weergave, taal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsScreen(
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onNavigateToAccountLink: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val themePreferences = application.container.themePreferences
    val largeText by themePreferences.largeText.collectAsState()
    val highContrast by themePreferences.highContrast.collectAsState()
    val feedbackRepository = application.container.feedbackRepository
    val accountLinkRepository = application.container.accountLinkRepository
    val isAccountLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)
    val householdMembersRepository = application.container.householdMembersRepository
    val isPremium by householdMembersRepository.observeHouseholdIsPremium().collectAsState(initial = false)

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackSentMessage = stringResource(R.string.more_feedback_sent_confirmation)
    val feedbackErrorMessage = stringResource(R.string.more_feedback_error)

    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }

    // CSV export (Voorraad) — the CSV content has to be built *before* the system's "save to..."
    // picker is launched (it needs a filename up front, but only hands back a Uri once the user
    // has actually picked a location, well after this composable has moved on), so it's held
    // here and written once that callback fires.
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
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.more_data_accessibility_support_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
