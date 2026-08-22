package com.dtraas.homestock.ui.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.TopAppBarContainerGradientEnd
import com.dtraas.homestock.work.ExpiryCheckWorker
import java.io.File
import kotlinx.coroutines.launch

private enum class AppLanguage(val tag: String, val labelRes: Int) {
    NL("nl", R.string.more_language_option_nl),
    EN("en", R.string.more_language_option_en),
    DE("de", R.string.more_language_option_de),
    FR("fr", R.string.more_language_option_fr),
    ES("es", R.string.more_language_option_es),
}

/**
 * Instellingen — per the Claude Design review, cut down to a profile header, the Premium card,
 * and exactly two settings groups (HUISHOUDEN / APP); everything used rarely (CSV,
 * toegankelijkheid, account koppelen, feedback, beoordelen, privacy, licenties) now lives one
 * tap away on [MoreOptionsScreen] instead of cluttering this screen directly. That relocation is
 * purely a move — none of that logic changed, see MoreOptionsScreen.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    onNavigateToHousehold: () -> Unit = {},
    onNavigateToMoreOptions: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val notificationPreferences = application.container.notificationPreferences
    val notificationsEnabled by notificationPreferences.expiryNotificationsEnabled.collectAsState()
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
    val currentLanguage = AppLanguage.entries.find { it.tag == LocalConfiguration.current.locales[0].language } ?: AppLanguage.NL

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showProfileDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStoresDialog by remember { mutableStateOf(false) }

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
            ProfileHeaderBlock(
                displayName = displayName,
                photoPath = photoPath,
                householdName = householdName,
                memberCount = memberCount,
                householdCode = householdId,
                onClick = { showProfileDialog = true },
            )

            PremiumCard(
                isPremium = isPremium,
                onClick = onNavigateToPremium,
            )

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
                            icon = Icons.Filled.ShoppingCart,
                            title = stringResource(R.string.more_auto_restock_title),
                            subtitle = stringResource(R.string.more_auto_restock_subtitle),
                            checked = autoRestockEnabled,
                            onCheckedChange = inventoryPreferences::setAutoRestockEnabled,
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
                            icon = Icons.Filled.Language,
                            title = stringResource(R.string.more_language_title),
                            subtitle = stringResource(currentLanguage.labelRes),
                            onClick = { showLanguageDialog = true },
                        )
                    },
                ),
            )

            SettingsGroup(
                rows = listOf(
                    {
                        SettingsRow(
                            icon = Icons.Filled.MoreHoriz,
                            title = stringResource(R.string.more_data_accessibility_support_title),
                            onClick = onNavigateToMoreOptions,
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
}

/** First letters of up to the first two words of [name], uppercased — "Jip de Vries" -> "JD".
 *  Falls back to an empty string for a blank/empty [name] (callers show an icon instead). */
private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

/**
 * The screen's own entry point for [ProfileEditDialog] — a 56dp squircle avatar (photo, or this
 * device's initials), the device's own name, and a subtitle combining the household's name,
 * member count and join code, so the one thing every household member sets up early (their name)
 * and the household they're in are both visible without opening anything. Styled as its own card
 * (unlike the design reference's full-bleed gradient header) since [HomeStockTopAppBar] is a
 * fixed-height Material app bar shared by every screen — expanding it into a tall custom header
 * just for this screen would be a bigger structural change than a per-screen layout pass
 * warrants; a prominent card at the top of the scrolling content preserves the same "this is the
 * header" hierarchy without that.
 */
@Composable
private fun ProfileHeaderBlock(
    displayName: String?,
    photoPath: String?,
    householdName: String?,
    memberCount: Int,
    householdCode: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val trimmedName = displayName?.trim().takeUnless { it.isNullOrEmpty() }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
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
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.more_header_subtitle_format,
                        memberCount,
                        householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_default_name),
                        memberCount,
                        householdCode ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun PremiumCard(isPremium: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
 * Wraps a whole section's worth of [SettingsRow]s in one shared card with thin dividers
 * between them, instead of each row being its own separately-shadowed card. [rows] takes a list
 * of composable lambdas rather than a list of plain data so each row can keep its own
 * conditional subtitle/trailingLabel/onClick logic exactly as before — this only changes how
 * they're laid out, not what any of them show. Not private — [MoreOptionsScreen] reuses it for
 * the settings it now hosts, so the two screens read as one visual language rather than two.
 */
@Composable
internal fun SettingsGroup(rows: List<@Composable () -> Unit>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                row()
                if (index != rows.lastIndex) {
                    // Indented to align under the title/subtitle text, not under the icon.
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
 * Generic tappable settings row: icon, title, optional subtitle. Opens a dialog (or navigates)
 * on tap. [trailingLabel] is a short badge-like label at the far end of the row (e.g. "PREMIUM").
 * Not private — see [SettingsGroup].
 */
@Composable
internal fun SettingsRow(
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
 * [Switch] instead of a click-through, since flipping it is the entire action. Kept directly on
 * this screen (unlike the rarely-used rows moved to [MoreOptionsScreen]) since burying a
 * developer-only debug affordance behind an extra tap would make testing premium-locked flows
 * more annoying, not less — a deliberate exception to "everything rare collapses away".
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
