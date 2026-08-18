package com.dtraas.homestock.ui.account

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountLinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val accountLinkRepository = application.container.accountLinkRepository
    val householdSession = application.container.householdSession
    val isLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)
    val coroutineScope = rememberCoroutineScope()

    var isLinking by remember { mutableStateOf(false) }
    var isUnlinking by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val collisionErrorMessage = stringResource(R.string.account_link_error_collision)
    val genericErrorMessage = stringResource(R.string.account_link_error_generic)
    val unlinkErrorMessage = stringResource(R.string.account_link_unlink_error)
    val recoverErrorMessage = stringResource(R.string.account_link_recover_error)
    val recoverNoneFoundMessage = stringResource(R.string.account_link_recover_none_found)

    // See AccountLinkRepository.switchToExistingGoogleAccount's doc — captured from the
    // collision so "Overstappen naar dat account" can retry linking with the exact same
    // credential the user just picked, instead of asking them to sign in with Google twice.
    var collidingIdToken by remember { mutableStateOf<String?>(null) }
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
        collidingIdToken = null
        isLinking = true
        coroutineScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val option = GetSignInWithGoogleOption
                    .Builder(context.getString(R.string.default_web_client_id))
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val response = credentialManager.getCredential(context, request)
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
                accountLinkRepository.linkWithGoogleIdToken(googleIdTokenCredential.idToken).onFailure { e ->
                    val friendly = if (e is FirebaseAuthUserCollisionException) collisionErrorMessage else genericErrorMessage
                    errorMessage = friendly.withDebugDetail(e)
                    if (e is FirebaseAuthUserCollisionException) collidingIdToken = googleIdTokenCredential.idToken
                }
            } catch (e: GetCredentialCancellationException) {
                // The user backed out of the account picker — a deliberate choice, not a
                // failure, so nothing is shown.
            } catch (e: GetCredentialException) {
                errorMessage = genericErrorMessage.withDebugDetail(e)
            } finally {
                isLinking = false
            }
        }
    }

    // See AccountLinkRepository.switchToExistingGoogleAccount/findMyHouseholds' docs — the
    // account-recovery path for exactly the collision above: switch this session to the
    // account the Google credential already belongs to, then look up which household(s) it's
    // a member of so the user can pick one to reopen on this device.
    fun switchToExistingAccount() {
        val idToken = collidingIdToken ?: return
        isRecovering = true
        errorMessage = null
        coroutineScope.launch {
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
            isRecovering = false
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
                title = { Text(stringResource(R.string.account_link_row_title)) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
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
                    isLinking = isLinking,
                    errorMessage = errorMessage,
                    canRecover = collidingIdToken != null && !isSwitchedAwaitingHouseholds,
                    isRecovering = isRecovering,
                    onSignInClick = ::startGoogleSignIn,
                    onRecoverClick = { showRecoverConfirm = true },
                )
            }
        }
    }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isUnlinking) showUnlinkConfirm = false },
            title = { Text(stringResource(R.string.account_link_unlink_dialog_title)) },
            text = { Text(stringResource(R.string.account_link_unlink_dialog_text)) },
            confirmButton = {
                TextButton(enabled = !isUnlinking, onClick = ::unlinkAccount) {
                    Text(stringResource(R.string.account_link_unlink_button))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isUnlinking,
                    onClick = { showUnlinkConfirm = false },
                ) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showRecoverConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isRecovering) showRecoverConfirm = false },
            title = { Text(stringResource(R.string.account_link_recover_dialog_title)) },
            text = { Text(stringResource(R.string.account_link_recover_dialog_text)) },
            confirmButton = {
                TextButton(
                    enabled = !isRecovering,
                    onClick = {
                        showRecoverConfirm = false
                        switchToExistingAccount()
                    },
                ) { Text(stringResource(R.string.account_link_recover_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !isRecovering, onClick = { showRecoverConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
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

@Composable
private fun UnlinkedState(
    isLinking: Boolean,
    errorMessage: String?,
    canRecover: Boolean,
    isRecovering: Boolean,
    onSignInClick: () -> Unit,
    onRecoverClick: () -> Unit,
) {
    Surface(
        shape = SoftBadgeShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(96.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
    }
    Text(
        text = stringResource(R.string.account_link_explanation),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 20.dp),
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
    Button(
        onClick = onSignInClick,
        enabled = !isLinking,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        if (isLinking) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.account_link_google_button))
        }
    }
    if (canRecover) {
        TextButton(
            onClick = onRecoverClick,
            enabled = !isRecovering,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (isRecovering) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.account_link_recover_button))
            }
        }
    }
}

@Composable
private fun LinkedState(email: String?, isUnlinking: Boolean, errorMessage: String?, onUnlinkClick: () -> Unit) {
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
