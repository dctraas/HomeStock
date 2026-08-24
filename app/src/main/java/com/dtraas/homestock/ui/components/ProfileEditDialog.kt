package com.dtraas.homestock.ui.components

import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dtraas.homestock.R
import java.io.File

// Matches HouseholdRepository.HOUSEHOLD_NAME_MAX_LENGTH — there's no shared constant between
// the two (a member's own display name and a household's name are unrelated concepts that
// just happen to want the same limit), so this is its own small local cap for the counter below.
private const val DISPLAY_NAME_MAX_LENGTH = 24

/** Shared name + photo editor, used both by the Home-screen profile button and Instellingen. */
@Composable
fun ProfileEditDialog(
    displayName: String?,
    photoPath: String?,
    onSaveName: (String) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var nameInput by remember { mutableStateOf(displayName ?: "") }
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(onPhotoPicked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_profile_dialog_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                            onClick = onRemovePhoto,
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
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= DISPLAY_NAME_MAX_LENGTH) nameInput = it },
                    label = { Text(stringResource(R.string.more_profile_title)) },
                    placeholder = { Text(stringResource(R.string.more_profile_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // Reuses the same "%1$d/%2$d tekens" format the household-name step of
                    // onboarding already uses for its own char counter (see HouseholdScreen's
                    // HouseholdNameContent) — same shape of value, no need for a near-duplicate
                    // string across all 5 locales.
                    text = stringResource(R.string.household_name_char_count_format, nameInput.length, DISPLAY_NAME_MAX_LENGTH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveName(nameInput)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
