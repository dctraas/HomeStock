package com.dtraas.homestock.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftCardShape
import kotlinx.coroutines.launch

/** One top-level numbered section of the privacy policy, parsed at runtime from
 *  [parsePolicySections] — see that function's doc for why this stays a runtime parse of the
 *  bundled `.txt` asset instead of a second, hand-duplicated copy of the same content. */
private data class PolicySection(val number: Int, val title: String, val body: String)

/**
 * Splits the bundled policy text into its 9 top-level numbered sections ("1. WELKE GEGEVENS
 * VERZAMELEN WE" … "9. CONTACT") so each can render as its own card — without ever touching or
 * re-typing the actual legal prose itself, which stays exactly what's in
 * `assets/privacy_policy_nl.txt`. Matches only a *bare* "N. TITLE IN CAPS" line, not a numbered
 * sub-section like "1.4 Gegevens die alleen lokaal..." (that second number right after the dot,
 * with no space, fails the `\s+` right after `\d+\.` — those subsections stay exactly where they
 * were, as ordinary prose inside their parent section's [body]).
 */
private fun parsePolicySections(rawText: String): List<PolicySection> {
    val headerRegex = Regex("""^(\d+)\.\s+([A-ZÀ-Ÿ ]+)$""", RegexOption.MULTILINE)
    val matches = headerRegex.findAll(rawText).toList()
    return matches.mapIndexed { index, match ->
        val number = match.groupValues[1].toInt()
        val title = match.groupValues[2].trim()
        val bodyStart = match.range.last + 1
        val bodyEnd = if (index + 1 < matches.size) matches[index + 1].range.first else rawText.length
        PolicySection(number, title, rawText.substring(bodyStart, bodyEnd).trim())
    }
}

/** Sentence-cases a section's ALL-CAPS source title ("WELKE GEGEVENS VERZAMELEN WE" ->
 *  "Welke gegevens verzamelen we") purely for the card heading's display — a presentational
 *  transform of the already-parsed title, not a rewrite of the source document, which keeps its
 *  own capitalization untouched in `assets/privacy_policy_nl.txt`. */
private fun String.toSentenceCase(): String =
    lowercase().replaceFirstChar { it.uppercase() }

/** Short nav-chip labels — distinct from each section's own (longer) heading, same idea as the
 *  Claude Design mockup's own short chip words. Purely a navigation aid; the section headings
 *  and bodies below stay exactly as parsed from the source document. */
private val chipLabelsBySection = mapOf(
    1 to R.string.privacy_chip_data,
    3 to R.string.privacy_chip_sharing,
    4 to R.string.privacy_chip_retention,
    6 to R.string.privacy_chip_rights,
    9 to R.string.privacy_chip_contact,
)

// A little over 200 words/minute — this is a legal document read carefully, not skimmed.
private const val WORDS_PER_MINUTE = 180

/**
 * Instellingen > Over/Ondersteuning > Privacybeleid — used to be an [androidx.compose.material3.AlertDialog]
 * with a fixed-height scrollable box; long-form legal prose reads better with a real page to
 * scroll in, so this is a full screen instead. See [LicensesScreen] for the same reasoning.
 *
 * Per the design review, this now opens with a "Kort gezegd" summary card (a genuine, short
 * paraphrase — not a replacement for the real sections underneath, which is the actual policy,
 * unchanged and unabridged) and a row of nav chips that jump straight to a section. Every one of
 * [parsePolicySections]'s 9 sections still renders in full below, in order, exactly as bundled.
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
    val sections = remember(policyText) { parsePolicySections(policyText) }
    val wordCount = remember(policyText) { policyText.split(Regex("\\s+")).count { it.isNotBlank() } }
    val readingMinutes = (wordCount / WORDS_PER_MINUTE).coerceAtLeast(1)
    // Parsed from the document's own "Laatst bijgewerkt: DD-MM-YYYY" line rather than a second,
    // hand-maintained copy of the same date in strings.xml — editing the .txt is then the only
    // place this ever needs updating.
    val lastUpdatedLabel = remember(policyText) {
        val raw = Regex("""Laatst bijgewerkt: (\d{2})-(\d{2})-(\d{4})""").find(policyText)?.destructured
        raw?.let { (day, month, year) ->
            val parsed = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("nl")).parse("$day-$month-$year")
            parsed?.let { java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("nl")).format(it) }
        }
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.more_about_privacy_policy))
                        if (lastUpdatedLabel != null) {
                            Text(
                                text = stringResource(R.string.privacy_last_updated_format, lastUpdatedLabel, readingMinutes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "summary") { PolicySummaryCard() }
            item(key = "chips") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { section ->
                        val labelRes = chipLabelsBySection[section.number] ?: return@forEach
                        val targetIndex = sections.indexOf(section) + 2 // + summary card + chip row
                        FilterChip(
                            selected = false,
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(targetIndex) } },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }
            items(sections, key = { it.number }) { section -> PolicySectionCard(section) }
        }
    }
}

/**
 * "Kort gezegd" — a short, genuine paraphrase of what's below (what's stored, what isn't, and
 * where), not a substitute for it. Every claim here is backed by a real section of the actual
 * policy text (opslaan -> section 1, niet doen -> sections 1.10/3, waar -> sections 3.1/4) and by
 * real app capabilities (export/verwijderen — see HouseholdSettingsScreen's Data-overzetten and
 * Huishouden verwijderen).
 */
@Composable
private fun PolicySummaryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.privacy_summary_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.outline,
            )
            SummaryLine(
                icon = Icons.Filled.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                boldLead = stringResource(R.string.privacy_summary_stores_lead),
                text = stringResource(R.string.privacy_summary_stores_text),
            )
            SummaryLine(
                icon = Icons.Filled.Cancel,
                iconTint = MaterialTheme.colorScheme.error,
                boldLead = stringResource(R.string.privacy_summary_never_lead),
                text = stringResource(R.string.privacy_summary_never_text),
            )
            SummaryLine(
                icon = Icons.Filled.LocationOn,
                iconTint = MaterialTheme.colorScheme.secondary,
                boldLead = stringResource(R.string.privacy_summary_where_lead),
                text = stringResource(R.string.privacy_summary_where_text),
            )
        }
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, iconTint: Color, boldLead: String, text: String) {
    Row {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = boldLead,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PolicySectionCard(section: PolicySection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${section.number}. ${section.title.toSentenceCase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = section.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    LicenseEntry("Firebase SDK (Authentication, Firestore, Storage, Functions, Analytics, Remote Config, Cloud Messaging, App Check)", "Google APIs Terms of Service"),
    LicenseEntry("Google Play Billing Library", "Google APIs Terms of Service"),
    LicenseEntry("Credential Manager & Google Sign-In (AndroidX Credentials, Google Identity)", "Apache License 2.0"),
    LicenseEntry("Baloo 2 & Nunito (Google Fonts)", "SIL Open Font License 1.1"),
)

private val dataLicenses = listOf(
    LicenseEntry("Open Food Facts", "Open Database License (ODbL)"),
    LicenseEntry("Spoonacular", "Spoonacular API Terms of Use"),
)

/** Where to send someone who taps a license group's "Licentietekst lezen" row — the canonical,
 *  always-current text lives at the license's own URL; bundling (and needing to keep in sync) a
 *  second, offline copy of legal text this app didn't author isn't worth the drift risk for a
 *  screen that's never the only way to reach it. */
private val licenseTextUrls = mapOf(
    "Apache License 2.0" to "https://www.apache.org/licenses/LICENSE-2.0",
    "Google APIs Terms of Service" to "https://developers.google.com/terms",
    "SIL Open Font License 1.1" to "https://scripts.sil.org/OFL",
)

private val dataSourceUrls = mapOf(
    "Open Food Facts" to "https://world.openfoodfacts.org",
    "Spoonacular" to "https://spoonacular.com",
)

/**
 * Instellingen > Over/Ondersteuning > Licenties — see [PrivacyPolicyScreen]'s doc for why this
 * moved off a dialog. Per the design review, grouped into one collapsible card per license type
 * (was a flat list) with a "Licentietekst lezen" link to that license's own canonical text, plus
 * a separate "Databronnen" section for the two APIs this app's *content* (not code) comes from.
 * Every entry from the original flat [softwareLicenses]/[dataLicenses] lists is still here, in
 * full, none dropped — only how they're grouped and presented changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val grouped = remember { softwareLicenses.groupBy { it.license } }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.more_about_licenses))
                        Text(
                            text = stringResource(R.string.licenses_header_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            grouped.forEach { (license, entries) ->
                LicenseGroupCard(
                    license = license,
                    entries = entries,
                    onReadLicenseText = {
                        licenseTextUrls[license]?.let { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }

            Text(
                text = stringResource(R.string.licenses_data_sources_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = SoftCardShape,
            ) {
                Column {
                    dataLicenses.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        DataSourceRow(
                            entry = entry,
                            onClick = {
                                dataSourceUrls[entry.name]?.let { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.licenses_thanks_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/** One license-type group ("Apache License 2.0", "Google APIs Terms of Service", "SIL Open Font
 *  License 1.1") — collapsed by default showing just how many entries it covers, expanding to
 *  the full list (every name exactly as in [softwareLicenses], nothing shortened) plus the link
 *  to that license's own text. */
@Composable
private fun LicenseGroupCard(license: String, entries: List<LicenseEntry>, onReadLicenseText: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    LicenseGroupCountBadge(entries.size)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(text = license, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    if (!expanded) {
                        Text(
                            text = pluralStringResourceCompat(entries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    entries.forEach { entry ->
                        Text(text = entry.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onReadLicenseText).padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.licenses_read_text_action),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseGroupCountBadge(count: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// Simple invariant "N onderdelen" (Dutch doesn't inflect this the way English "1 part"/"2 parts"
// does) — matches the plural-that-doesn't-actually-change pattern used elsewhere in this app's
// own strings.xml rather than reaching for a full <plurals> resource for one static word.
@Composable
private fun pluralStringResourceCompat(count: Int): String =
    stringResource(R.string.licenses_group_count_format, count)

@Composable
private fun DataSourceRow(entry: LicenseEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(text = entry.license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.licenses_open_link_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
