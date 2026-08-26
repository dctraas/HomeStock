package com.dtraas.homestock.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.theme.SoftCardShape
import java.io.File

// Matches HouseholdRepository.HOUSEHOLD_NAME_MAX_LENGTH — there's no shared constant between
// the two (a member's own display name and a household's name are unrelated concepts that
// just happen to want the same limit), so this is its own small local cap for the counter below.
private const val DISPLAY_NAME_MAX_LENGTH = 24

/**
 * Shared name + photo editor, used both by the Home-screen profile button and Instellingen.
 * Rebuilt as a bottom sheet (2026-08 dialog review): a squircle avatar with a visible camera
 * badge (the old plain circle gave no hint it was tappable), the char counter moved inside the
 * field's own trailing edge, and a live "ZO ZIET HET UIT" preview so the household can see what
 * their own name/photo will look like on an activity row without saving first and going to look.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetTitle(
                title = stringResource(R.string.more_profile_title),
                subtitle = stringResource(R.string.more_profile_subtitle),
            )

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(72.dp)
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
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }
                    // The tap affordance the plain circle used to give no hint of — a visible
                    // camera badge instead of relying on the household to guess.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier.align(Alignment.BottomEnd).size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= DISPLAY_NAME_MAX_LENGTH) nameInput = it },
                        placeholder = { Text(stringResource(R.string.more_profile_name_placeholder)) },
                        singleLine = true,
                        trailingIcon = {
                            Text(
                                text = stringResource(R.string.household_name_char_count_format, nameInput.length, DISPLAY_NAME_MAX_LENGTH),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                // A touch of end padding — flush against the field's own edge
                                // read as too tight.
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.more_profile_helper),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                    if (photoPath != null) {
                        TextButton(onClick = onRemovePhoto, modifier = Modifier.padding(top = 2.dp)) {
                            Text(
                                text = stringResource(R.string.more_profile_remove_photo_cd),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetEyebrow(text = stringResource(R.string.more_profile_preview_section))
                ProfilePreviewRow(
                    name = nameInput.trim().takeIf { it.isNotEmpty() } ?: stringResource(R.string.more_household_member_unnamed),
                    photoPath = photoPath,
                )
            }

            SheetPrimaryButton(
                text = stringResource(R.string.common_save),
                enabled = nameInput.isNotBlank(),
                onClick = {
                    onSaveName(nameInput)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * A real activity-log row (same shape as NotificationsScreen's `HouseholdActivityRow` — actor,
 * bold product name, past-tense detail, then a time line), rendered live with whatever's
 * currently typed/picked, so the household sees their own name and photo the way it will
 * actually show up before they've even saved — a real preview, not a hypothetical description.
 */
@Composable
private fun ProfilePreviewRow(name: String, photoPath: String?) {
    val actionText = buildAnnotatedString {
        append(name)
        append(" ")
        append(stringResource(R.string.activity_action_connector))
        append(" ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.more_profile_preview_product)) }
        append(" ")
        append(stringResource(R.string.activity_type_added_to_shopping_list).replaceFirstChar { it.lowercase() })
        append(".")
    }
    Surface(shape = SoftCardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (photoPath != null) {
                        AsyncImage(
                            model = File(photoPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = actionText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.common_now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
