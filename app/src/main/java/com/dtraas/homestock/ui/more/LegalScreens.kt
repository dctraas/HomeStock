package com.dtraas.homestock.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.components.HomeStockTopAppBar

/**
 * Instellingen > Over/Ondersteuning > Privacybeleid — used to be an [androidx.compose.material3.AlertDialog]
 * with a fixed-height scrollable box; long-form legal prose reads better with a real page to
 * scroll in, so this is a full screen instead. See [LicensesScreen] for the same reasoning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Bundled as a plain-text asset rather than a string resource: it's long-form legal
    // prose, not UI chrome, and keeping it out of strings.xml avoids bloating that file
    // and awkward XML-escaping of a multi-paragraph document.
    val policyText = remember {
        context.assets.open("privacy_policy_nl.txt").bufferedReader().use { it.readText() }
    }
    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.more_about_privacy_policy)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Text(
            text = policyText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

private data class LicenseEntry(val name: String, val license: String)

// License names are intentionally left untranslated, matching how every OSS-licenses
// screen (including Android's own Settings > About > Legal information) shows them —
// these are the licenses' authoritative names, not app copy.
private val softwareLicenses = listOf(
    LicenseEntry("Kotlin & kotlinx.coroutines (JetBrains)", "Apache License 2.0"),
    LicenseEntry("AndroidX Jetpack (Core, Lifecycle, Activity, Compose, Navigation, CameraX, WorkManager, Glance, AppCompat)", "Apache License 2.0"),
    LicenseEntry("Material Components & Material Icons", "Apache License 2.0"),
    LicenseEntry("Retrofit, OkHttp & Gson", "Apache License 2.0"),
    LicenseEntry("Coil", "Apache License 2.0"),
    LicenseEntry("Guava", "Apache License 2.0"),
    LicenseEntry("Google ML Kit (Barcode Scanning)", "Google APIs Terms of Service"),
    LicenseEntry("Firebase SDK (Authentication, Firestore)", "Google APIs Terms of Service"),
    LicenseEntry("Baloo 2 & Nunito (Google Fonts)", "SIL Open Font License 1.1"),
)

private val dataLicenses = listOf(
    LicenseEntry("Open Food Facts", "Open Database License (ODbL)"),
)

/** Instellingen > Over/Ondersteuning > Licenties — see [PrivacyPolicyScreen]'s doc for why this moved off a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.more_about_licenses)) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            softwareLicenses.forEach { entry -> LicenseRow(entry) }
            HorizontalDivider()
            dataLicenses.forEach { entry -> LicenseRow(entry) }
        }
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry) {
    Column {
        Text(entry.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = entry.license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
