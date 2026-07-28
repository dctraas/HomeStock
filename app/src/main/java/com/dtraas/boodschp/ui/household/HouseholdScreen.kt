package com.dtraas.boodschp.ui.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.boodschp.BoodschpApplication

/**
 * Shown before the main app whenever this device isn't part of a household yet.
 * A household is the sharing boundary: every device that creates or joins the
 * same household code sees the same inventory, shopping list and activity log.
 */
@Composable
fun HouseholdScreen() {
    val application = LocalContext.current.applicationContext as BoodschpApplication
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
        text = "Welkom bij Boodschp",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 20.dp),
    )
    Text(
        text = "Deel je voorraad en boodschappenlijst met je huisgenoten. Maak een nieuw huishouden aan, " +
            "of sluit aan bij een bestaand huishouden met een code.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
    )
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
        Text("Nieuw huishouden starten")
    }
    OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Ik heb al een huishouden-code")
    }
}

@Composable
private fun CreateContent(
    uiState: HouseholdUiState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    when {
        uiState.isLoading -> {
            CircularProgressIndicator()
            Text(
                text = "Huishouden aanmaken…",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        uiState.errorMessage != null -> {
            val message = uiState.errorMessage
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Probeer opnieuw")
            }
            TextButton(onClick = onBack) { Text("Terug") }
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
                text = "Huishouden aangemaakt!",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Deel deze code met je huisgenoten zodat zij ook kunnen aansluiten.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
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
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Kopieer code", modifier = Modifier.padding(start = 4.dp))
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text("Doorgaan")
            }
        }
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
        text = "Huishouden koppelen",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
    )
    OutlinedTextField(
        value = uiState.joinCodeInput,
        onValueChange = { onCodeChange(it.uppercase()) },
        label = { Text("Huishouden-code") },
        singleLine = true,
        textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center),
        isError = uiState.errorMessage != null,
        supportingText = uiState.errorMessage?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onJoin,
        enabled = !uiState.isLoading && uiState.joinCodeInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Aansluiten")
        }
    }
    TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
        Text("Terug")
    }
}
