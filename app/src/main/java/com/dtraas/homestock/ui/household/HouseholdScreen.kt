package com.dtraas.homestock.ui.household

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.DeviceProfile
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.ui.theme.SoftCardShape
import java.io.File
import kotlinx.coroutines.launch

/**
 * Shown before the main app whenever this device isn't part of a household yet.
 * A household is the sharing boundary: every device that creates or joins the
 * same household code sees the same inventory, shopping list and activity log.
 *
 * [prefillJoinCode] comes from a homestock://join?code=XXXXXX link (see MainActivity /
 * HouseholdInviteLink) — when set, the join step opens pre-filled with that code instead of
 * the usual create-or-join choice, right after the one-time profile step if this is a fresh
 * install.
 */
@Composable
fun HouseholdScreen(prefillJoinCode: String? = null) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val deviceProfile = application.container.deviceProfile
    // Keyed off onboardingGeneration so leaving/deleting a household forces a brand new
    // HouseholdViewModel instead of Android handing back the previous, stale one — see
    // HouseholdSession.onboardingGeneration's doc for why that would otherwise happen.
    val onboardingGeneration by application.container.householdSession.onboardingGeneration.collectAsState()
    val viewModel: HouseholdViewModel = viewModel(
        key = "household_onboarding_$onboardingGeneration",
        factory = viewModelFactory {
            initializer {
                HouseholdViewModel(
                    householdRepository = application.container.householdRepository,
                    householdSession = application.container.householdSession,
                    householdMembersRepository = application.container.householdMembersRepository,
                    deviceProfile = deviceProfile,
                    prefillJoinCode = prefillJoinCode,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val codeCopiedMessage = stringResource(R.string.household_code_copied_snackbar)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Several steps here (profile photo, household name, the created-code card)
                // can add up to more than one screen's worth of content — especially with
                // "Groot lettertype" (see more_accessibility_large_text) or on a small device
                // — so this needs a scroll escape hatch instead of clipping the "Doorgaan"
                // button that gates entry to the rest of the app.
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.mode) {
                HouseholdMode.PROFILE -> ProfileSetupContent(
                    deviceProfile = deviceProfile,
                    onContinue = viewModel::confirmProfile,
                )
                HouseholdMode.CHOOSE -> ChooseContent(
                    onCreate = viewModel::selectCreate,
                    onJoin = viewModel::selectJoin,
                )
                HouseholdMode.CREATE -> if (!uiState.hasSubmittedHouseholdName) {
                    HouseholdNameContent(
                        uiState = uiState,
                        onNameChange = viewModel::onHouseholdNameChange,
                        onSubmit = viewModel::submitHouseholdName,
                        onBack = viewModel::back,
                    )
                } else {
                    CreateContent(
                        uiState = uiState,
                        onConfirm = viewModel::confirmCreatedHousehold,
                        onRetry = viewModel::submitHouseholdName,
                        onBack = viewModel::back,
                        onCodeCopied = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(codeCopiedMessage, duration = SnackbarDuration.Short)
                            }
                        },
                    )
                }
                HouseholdMode.JOIN -> JoinContent(
                    uiState = uiState,
                    onCodeChange = viewModel::onJoinCodeChange,
                    onJoin = viewModel::joinHousehold,
                    onBack = viewModel::back,
                )
            }
        }
    }
}

@Composable
private fun ProfileSetupContent(
    deviceProfile: DeviceProfile,
    onContinue: () -> Unit,
) {
    val photoPath by deviceProfile.photoPath.collectAsState()
    var nameInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let { picked -> coroutineScope.launch { deviceProfile.setPhotoFromUri(picked) } } }

    Text(
        text = stringResource(R.string.household_profile_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.household_profile_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
    )

    Box {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(96.dp)
                .clickable {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
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
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
        if (photoPath != null) {
            IconButton(
                onClick = { coroutineScope.launch { deviceProfile.clearPhoto() } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.more_profile_remove_photo_cd),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    TextButton(
        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(stringResource(R.string.household_profile_add_photo))
    }

    OutlinedTextField(
        value = nameInput,
        onValueChange = { nameInput = it },
        label = { Text(stringResource(R.string.more_profile_title)) },
        placeholder = { Text(stringResource(R.string.more_profile_name_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    )
    Button(
        onClick = {
            deviceProfile.setDisplayName(nameInput)
            onContinue()
        },
        enabled = nameInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        Text(stringResource(R.string.household_continue))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholdNameContent(
    uiState: HouseholdUiState,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Icon(
        imageVector = Icons.Filled.Groups,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.household_name_title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
    )
    OutlinedTextField(
        value = uiState.householdNameInput,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.household_name_label)) },
        placeholder = { Text(stringResource(R.string.household_name_placeholder)) },
        singleLine = true,
        supportingText = {
            Text(
                stringResource(
                    R.string.household_name_char_count_format,
                    uiState.householdNameInput.length,
                    HouseholdRepository.HOUSEHOLD_NAME_MAX_LENGTH,
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onSubmit,
        enabled = uiState.householdNameInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(stringResource(R.string.household_continue))
    }
    TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.common_back))
    }
}

@Composable
private fun ChooseContent(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Icon(
        imageVector = Icons.Filled.Groups,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.household_welcome_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 20.dp),
    )
    Text(
        text = stringResource(R.string.household_welcome_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
    )
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.household_create_button))
    }
    OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.household_join_button))
    }
}

@Composable
private fun CreateContent(
    uiState: HouseholdUiState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onCodeCopied: () -> Unit,
) {
    var hasCopiedCode by remember { mutableStateOf(false) }
    var showBackWarning by remember { mutableStateOf(false) }

    // There's no in-app "Terug" button once a code exists (only "Kopieer" and
    // "Doorgaan" below) — this is specifically about the system back gesture/button,
    // which would otherwise silently discard an uncopied code.
    BackHandler(enabled = uiState.createdCode != null && !hasCopiedCode) {
        showBackWarning = true
    }

    when {
        uiState.isLoading -> {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.household_creating),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        uiState.errorMessage != null || uiState.hasGenericError -> {
            val message = uiState.errorMessage ?: stringResource(R.string.household_generic_error)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.household_retry))
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        }
        uiState.createdCode != null -> {
            val code = uiState.createdCode
            val clipboard = LocalClipboardManager.current
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.household_created_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.household_created_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Surface(
                shape = SoftCardShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = code,
                    style = TextStyle(fontSize = 32.sp, letterSpacing = 8.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                )
            }
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    hasCopiedCode = true
                    onCodeCopied()
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.household_copy_code), modifier = Modifier.padding(start = 4.dp))
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text(stringResource(R.string.household_continue))
            }
        }
    }

    if (showBackWarning) {
        AlertDialog(
            onDismissRequest = { showBackWarning = false },
            title = { Text(stringResource(R.string.household_create_back_warning_title)) },
            text = { Text(stringResource(R.string.household_create_back_warning_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackWarning = false
                        onBack()
                    },
                ) { Text(stringResource(R.string.household_create_back_warning_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackWarning = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinContent(
    uiState: HouseholdUiState,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
) {
    Icon(
        imageVector = Icons.Filled.Groups,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.household_join_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
    )
    val isCompleteLength = uiState.joinCodeInput.length == HouseholdRepository.CODE_LENGTH
    val hasError = uiState.errorMessage != null || uiState.hasGenericError || uiState.householdFull
    val supportingMessage = when {
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.hasGenericError -> stringResource(R.string.household_generic_error)
        // Premium households have no member cap at all (see
        // HouseholdMembersRepository.HouseholdJoinResult), so this can only mean the
        // household isn't Premium yet.
        uiState.householdFull -> stringResource(R.string.household_join_full_error)
        uiState.joinCodeInput.isNotEmpty() && !isCompleteLength ->
            stringResource(R.string.household_join_code_length_hint, HouseholdRepository.CODE_LENGTH)
        else -> null
    }
    OutlinedTextField(
        value = uiState.joinCodeInput,
        onValueChange = { onCodeChange(it.uppercase()) },
        label = { Text(stringResource(R.string.household_code_label)) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center),
        isError = hasError,
        supportingText = supportingMessage?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onJoin,
        enabled = !uiState.isLoading && isCompleteLength,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.household_join_action))
        }
    }
    TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.common_back))
    }
}
