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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.ProfileEditDialog
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToRecipes: () -> Unit = {},
    onNavigateToReceiptScan: () -> Unit = {},
    onNavigateToAiRecognize: () -> Unit = {},
    onNavigateToMealPlan: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    onNavigateToHousehold: () -> Unit = {},
    onNavigateToAccountLink: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val notificationPreferences = application.container.notificationPreferences
    val notificationsEnabled by notificationPreferences.expiryNotificationsEnabled.collectAsState()
    val themePreferences = application.container.themePreferences
    val themeMode by themePreferences.themeMode.collectAsState()
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
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStoresDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

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
            SectionHeader(stringResource(R.string.more_section_account))
            AccountCard(
                displayName = displayName,
                photoPath = photoPath,
                onClick = { showProfileDialog = true },
            )
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
            SettingsRow(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.more_household_title),
                subtitle = stringResource(R.string.more_household_code_format, householdId ?: "—"),
                onClick = onNavigateToHousehold,
            )
            SettingsRow(
                icon = Icons.Filled.WorkspacePremium,
                title = stringResource(R.string.more_premium_title),
                subtitle = stringResource(if (isPremium) R.string.more_premium_active else R.string.more_premium_inactive),
                onClick = onNavigateToPremium,
            )

            SectionHeader(stringResource(R.string.more_section_features))
            SettingsRow(
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.more_statistics_title),
                subtitle = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                onClick = { if (isPremium) onNavigateToStatistics() else onNavigateToPremium() },
            )
            SettingsRow(
                icon = Icons.Filled.RestaurantMenu,
                title = stringResource(R.string.more_beta_recipes),
                subtitle = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                onClick = { if (isPremium) onNavigateToRecipes() else onNavigateToPremium() },
            )
            SettingsRow(
                icon = Icons.Filled.Receipt,
                title = stringResource(R.string.more_beta_receipt_scan),
                subtitle = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                onClick = { if (isPremium) onNavigateToReceiptScan() else onNavigateToPremium() },
            )
            // Premium-gated like its siblings above — unlike the on-device barcode scanner,
            // the photo actually leaves the device (to the recognizeProduct Cloud Function,
            // which calls Claude), so this carries a real per-scan cost the free tier
            // shouldn't be able to run up.
            SettingsRow(
                icon = Icons.Filled.AutoAwesome,
                title = stringResource(R.string.ai_recognize_title),
                subtitle = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                onClick = { if (isPremium) onNavigateToAiRecognize() else onNavigateToPremium() },
            )
            SettingsRow(
                icon = Icons.Filled.CalendarMonth,
                title = stringResource(R.string.meal_plan_title),
                subtitle = if (isPremium) null else stringResource(R.string.more_premium_locked_subtitle),
                onClick = { if (isPremium) onNavigateToMealPlan() else onNavigateToPremium() },
            )

            SectionHeader(stringResource(R.string.more_section_preferences))
            SettingsRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.more_theme_title),
                subtitle = stringResource(themeMode.labelRes()),
                onClick = { showThemeDialog = true },
            )
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.more_notifications_row_title),
                subtitle = stringResource(if (notificationsEnabled) R.string.common_on else R.string.common_off),
                onClick = { showNotificationsDialog = true },
            )
            SettingsRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.more_language_title),
                subtitle = stringResource(currentLanguage.labelRes),
                onClick = { showLanguageDialog = true },
            )
            SettingsRow(
                icon = Icons.Filled.Storefront,
                title = stringResource(R.string.more_stores_title),
                subtitle = stringResource(R.string.more_stores_count_format, stores.size),
                onClick = { showStoresDialog = true },
            )

            SectionHeader(stringResource(R.string.more_section_about))
            SettingsRow(
                icon = Icons.Filled.Feedback,
                title = stringResource(R.string.more_about_feedback),
                onClick = { showFeedbackDialog = true },
            )
            SettingsRow(
                icon = Icons.Filled.StarRate,
                title = stringResource(R.string.more_about_rate_app),
                onClick = { openPlayStoreListing(context) },
            )
            SettingsRow(
                icon = Icons.Filled.PrivacyTip,
                title = stringResource(R.string.more_about_privacy_policy),
                onClick = { showPrivacyPolicyDialog = true },
            )
            SettingsRow(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.more_about_licenses),
                onClick = { showLicensesDialog = true },
            )

            if (BuildConfig.DEBUG) {
                SectionHeader(stringResource(R.string.more_section_debug))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = SoftCardShape,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = SoftBadgeShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(stringResource(R.string.more_debug_premium_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.more_debug_premium_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = debugPremiumOverride,
                            onCheckedChange = billingRepository::setDebugPremiumOverride,
                        )
                    }
                }
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

    if (showNotificationsDialog) {
        NotificationsDialog(
            expiryEnabled = notificationsEnabled,
            onExpiryEnabledChange = ::setNotificationsEnabled,
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

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
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

@Composable
private fun AccountCard(displayName: String?, photoPath: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = SoftBadgeShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                if (photoPath != null) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(stringResource(R.string.more_account_row_title), style = MaterialTheme.typography.titleSmall)
                if (displayName != null) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Generic tappable settings row: icon, title, optional subtitle. Opens a dialog on tap. */
@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = SoftBadgeShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
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
        }
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
private fun NotificationsDialog(expiryEnabled: Boolean, onExpiryEnabledChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_notifications_row_title)) },
        text = {
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

@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Bundled as a plain-text asset rather than a string resource: it's long-form legal
    // prose, not UI chrome, and keeping it out of strings.xml avoids bloating that file
    // and awkward XML-escaping of a multi-paragraph document.
    val policyText = remember {
        context.assets.open("privacy_policy_nl.txt").bufferedReader().use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_about_privacy_policy)) },
        text = {
            Text(
                text = policyText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        },
    )
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

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_about_licenses)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                softwareLicenses.forEach { entry -> LicenseRow(entry) }
                HorizontalDivider()
                dataLicenses.forEach { entry -> LicenseRow(entry) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        },
    )
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
