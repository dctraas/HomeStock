package com.dtraas.boodschapbeheer.ui.household

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dtraas.boodschapbeheer.BoodschapBeheerApplication
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.repository.HouseholdRepository
import com.dtraas.boodschapbeheer.ui.theme.SoftCardShape

/**
 * Shown before the main app whenever this device isn't part of a household yet.
 * A household is the sharing boundary: every device that creates or joins the
 * same household code sees the same inventory, shopping list and activity log.
 */
@Composable
fun HouseholdScreen() {
    val application = LocalContext.current.applicationContext as BoodschapBeheerApplication
    val viewModel: HouseholdViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HouseholdViewModel(
                    householdRepository = application.container.householdRepository,
                    householdSession = application.container.householdSession,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.mode) {
                HouseholdMode.CHOOSE -> ChooseContent(
                    onCreate = viewModel::selectCreate,
                    onJoin = viewModel::selectJoin,
                )
                HouseholdMode.CREATE -> CreateContent(
                    uiState = uiState,
                    onConfirm = viewModel::confirmCreatedHousehold,
                    onRetry = viewModel::selectCreate,
                    onBack = viewModel::back,
                )
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
    val hasError = uiState.errorMessage != null || uiState.hasGenericError
    val supportingMessage = when {
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.hasGenericError -> stringResource(R.string.household_generic_error)
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
