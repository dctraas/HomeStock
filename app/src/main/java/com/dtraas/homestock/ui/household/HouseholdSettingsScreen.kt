package com.dtraas.homestock.ui.household

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.LaunchedEffect
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
import com.dtraas.homestock.data.repository.RecentHousehold
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
    val recentHouseholds by householdSession.recentHouseholds.collectAsState()
    // Switching to the current household would be a no-op, and it's already shown above as
    // "this" household — only *other* previously-joined households belong in the switcher.
    val otherHouseholds = recentHouseholds.filter { it.id != householdId }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteSuccessMessage = stringResource(R.string.more_delete_household_success)
    val deleteErrorMessage = stringResource(R.string.more_delete_household_error)
    val switchFullMessage = stringResource(R.string.household_join_full_error)
    val switchNotFoundMessage = stringResource(R.string.household_switch_not_found_error)

    // Re-seeds from the live household name whenever it changes (e.g. this screen reopening,
    // or a housemate renaming it on their own device) — but not on every keystroke, since
    // `key1 = householdName` only re-runs this initializer when that upstream value itself changes.
    var nameInput by remember(householdName) { mutableStateOf(householdName.orEmpty()) }

    // Keeps the switcher's cached label for *this* household in sync with its live name —
    // covers both "just joined by code, name wasn't known yet" and "a housemate renamed it".
    LaunchedEffect(householdId, householdName) {
        val id = householdId
        if (id != null && householdName != null) householdSession.rememberHousehold(id, householdName)
    }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var switchTarget by remember { mutableStateOf<RecentHousehold?>(null) }
    var isSwitching by remember { mutableStateOf(false) }

    fun switchToHousehold(target: RecentHousehold) {
        isSwitching = true
        coroutineScope.launch {
            try {
                householdRepository.joinHousehold(target.id)
                    .onSuccess { validId ->
                        if (!householdMembersRepository.canJoin(validId)) {
                            snackbarHostState.showSnackbar(switchFullMessage, duration = SnackbarDuration.Short)
                            return@onSuccess
                        }
                        // Leave the currently active household before joining the new one, so
                        // this device isn't left registered as a member of both at once.
                        householdMembersRepository.unregisterCurrentDevice()
                        householdMembersRepository.registerCurrentDevice(validId)
                        householdSession.rememberHousehold(validId, target.name)
                        householdSession.setHousehold(validId)
                    }
                    .onFailure {
                        // Most likely it was deleted since this device last saw it — nothing
                        // left to switch to, so drop it from the list instead of offering it again.
                        householdSession.forgetHousehold(target.id)
                        snackbarHostState.showSnackbar(switchNotFoundMessage, duration = SnackbarDuration.Short)
                    }
            } finally {
                isSwitching = false
            }
        }
    }

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
                actions = {
                    val code = householdId
                    IconButton(
                        enabled = code != null,
                        onClick = {
                            if (code != null) {
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
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.household_share_invite_cd),
                        )
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

                if (otherHouseholds.isNotEmpty()) {
                    SwitchHouseholdSection(
                        households = otherHouseholds,
                        isSwitching = isSwitching,
                        onHouseholdClick = { switchTarget = it },
                        onForgetClick = { householdSession.forgetHousehold(it.id) },
                    )
                }

                ActionButtonsRow(
                    isDeleting = isDeleting,
                    onLeaveClick = { showLeaveConfirm = true },
                    onDeleteClick = { showDeleteConfirm = true },
                )
            }

            CodeSection(householdCode = householdId)
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

    switchTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isSwitching) switchTarget = null },
            title = { Text(stringResource(R.string.household_switch_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.household_switch_dialog_text_format,
                        householdName ?: stringResource(R.string.household_switch_unnamed_fallback),
                        target.name ?: stringResource(R.string.household_switch_unnamed_fallback),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isSwitching,
                    onClick = {
                        switchToHousehold(target)
                        switchTarget = null
                    },
                ) { Text(stringResource(R.string.household_switch_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !isSwitching, onClick = { switchTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
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
 * how much content (e.g. members) is above it. The share action itself lives in the top app
 * bar (see [HouseholdSettingsScreen]) so this is just the code, centered.
 */
@Composable
private fun CodeSection(householdCode: String?) {
    Text(
        text = stringResource(R.string.more_household_code_format, householdCode ?: "—"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    )
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
 * Households this device created or joined before, most-recent-first (see
 * [com.dtraas.homestock.data.repository.HouseholdSession.recentHouseholds]) — tapping one
 * rejoins it without retyping its code, a faster path than "Huishouden verlaten" followed by
 * manually entering a remembered code on the onboarding screen.
 */
@Composable
private fun SwitchHouseholdSection(
    households: List<RecentHousehold>,
    isSwitching: Boolean,
    onHouseholdClick: (RecentHousehold) -> Unit,
    onForgetClick: (RecentHousehold) -> Unit,
) {
    SectionCard {
        Text(
            text = stringResource(R.string.household_switch_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            households.forEach { household ->
                RecentHouseholdRow(
                    household = household,
                    enabled = !isSwitching,
                    onClick = { onHouseholdClick(household) },
                    onForgetClick = { onForgetClick(household) },
                )
            }
        }
    }
}

@Composable
private fun RecentHouseholdRow(
    household: RecentHousehold,
    enabled: Boolean,
    onClick: () -> Unit,
    onForgetClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = household.name?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.household_switch_unnamed_format, household.id),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        )
        IconButton(enabled = enabled, onClick = onForgetClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.household_switch_forget_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
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

