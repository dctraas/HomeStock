package com.dtraas.homestock.ui.account

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.RecoverableHousehold
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.SheetPrimaryButton
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Instellingen > Account koppelen — upgrades this device's anonymous Firebase Auth session to
 * a Google-backed one via [androidx.credentials.CredentialManager] (the current recommended
 * Sign in with Google API, superseding the deprecated GoogleSignInClient). See
 * [com.dtraas.homestock.data.repository.AccountLinkRepository] for why this matters:
 * without it, uninstalling the app or switching devices loses access to the household.
 *
 * Requires `R.string.default_web_client_id`, which the google-services Gradle plugin only
 * generates once Google is enabled as a sign-in provider in the Firebase console and
 * `google-services.json` has been re-downloaded — this screen won't compile until then.
 */

/**
 * In debug builds, appends the real exception so a failure (e.g. a misconfigured OAuth client
 * or SHA fingerprint) can be diagnosed straight from the on-screen message instead of digging
 * through Logcat. Release builds only ever show [this] friendly message — an exception's raw
 * type/message isn't something to surface to a real user.
 */
private fun String.withDebugDetail(cause: Throwable): String =
    if (BuildConfig.DEBUG) "$this\n\n[debug] ${cause::class.simpleName}: ${cause.message}" else this

/** Runs Credential Manager's Google sign-in picker and returns the resulting id token — null if
 *  the user backed out of the picker (a deliberate choice, not a failure); anything else propagates
 *  as a [GetCredentialException] for the caller to catch and show its own error copy for. Shared
 *  by [AccountLinkScreen]'s two sign-in entry points — linking a *new* Google account to this
 *  session, and finding an *existing* Google-linked account's households — same picker, two
 *  different things done with the token it returns. */
private suspend fun getGoogleIdToken(context: Context): String? {
    val credentialManager = CredentialManager.create(context)
    val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val response = credentialManager.getCredential(context, request)
        GoogleIdTokenCredential.createFrom(response.credential.data).idToken
    } catch (e: GetCredentialCancellationException) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountLinkScreen(onBack: () -> Unit, onNavigateToPrivacyPolicy: () -> Unit = {}) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val accountLinkRepository = application.container.accountLinkRepository
    val householdSession = application.container.householdSession
    val householdRepository = application.container.householdRepository
    val isLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)
    val householdName by householdRepository.observeHouseholdName().collectAsState(initial = null)
    val householdCreatedAt by householdRepository.observeHouseholdCreatedAt().collectAsState(initial = null)
    val historyMonths = householdCreatedAt?.let { created ->
        val elapsedDays = (System.currentTimeMillis() - created).coerceAtLeast(0L) / TimeUnit.DAYS.toMillis(1)
        (elapsedDays / 30L).toInt().coerceAtLeast(1)
    }
    val coroutineScope = rememberCoroutineScope()

    var isLinking by remember { mutableStateOf(false) }
    var isUnlinking by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val genericErrorMessage = stringResource(R.string.account_link_error_generic)
    val unlinkErrorMessage = stringResource(R.string.account_link_unlink_error)
    val recoverErrorMessage = stringResource(R.string.account_link_recover_error)
    val recoverNoneFoundMessage = stringResource(R.string.account_link_recover_none_found)

    // The token behind the confirm dialog below — set either by a link attempt that collided
    // with an already-linked Google account (see startGoogleSignIn), or by the always-available
    // "Ik had al een HomeStock-account" row (see findExistingAccount) running the exact same
    // Google picker on its own. Either way, switching sessions is real enough (this device loses
    // its current anonymous household's access) to confirm first rather than act on it straight
    // from the picker result.
    var pendingSwitchIdToken by remember { mutableStateOf<String?>(null) }
    var showRecoverConfirm by remember { mutableStateOf(false) }
    var isRecovering by remember { mutableStateOf(false) }
    var recoverableHouseholds by remember { mutableStateOf<List<RecoverableHousehold>?>(null) }
    var isSwitchedAwaitingHouseholds by remember { mutableStateOf(false) }

    fun unlinkAccount() {
        isUnlinking = true
        coroutineScope.launch {
            accountLinkRepository.unlinkGoogleAccount().onFailure { e ->
                errorMessage = unlinkErrorMessage.withDebugDetail(e)
            }
            isUnlinking = false
            showUnlinkConfirm = false
        }
    }

    fun startGoogleSignIn() {
        errorMessage = null
        isLinking = true
        coroutineScope.launch {
            try {
                val idToken = getGoogleIdToken(context)
                if (idToken == null) {
                    isLinking = false
                    return@launch
                }
                accountLinkRepository.linkWithGoogleIdToken(idToken).onFailure { e ->
                    if (e is FirebaseAuthUserCollisionException) {
                        // Already linked to a *different* household elsewhere — rather than a
                        // dead-end error, this is exactly what the confirm dialog below (also
                        // used by findExistingAccount) is for, so open it straight away with the
                        // token already at hand instead of making the user tap "Ik had al een
                        // account" and pick the exact same Google account a second time.
                        pendingSwitchIdToken = idToken
                        showRecoverConfirm = true
                    } else {
                        errorMessage = genericErrorMessage.withDebugDetail(e)
                    }
                }
            } catch (e: GetCredentialException) {
                errorMessage = genericErrorMessage.withDebugDetail(e)
            } finally {
                isLinking = false
            }
        }
    }

    // See AccountLinkRepository.switchToExistingGoogleAccount/findMyHouseholds' docs — switches
    // this session to the account [idToken] belongs to, then looks up which household(s) it's a
    // member of so the user can pick one to reopen on this device.
    suspend fun switchAndFindHouseholds(idToken: String) {
        errorMessage = null
        accountLinkRepository.switchToExistingGoogleAccount(idToken)
            .onSuccess {
                isSwitchedAwaitingHouseholds = true
                accountLinkRepository.findMyHouseholds()
                    .onSuccess { households ->
                        if (households.isEmpty()) {
                            errorMessage = recoverNoneFoundMessage
                        } else {
                            recoverableHouseholds = households
                        }
                    }
                    .onFailure { e -> errorMessage = recoverErrorMessage.withDebugDetail(e) }
            }
            .onFailure { e -> errorMessage = recoverErrorMessage.withDebugDetail(e) }
    }

    /** The "Ik had al een HomeStock-account" row's own entry point — an independent Google
     *  sign-in (not gated behind a failed link attempt the way this dialog used to be) for
     *  someone who already knows they have an account, rather than only discovering the option
     *  after [startGoogleSignIn] happens to collide. */
    fun findExistingAccount() {
        errorMessage = null
        isRecovering = true
        coroutineScope.launch {
            try {
                val idToken = getGoogleIdToken(context)
                if (idToken != null) {
                    pendingSwitchIdToken = idToken
                    showRecoverConfirm = true
                }
            } catch (e: GetCredentialException) {
                errorMessage = genericErrorMessage.withDebugDetail(e)
            } finally {
                isRecovering = false
            }
        }
    }

    fun retryFindHouseholds() {
        isRecovering = true
        errorMessage = null
        coroutineScope.launch {
            accountLinkRepository.findMyHouseholds()
                .onSuccess { households ->
                    if (households.isEmpty()) {
                        errorMessage = recoverNoneFoundMessage
                    } else {
                        recoverableHouseholds = households
                    }
                }
                .onFailure { e -> errorMessage = recoverErrorMessage.withDebugDetail(e) }
            isRecovering = false
        }
    }

    fun selectRecoveredHousehold(household: RecoverableHousehold) {
        // Deliberately doesn't call HouseholdMembersRepository.registerCurrentDevice: this uid
        // is already a member of [household] (that's how findMyHouseholds found it in the
        // first place), and that call fully overwrites the member doc rather than merging,
        // which would wipe fields like isPremium/photoUrl/excludedAllergens that aren't in its
        // write. Flipping the session's householdId is enough — HouseholdMembersRepository's
        // reactive syncPremiumStatus/syncDisplayName/syncCurrentDevicePhoto observers (all
        // merge writes) pick it up and keep it current from here.
        householdSession.rememberHousehold(household.id, household.name)
        householdSession.setHousehold(household.id)
        onBack()
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.account_link_row_title))
                        if (!isLinked) {
                            Text(
                                text = stringResource(R.string.account_link_header_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
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
                // See PremiumScreen for why this needs a scroll escape hatch: icon + title +
                // explanation + error text + button doesn't reliably fit with large text or on
                // a small device.
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isLinked) {
                LinkedState(
                    email = accountLinkRepository.linkedEmail,
                    isUnlinking = isUnlinking,
                    errorMessage = errorMessage,
                    onUnlinkClick = { showUnlinkConfirm = true },
                )
            } else {
                UnlinkedState(
                    householdName = householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_default_name),
                    historyMonths = historyMonths,
                    isLinking = isLinking,
                    isRecovering = isRecovering,
                    errorMessage = errorMessage,
                    onSignInClick = ::startGoogleSignIn,
                    onFindExistingClick = ::findExistingAccount,
                    onPrivacyPolicyClick = onNavigateToPrivacyPolicy,
                )
                LinkedDeviceFooter()
            }
        }
    }

    if (showUnlinkConfirm) {
        AccountUnlinkSheet(
            isUnlinking = isUnlinking,
            onConfirm = ::unlinkAccount,
            onDismiss = { showUnlinkConfirm = false },
        )
    }

    if (showRecoverConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isRecovering) { showRecoverConfirm = false; pendingSwitchIdToken = null } },
            title = { Text(stringResource(R.string.account_link_recover_dialog_title)) },
            text = { Text(stringResource(R.string.account_link_recover_dialog_text)) },
            confirmButton = {
                TextButton(
                    enabled = !isRecovering,
                    onClick = {
                        showRecoverConfirm = false
                        val token = pendingSwitchIdToken ?: return@TextButton
                        isRecovering = true
                        coroutineScope.launch {
                            switchAndFindHouseholds(token)
                            isRecovering = false
                        }
                    },
                ) { Text(stringResource(R.string.account_link_recover_confirm)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRecovering,
                    onClick = { showRecoverConfirm = false; pendingSwitchIdToken = null },
                ) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    recoverableHouseholds?.let { households ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.account_link_recover_picker_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.account_link_recover_picker_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    households.forEach { household ->
                        RecoverableHouseholdRow(household = household, onClick = { selectRecoveredHousehold(household) })
                    }
                }
            },
            confirmButton = {},
        )
    }

    // Switched accounts but findMyHouseholds came back empty or failed and the user hasn't
    // dismissed the error yet — offers a retry rather than stranding them mid-flow, since the
    // account switch itself already succeeded at this point.
    if (isSwitchedAwaitingHouseholds && recoverableHouseholds == null && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null; isSwitchedAwaitingHouseholds = false },
            title = { Text(stringResource(R.string.account_link_recover_dialog_title)) },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(enabled = !isRecovering, onClick = ::retryFindHouseholds) {
                    if (isRecovering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.account_link_recover_retry))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { errorMessage = null; isSwitchedAwaitingHouseholds = false },
                ) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun RecoverableHouseholdRow(household: RecoverableHousehold, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Text(
            text = household.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * The "Niet gekoppeld" status card — a coral status header, then three concrete reasons to link
 * (a new device, a reinstall, and this device breaking) rather than the single paragraph this
 * used to be, followed by the primary Google button and the always-available "Ik had al een
 * account" row (used to only appear after a collision — see [AccountLinkScreen]'s own doc for why
 * it's independent now).
 */
@Composable
private fun UnlinkedState(
    householdName: String,
    historyMonths: Int?,
    isLinking: Boolean,
    isRecovering: Boolean,
    errorMessage: String?,
    onSignInClick: () -> Unit,
    onFindExistingClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.account_link_status_unlinked),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            HorizontalDivider()
            AccountLinkBenefitRow(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.account_link_benefit_device_title),
                subtitle = stringResource(R.string.account_link_benefit_device_subtitle),
            )
            AccountLinkBenefitRow(
                icon = Icons.Filled.History,
                title = stringResource(R.string.account_link_benefit_reinstall_title),
                subtitle = if (historyMonths != null) {
                    stringResource(R.string.account_link_benefit_reinstall_subtitle_with_history_format, historyMonths)
                } else {
                    stringResource(R.string.account_link_benefit_reinstall_subtitle)
                },
            )
            AccountLinkBenefitRow(
                icon = Icons.Filled.Groups,
                title = stringResource(R.string.account_link_benefit_access_title_format, householdName),
                subtitle = stringResource(R.string.account_link_benefit_access_subtitle),
            )
        }
    }

    if (errorMessage != null) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Button(
        onClick = onSignInClick,
        enabled = !isLinking && !isRecovering,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLinking) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        } else {
            // No actual Google "G" glyph in Material Icons (extended or otherwise — that's a
            // trademarked logo, not a generic symbol) to reach for here, so a plain white "G"
            // badge stands in for it instead of an icon that doesn't exist.
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "G",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inverseSurface,
                    )
                }
            }
            Text(stringResource(R.string.account_link_google_button), modifier = Modifier.padding(start = 10.dp))
        }
    }

    Surface(
        onClick = onFindExistingClick,
        enabled = !isLinking && !isRecovering,
        shape = SoftCardShapeCompact,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.account_link_find_existing_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.account_link_find_existing_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRecovering) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.account_link_privacy_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onPrivacyPolicyClick, modifier = Modifier.padding(top = 2.dp)) {
            Text(stringResource(R.string.more_about_privacy_policy), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AccountLinkBenefitRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A quiet reference row at the very bottom, always echoing this *device's* own current link
 * state — only shown while unlinked (see [AccountLinkScreen]) since the card above already
 * covers that ground prominently once actually linked. "Ontkoppelen" here has nothing to do yet,
 * so it's shown greyed out rather than as a working control.
 */
@Composable
private fun LinkedDeviceFooter() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.account_link_footer_eyebrow),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_link_footer_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.account_link_unlink_button),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun LinkedState(email: String?, isUnlinking: Boolean, errorMessage: String?, onUnlinkClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Surface(
            shape = SoftBadgeShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.account_link_linked_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.account_link_linked_subtitle_format, email ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        TextButton(onClick = onUnlinkClick, enabled = !isUnlinking, modifier = Modifier.padding(top = 20.dp)) {
            if (isUnlinking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.account_link_unlink_button), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * "Account ontkoppelen" — same destructive-sheet treatment as [com.dtraas.homestock.ui.household]'s
 * delete-household sheet (2026-08 dialog review), minus the type-to-confirm: there's no household
 * data actually being deleted here, just this device's recovery path, so a real count-of-what's-
 * lost card would have nothing concrete to show — the body text already states the actual risk.
 * No dismiss button either (cross-cutting rule #2) — the sheet's own drag/scrim cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountUnlinkSheet(isUnlinking: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    HomeStockBottomSheet(onDismissRequest = { if (!isUnlinking) onDismiss() }) {
        Column(
            modifier = Modifier.padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            SheetTitle(
                title = stringResource(R.string.account_link_unlink_dialog_title),
                subtitle = stringResource(R.string.account_link_unlink_dialog_text),
            )
            SheetPrimaryButton(
                text = stringResource(R.string.account_link_unlink_button),
                onClick = onConfirm,
                enabled = !isUnlinking,
                loading = isUnlinking,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        }
    }
}
