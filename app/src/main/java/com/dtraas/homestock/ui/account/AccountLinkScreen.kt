package com.dtraas.homestock.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
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
    val isLinked by accountLinkRepository.observeIsLinked().collectAsState(initial = accountLinkRepository.linkedEmail != null)
    val coroutineScope = rememberCoroutineScope()

    var isLinking by remember { mutableStateOf(false) }
    var isUnlinking by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val collisionErrorMessage = stringResource(R.string.account_link_error_collision)
    val genericErrorMessage = stringResource(R.string.account_link_error_generic)
    val unlinkErrorMessage = stringResource(R.string.account_link_unlink_error)

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
                    onSignInClick = ::startGoogleSignIn,
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
}

@Composable
private fun UnlinkedState(isLinking: Boolean, errorMessage: String?, onSignInClick: () -> Unit) {
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
