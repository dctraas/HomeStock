package com.dtraas.homestock.ui.household

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.export.CsvExporter
import com.dtraas.homestock.data.export.InventoryCsvHeaders
import com.dtraas.homestock.data.export.ShoppingListCsvHeaders
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.model.MeasurementUnit
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.SheetActionRow
import com.dtraas.homestock.ui.components.SheetEyebrow
import com.dtraas.homestock.ui.components.SheetPrimaryButton
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.data.repository.HouseholdCapacityInfo
import com.dtraas.homestock.data.repository.HouseholdInviteLink
import com.dtraas.homestock.data.repository.HouseholdJoinResult
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.data.repository.RecentHousehold
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val capacityInfo by householdMembersRepository.observeCapacityInfo().collectAsState(
        initial = HouseholdCapacityInfo(memberCount = 0, limit = HouseholdMembersRepository.FREE_MEMBER_LIMIT, isPremium = false),
    )
    val isPremium by householdMembersRepository.observeHouseholdIsPremium().collectAsState(initial = false)
    // Matched by exact display name, same reasoning as NotificationsViewModel.photoUrlFor and
    // StatisticsViewModel's MemberScanEntry — activity log entries only ever stamp a plain
    // name, not a uid, so that's the only join key available here too.
    val recentActivity by application.container.activityLogRepository.observeRecent().collectAsState(initial = emptyList())
    val lastActiveByName = remember(recentActivity) {
        recentActivity.groupBy { it.actorName }.mapValues { (_, entries) -> entries.maxOf { it.timestamp } }
    }
    val inviteExpiresAt by householdRepository.observeInviteExpiresAt().collectAsState(initial = null)
    val recentHouseholds by householdSession.recentHouseholds.collectAsState()
    // Switching to the current household would be a no-op, and it's already shown above as
    // "this" household — only *other* previously-joined households belong in the switcher.
    val otherHouseholds = recentHouseholds.filter { it.id != householdId }

    // Real counts for the delete-confirmation sheet's "Dit verdwijnt" card (cross-cutting rule
    // #4 of the 2026-08 dialog review: "put the app's knowledge in the sheet") — the same
    // repositories MoreScreen's own Data-overzetten sheet reads from.
    val inventoryRepository = application.container.inventoryRepository
    val inventoryItemCount by remember {
        inventoryRepository.observeInventoryWithProduct().map { it.size }
    }.collectAsState(initial = 0)
    val shoppingListRepository = application.container.shoppingListRepository
    val shoppingListItemCount by remember {
        shoppingListRepository.observeShoppingList().map { it.size }
    }.collectAsState(initial = 0)
    val shoppingListsRepository = application.container.shoppingListsRepository
    val namedListCount by remember {
        shoppingListsRepository.observeLists().map { it.size }
    }.collectAsState(initial = 0)
    val householdCreatedAt by householdRepository.observeHouseholdCreatedAt().collectAsState(initial = null)
    val historyMonths = householdCreatedAt?.let { created ->
        val elapsedDays = (System.currentTimeMillis() - created).coerceAtLeast(0L) / TimeUnit.DAYS.toMillis(1)
        (elapsedDays / 30L).toInt().coerceAtLeast(1)
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteSuccessMessage = stringResource(R.string.more_delete_household_success)
    val deleteErrorMessage = stringResource(R.string.more_delete_household_error)
    val switchFullMessage = stringResource(R.string.household_join_full_error)
    val switchNotFoundMessage = stringResource(R.string.household_switch_not_found_error)

    // "Eerst exporteren als CSV" in the delete sheet — a full Voorraad + Boodschappenlijst
    // export, same CsvExporter this app's Data-overzetten sheet uses, just always "Alles"
    // scope here since the household (and everything in it) is about to be gone either way.
    val exportPreferences = application.container.exportPreferences
    var pendingExportCsv by remember { mutableStateOf<String?>(null) }
    val exportErrorMessage = stringResource(R.string.more_export_error)
    val exportSuccessMessage = stringResource(R.string.more_export_success)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingExportCsv
        pendingExportCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val message = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                exportPreferences.recordExportNow()
                exportSuccessMessage
            } catch (e: Exception) {
                exportErrorMessage
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
    val categoryLabels = Category.entries.associate { it.storageKey to stringResource(it.displayNameRes) }
    val unitLabels = MeasurementUnit.entries.associate { it.storageKey to stringResource(it.shortLabelRes) }
    val csvYes = stringResource(R.string.common_yes)
    val csvNo = stringResource(R.string.common_no)
    val inventoryCsvHeaders = InventoryCsvHeaders(
        name = stringResource(R.string.common_name),
        brand = stringResource(R.string.product_detail_field_brand),
        category = stringResource(R.string.category_dropdown_label),
        quantity = stringResource(R.string.common_quantity),
        unit = stringResource(R.string.product_detail_field_unit),
        expiration = stringResource(R.string.product_detail_expiration_label),
        minQuantity = stringResource(R.string.product_detail_min_quantity_label),
        favorite = stringResource(R.string.more_export_header_favorite),
        note = stringResource(R.string.shopping_list_note_label),
    )
    val shoppingListCsvHeaders = ShoppingListCsvHeaders(
        name = stringResource(R.string.common_name),
        category = stringResource(R.string.category_dropdown_label),
        store = stringResource(R.string.store_dropdown_label),
        quantity = stringResource(R.string.common_quantity),
        unit = stringResource(R.string.product_detail_field_unit),
        note = stringResource(R.string.shopping_list_note_label),
        price = stringResource(R.string.more_export_header_price),
        checked = stringResource(R.string.more_export_header_checked),
    )
    val inventorySectionTitle = stringResource(R.string.inventory_title)
    val shoppingListSectionTitle = stringResource(R.string.shopping_list_title)

    fun exportAllBeforeDelete() {
        coroutineScope.launch {
            val inventoryCsv = CsvExporter.inventoryToCsv(
                inventoryRepository.observeInventoryWithProduct().first(),
                inventoryCsvHeaders,
                categoryLabel = { key -> categoryLabels[key] ?: key },
                unitLabel = { key -> unitLabels[key] ?: (key ?: "") },
                yesLabel = csvYes,
                noLabel = csvNo,
            )
            val listCsv = CsvExporter.shoppingListToCsv(
                shoppingListRepository.observeShoppingList().first(),
                shoppingListCsvHeaders,
                categoryLabel = { key -> categoryLabels[key] ?: key },
                unitLabel = { key -> unitLabels[key] ?: key },
                yesLabel = csvYes,
                noLabel = csvNo,
            )
            pendingExportCsv = CsvExporter.combinedToCsv(inventoryCsv, listCsv, inventorySectionTitle, shoppingListSectionTitle)
            exportLauncher.launch("homestock-data.csv")
        }
    }

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
                        val joinResult = householdMembersRepository.canJoin(validId)
                        if (joinResult != HouseholdJoinResult.ALLOWED) {
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
                                // Pushes the invite's expiry back out to a fresh
                                // INVITE_VALIDITY_DAYS window before the link is actually
                                // shared, so a link handed out just now is never already
                                // stale — see HouseholdRepository.refreshInviteExpiry.
                                coroutineScope.launch {
                                    householdRepository.refreshInviteExpiry(code)
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
                    isPremium = isPremium,
                    members = members,
                    lastActiveByName = lastActiveByName,
                    capacityInfo = capacityInfo,
                    isDeleting = isDeleting,
                    onLeaveClick = { showLeaveConfirm = true },
                    onDeleteClick = { showDeleteConfirm = true },
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

            CodeSection(
                householdCode = householdId,
                inviteExpiresAt = inviteExpiresAt,
                onRefreshInvite = {
                    val code = householdId ?: return@CodeSection
                    coroutineScope.launch { householdRepository.refreshInviteExpiry(code) }
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
        DeleteHouseholdSheet(
            householdName = householdName,
            inventoryItemCount = inventoryItemCount,
            shoppingListCount = namedListCount,
            shoppingListItemCount = shoppingListItemCount,
            historyMonths = historyMonths,
            memberCount = members.size,
            isDeleting = isDeleting,
            onExportFirst = ::exportAllBeforeDelete,
            onConfirm = {
                val idToDelete = householdId ?: return@DeleteHouseholdSheet
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
            onDismiss = { showDeleteConfirm = false },
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
private fun CodeSection(householdCode: String?, inviteExpiresAt: Long?, onRefreshInvite: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.more_household_code_format, householdCode ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Only shown once an invite link has actually been shared at least once (see
        // HouseholdRepository.observeInviteExpiresAt's doc) — a household that's never used
        // "Deel uitnodiging" has nothing to show here, same as before this existed.
        if (inviteExpiresAt != null) {
            val isExpired = inviteExpiresAt < System.currentTimeMillis()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = if (isExpired) {
                        stringResource(R.string.household_invite_expired)
                    } else {
                        stringResource(R.string.household_invite_expiry_format, inviteExpiryFormatter.format(Date(inviteExpiresAt)))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRefreshInvite, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(stringResource(R.string.household_invite_refresh_action), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// A plain day-month-year is enough here — this is a soft "share again soon" nudge, not a
// precise-to-the-minute deadline, so the hour doesn't need to be shown.
private val inviteExpiryFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

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
    isPremium: Boolean,
    members: List<HouseholdMember>,
    lastActiveByName: Map<String?, Long>,
    capacityInfo: HouseholdCapacityInfo,
    isDeleting: Boolean,
    onLeaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.household_name_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (isPremium) PremiumBadge()
        }
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameInputChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = if (capacityInfo.limit != null) {
                stringResource(R.string.household_members_title_with_limit_format, capacityInfo.memberCount, capacityInfo.limit)
            } else {
                stringResource(R.string.more_household_members_title)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            members.forEach { member -> HouseholdMemberRow(member, lastActiveAt = lastActiveByName[member.displayName]) }
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
 * Leave/delete as two plain, right-aligned, labeled buttons, side by side on one row. "Verlaten"
 * stays a neutral (theme-aware "black") tint — leaving is reversible, you can always rejoin by
 * code. "Verwijderen" is not: the row itself now carries an error tint, so the danger reads at a
 * glance instead of living only in [DeleteHouseholdSheet]'s copy (2026-08 dialog review).
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
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
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

/**
 * "Make delete unmistakably destructive" (2026-08 dialog review) — a coral/error icon badge, the
 * household's own name in the question, a bolded reminder that every housemate loses access, a
 * "Dit verdwijnt" card with real counts (not vague "your data"), an escape hatch that actually
 * exports before anything is lost, and type-to-confirm so the final button can't be an idle tap.
 * [historyMonths] is null for a household created before `createdAt` existed — that row is just
 * omitted rather than showing a wrong or zero count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteHouseholdSheet(
    householdName: String?,
    inventoryItemCount: Int,
    shoppingListCount: Int,
    shoppingListItemCount: Int,
    historyMonths: Int?,
    memberCount: Int,
    isDeleting: Boolean,
    onExportFirst: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmInput by remember { mutableStateOf("") }
    val confirmKeyword = stringResource(R.string.more_delete_household_confirm_keyword)
    val isConfirmed = confirmInput.trim().equals(confirmKeyword, ignoreCase = true)

    HomeStockBottomSheet(onDismissRequest = { if (!isDeleting) onDismiss() }) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(sheetContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            SheetTitle(
                title = stringResource(
                    R.string.more_delete_household_sheet_title_format,
                    householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_default_name),
                ),
            )
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.more_delete_household_sheet_body_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.more_delete_household_sheet_body_bold))
                    }
                    append(stringResource(R.string.more_delete_household_sheet_body_suffix))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = SoftCardShapeCompact,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetEyebrow(text = stringResource(R.string.more_delete_household_disappears_eyebrow), color = MaterialTheme.colorScheme.error)
                    DisappearsRow(
                        icon = Icons.Filled.Inventory,
                        text = pluralStringResource(R.plurals.more_export_subtitle_inventory_format, inventoryItemCount, inventoryItemCount),
                    )
                    DisappearsRow(
                        icon = Icons.Filled.ViewList,
                        text = stringResource(R.string.more_delete_household_lists_format, shoppingListCount, shoppingListItemCount),
                    )
                    if (historyMonths != null) {
                        DisappearsRow(
                            icon = Icons.Filled.Schedule,
                            text = pluralStringResource(R.plurals.more_delete_household_months_format, historyMonths, historyMonths),
                        )
                    }
                    DisappearsRow(
                        icon = Icons.Filled.Groups,
                        text = pluralStringResource(R.plurals.more_delete_household_members_format, memberCount, memberCount),
                    )
                }
            }
            SheetActionRow(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.more_delete_household_export_first_action),
                onClick = onExportFirst,
                enabled = !isDeleting,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                borderColor = MaterialTheme.colorScheme.primaryContainer,
                iconTileColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.more_delete_household_type_to_confirm_label_format, confirmKeyword),
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = confirmInput,
                    onValueChange = { confirmInput = it },
                    singleLine = true,
                    enabled = !isDeleting,
                    placeholder = { Text(confirmKeyword) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SheetPrimaryButton(
                text = stringResource(R.string.more_delete_household_final_confirm),
                onClick = onConfirm,
                enabled = isConfirmed && !isDeleting,
                loading = isDeleting,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        }
    }
}

@Composable
private fun DisappearsRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Small "Premium" pill — same [Icons.Filled.WorkspacePremium] mark MoreScreen's own Premium
 *  card and rows use, just shrunk down to fit inline next to the "Naam" label. */
@Composable
private fun PremiumBadge() {
    Surface(shape = RoundedCornerShape(percent = 50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Text(
                // Just "Premium" — unlike MoreScreen's PremiumCard, this pill has no
                // "HomeStock Premium" title alongside it to say what's active, so
                // more_premium_active ("Actief") alone would read as unclear here.
                text = stringResource(R.string.premium_title),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * [lastActiveAt] is the most recent activity-log timestamp matched to this member's display
 * name (see [HouseholdSettingsScreen]'s `lastActiveByName`) — null for a member who hasn't
 * logged any activity yet (e.g. joined but hasn't scanned/added anything) or has no name set
 * at all, in which case nothing extra is shown, same as before this existed.
 */
@Composable
private fun HouseholdMemberRow(member: HouseholdMember, lastActiveAt: Long?) {
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
            if (lastActiveAt != null) {
                Text(
                    text = lastActiveLabel(lastActiveAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Visible to the whole household — the filter button this app used to offer here to
            // set your own allergens is gone (removed per explicit request), but a member who
            // already had some set before that removal still shows them, so the household can
            // still see at a glance what to avoid when picking recipes together.
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

/** "Nu actief" / "12 min. geleden" / "3 uur geleden" / … — coarsens as the gap grows, since
 *  precision stops being useful (and starts looking odd, e.g. "127 uur geleden") past a
 *  certain point; caps out at weeks, plenty granular for "does anyone still use this app". */
@Composable
private fun lastActiveLabel(timestampMillis: Long): String {
    val minutes = (System.currentTimeMillis() - timestampMillis) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.household_last_active_now)
        minutes < 60 -> stringResource(R.string.household_last_active_minutes_format, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.household_last_active_hours_format, (minutes / 60).toInt())
        minutes < 60 * 24 * 7 -> stringResource(R.string.household_last_active_days_format, (minutes / (60 * 24)).toInt())
        else -> stringResource(R.string.household_last_active_weeks_format, (minutes / (60 * 24 * 7)).toInt())
    }
}

