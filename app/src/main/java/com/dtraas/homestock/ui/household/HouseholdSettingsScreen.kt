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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.data.repository.HouseholdInviteLink
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.data.repository.RecentHousehold
import com.dtraas.homestock.data.repository.RecipeRepository
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
            HomeStockTopAppBar(
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
                HouseholdSection(
                    nameInput = nameInput,
                    onNameInputChange = { if (it.length <= HouseholdRepository.HOUSEHOLD_NAME_MAX_LENGTH) nameInput = it },
                    members = members,
                    isDeleting = isDeleting,
                    onLeaveClick = { showLeaveConfirm = true },
                    onDeleteClick = { showDeleteConfirm = true },
                    myExcludedAllergens = members.firstOrNull { it.isCurrentDevice }?.excludedAllergens ?: emptySet(),
                    onToggleMyAllergen = { allergen ->
                        val current = members.firstOrNull { it.isCurrentDevice }?.excludedAllergens ?: emptySet()
                        val updated = if (allergen in current) current - allergen else current + allergen
                        coroutineScope.launch { householdMembersRepository.updateExcludedAllergens(updated) }
                    },
                )

                if (otherHouseholds.isNotEmpty()) {
                    SwitchHouseholdSection(
                        households = otherHouseholds,
                        isSwitching = isSwitching,
                        onHouseholdClick = { switchTarget = it },
                        onForgetClick = { householdSession.forgetHousehold(it.id) },
                    )
                }
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
 * Naam, Leden, and the leave/delete actions all in one card rather than three separate ones —
 * they're all "manage this household" actions on the same object, and splitting them into
 * their own cards mostly just added visual weight without actually separating unrelated
 * concerns (unlike [SwitchHouseholdSection] below, which really is a different thing: switching
 * *away* from this household to a different one).
 *
 * No explicit save button for the name field — a rename is saved when the screen is left (see
 * [HouseholdSettingsScreen]'s `saveNameAndGoBack`), which replaces both the old dialog's
 * disconnected "OK" button (that only ever dismissed, never saved) and this screen's earlier
 * always-visible save button.
 */
@Composable
private fun HouseholdSection(
    nameInput: String,
    onNameInputChange: (String) -> Unit,
    members: List<HouseholdMember>,
    isDeleting: Boolean,
    onLeaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    myExcludedAllergens: Set<Allergen>,
    onToggleMyAllergen: (Allergen) -> Unit,
) {
    SectionCard {
        Text(stringResource(R.string.household_name_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameInputChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.more_household_members_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            MyAllergensMenuButton(excludedAllergens = myExcludedAllergens, onToggle = onToggleMyAllergen)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            members.forEach { member -> HouseholdMemberRow(member) }
        }

        ActionButtonsRow(
            isDeleting = isDeleting,
            onLeaveClick = onLeaveClick,
            onDeleteClick = onDeleteClick,
        )
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
 * Leave/delete as two plain, right-aligned, labeled buttons, side by side on one row. A
 * neutral (theme-aware "black") tint rather than the error color keeps them from reading as
 * loud/alarming at a glance; the actual warning is the confirmation dialog each one opens (see
 * [HouseholdSettingsScreen]'s showLeaveConfirm/showDeleteConfirm), not the button color.
 */
@Composable
private fun ActionButtonsRow(isDeleting: Boolean, onLeaveClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        OutlinedButton(
            onClick = onLeaveClick,
            enabled = !isDeleting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(R.string.more_leave), modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(
            onClick = onDeleteClick,
            enabled = !isDeleting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(R.string.more_delete_household), modifier = Modifier.padding(start = 6.dp))
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
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = member.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_member_unnamed),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Visible to the whole household, editable only by that member's own device (see
            // MyAllergensMenuButton) — the point is everyone can see at a glance what to avoid
            // when picking recipes together, not just the person who set it.
            if (member.excludedAllergens.isNotEmpty()) {
                // .map { stringResource(...) } (an inline stdlib call) rather than
                // .joinToString(...) { stringResource(...) } — joinToString's transform lambda
                // isn't inline, so a @Composable call inside it wouldn't compile.
                val labels = member.excludedAllergens.sortedBy { it.ordinal }.map { stringResource(it.labelRes) }
                Text(
                    text = stringResource(R.string.household_member_allergens_format, labels.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (member.isCurrentDevice) {
            Text(
                text = stringResource(R.string.more_household_member_you),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Same dropdown-of-checkable-items shape as RecipesScreen's AllergenFilterMenuButton, reused
 * here for a different purpose: this one *writes* a persistent per-member preference (see
 * [HouseholdMembersRepository.updateExcludedAllergens]) rather than an ephemeral session filter.
 * Only ever edits the current device's own entry.
 */
@Composable
private fun MyAllergensMenuButton(excludedAllergens: Set<Allergen>, onToggle: (Allergen) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            if (excludedAllergens.isEmpty()) {
                Icon(Icons.Filled.FilterAlt, contentDescription = stringResource(R.string.household_allergens_menu_cd))
            } else {
                BadgedBox(badge = { Badge() }) {
                    Icon(Icons.Filled.FilterAlt, contentDescription = stringResource(R.string.household_allergens_menu_cd))
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            RecipeRepository.filterableAllergens.forEach { allergen ->
                val selected = allergen in excludedAllergens
                DropdownMenuItem(
                    text = { Text(stringResource(allergen.labelRes)) },
                    trailingIcon = {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = { onToggle(allergen) },
                )
            }
        }
    }
}

