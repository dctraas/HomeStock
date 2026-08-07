package com.dtraas.homestock.ui.household

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.HouseholdInviteLink
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.ui.theme.SoftCardShape
import kotlinx.coroutines.launch

/**
 * Instellingen > Huishouden — split out of a former [androidx.compose.material3.AlertDialog]
 * into its own screen once it grew a members list on top of the code display and rename
 * field: a small popup doesn't have room to breathe for that much content, and cramming it
 * in obscured the actual save action for a rename (see [NameSection]'s doc).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as HomeStockApplication
    val householdSession = application.container.householdSession
    val householdId by householdSession.householdId.collectAsState()
    val householdRepository = application.container.householdRepository
    val householdName by householdRepository.observeHouseholdName().collectAsState(initial = null)
    val householdMembersRepository = application.container.householdMembersRepository
    val members by householdMembersRepository.observeMembers().collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteSuccessMessage = stringResource(R.string.more_delete_household_success)
    val deleteErrorMessage = stringResource(R.string.more_delete_household_error)

    // Re-seeds from the live household name whenever it changes (e.g. this screen reopening,
    // or a housemate renaming it on their own device) — but not on every keystroke, since
    // `key1 = householdName` only re-runs this initializer when that upstream value itself changes.
    var nameInput by remember(householdName) { mutableStateOf(householdName.orEmpty()) }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // There's no explicit save button any more — leaving the screen (either via the app bar's
    // back arrow or the system back gesture/button, hence both wiring below) is the save
    // action. Firestore's offline persistence queues this write even without a connection, so
    // it doesn't need to block navigating away.
    fun saveNameAndGoBack() {
        val trimmed = nameInput.trim()
        val idToRename = householdId
        if (idToRename != null && trimmed.isNotBlank() && trimmed != householdName) {
            coroutineScope.launch { householdRepository.renameHousehold(idToRename, trimmed) }
        }
        onBack()
    }

    BackHandler(onBack = ::saveNameAndGoBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.more_household_title)) },
                navigationIcon = {
                    IconButton(onClick = ::saveNameAndGoBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Split into a scrollable region (weighted) plus a footer sitting outside of it,
        // rather than putting a weighted Spacer inside the scrollable Column itself — Compose
        // disallows weight() on a child of a verticalScroll() container (it measures that
        // container's content with unbounded height, which a weight expects to be bounded, and
        // crashes). This split is the standard "scrollable content + pinned footer" pattern.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NameSection(
                    nameInput = nameInput,
                    onNameInputChange = { if (it.length <= HouseholdRepository.HOUSEHOLD_NAME_MAX_LENGTH) nameInput = it },
                )

                MembersSection(members = members)

                ActionButtonsRow(
                    isDeleting = isDeleting,
                    onLeaveClick = { showLeaveConfirm = true },
                    onDeleteClick = { showDeleteConfirm = true },
                )
            }

            CodeSection(
                householdCode = householdId,
                onShareClick = { code ->
                    val message = context.getString(
                        R.string.household_share_invite_text_format,
                        HouseholdInviteLink.build(code),
                        code,
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                },
            )
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.more_leave_dialog_title)) },
            text = { Text(stringResource(R.string.more_leave_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        coroutineScope.launch {
                            householdMembersRepository.unregisterCurrentDevice()
                            householdSession.leaveHousehold()
                        }
                    },
                ) { Text(stringResource(R.string.more_leave)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            title = { Text(stringResource(R.string.more_delete_household_dialog_title)) },
            text = { Text(stringResource(R.string.more_delete_household_dialog_text)) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        val idToDelete = householdId ?: return@TextButton
                        isDeleting = true
                        coroutineScope.launch {
                            try {
                                householdRepository.deleteHousehold(idToDelete)
                                showDeleteConfirm = false
                                // Shown while this screen (and its SnackbarHost) still exist —
                                // leaveHousehold() below flips householdId to null, which
                                // MainActivity reacts to by swapping to HouseholdScreen and
                                // tearing this composition down, so the snackbar must finish
                                // first or it would never be seen.
                                snackbarHostState.showSnackbar(deleteSuccessMessage, duration = SnackbarDuration.Short)
                                householdSession.leaveHousehold()
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(deleteErrorMessage, duration = SnackbarDuration.Short)
                            } finally {
                                isDeleting = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.more_delete_household_confirm)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showDeleteConfirm = false },
                ) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/**
 * Low-key footer pinned to the very bottom of the screen, outside the scrollable content
 * above it — the code is for sharing, not editing, so it doesn't need a card of its own like
 * the sections above it, and staying put at the bottom keeps it easy to find regardless of
 * how much content (e.g. members) is above it.
 */
@Composable
private fun CodeSection(householdCode: String?, onShareClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp)
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.more_household_code_format, householdCode ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(enabled = householdCode != null, onClick = { householdCode?.let(onShareClick) }) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(R.string.household_share_invite_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * No explicit save button any more — a rename is saved when the screen is left (see
 * [HouseholdSettingsScreen]'s `saveNameAndGoBack`), which replaces both the old dialog's
 * disconnected "OK" button (that only ever dismissed, never saved) and this screen's earlier
 * always-visible save button.
 */
@Composable
private fun NameSection(nameInput: String, onNameInputChange: (String) -> Unit) {
    SectionCard {
        Text(stringResource(R.string.household_name_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameInputChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MembersSection(members: List<HouseholdMember>) {
    SectionCard {
        Text(
            text = stringResource(R.string.more_household_members_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            members.forEach { member -> HouseholdMemberRow(member) }
        }
    }
}

/**
 * Leave/delete as two plain, right-aligned icon buttons — no card, no red background, no text
 * labels. A neutral (theme-aware "black") tint rather than the error color keeps them from
 * reading as loud/alarming at a glance; the actual warning is the confirmation dialog each one
 * opens (see [HouseholdSettingsScreen]'s showLeaveConfirm/showDeleteConfirm), not the icon color.
 */
@Composable
private fun ActionButtonsRow(isDeleting: Boolean, onLeaveClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        IconButton(onClick = onLeaveClick, enabled = !isDeleting) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = stringResource(R.string.more_leave),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDeleteClick, enabled = !isDeleting) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = stringResource(R.string.more_delete_household),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HouseholdMemberRow(member: HouseholdMember) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            if (member.photoUrl != null) {
                AsyncImage(
                    model = member.photoUrl,
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
        Text(
            text = member.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_member_unnamed),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (member.isCurrentDevice) {
            Text(
                text = stringResource(R.string.more_household_member_you),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

