package com.dtraas.homestock.ui.household

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.dtraas.homestock.ui.components.dashedBorder
import com.dtraas.homestock.ui.components.initialsOf
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.data.repository.HouseholdCapacityInfo
import com.dtraas.homestock.data.repository.HouseholdInviteLink
import com.dtraas.homestock.data.repository.HouseholdJoinResult
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.data.repository.RecentHousehold
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SoftCardShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.TopAppBarContainerGradientEnd
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
 * in obscured the actual save action for a rename (see [NameCard]'s doc).
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
    val householdCreatedBy by householdRepository.observeHouseholdCreatedBy().collectAsState(initial = null)
    val householdMembersRepository = application.container.householdMembersRepository
    val members by householdMembersRepository.observeMembers().collectAsState(initial = emptyList())
    val capacityInfo by householdMembersRepository.observeCapacityInfo().collectAsState(
        initial = HouseholdCapacityInfo(memberCount = 0, limit = HouseholdMembersRepository.FREE_MEMBER_LIMIT, isPremium = false),
    )
    val isPremium by householdMembersRepository.observeHouseholdIsPremium().collectAsState(initial = false)
    // Matched by exact display name, same reasoning as HouseholdMembersRepository.photoUrlFor —
    // activity log entries only ever stamp a plain name, not a uid, so that's the only join key
    // available here too.
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
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteSuccessMessage = stringResource(R.string.more_delete_household_success)
    val deleteErrorMessage = stringResource(R.string.more_delete_household_error)
    val switchFullMessage = stringResource(R.string.household_join_full_error)
    val switchNotFoundMessage = stringResource(R.string.household_switch_not_found_error)
    val invitedCopiedMessage = stringResource(R.string.household_invite_copied_message)

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
            pendingExportCsv = CsvExporter.combinedToCsv(
                listOf(inventorySectionTitle to inventoryCsv, shoppingListSectionTitle to listCsv),
            )
            exportLauncher.launch("homestock-data.csv")
        }
    }

    // Re-seeds from the live household name whenever it changes (e.g. this screen reopening,
    // or a housemate renaming it on their own device) — but not on every keystroke, since
    // `key1 = householdName` only re-runs this initializer when that upstream value itself changes.
    var nameInput by remember(householdName) { mutableStateOf(householdName.orEmpty()) }
    var isEditingName by remember { mutableStateOf(false) }

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
    var removeMemberTarget by remember { mutableStateOf<HouseholdMember?>(null) }

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
                switchTarget = null
            }
        }
    }

    // There's no explicit save button for a rename any more — confirming the NAAM card's edit
    // field (see NameCard) or leaving the screen (app bar back arrow / system back gesture,
    // hence both wiring below) both save. Firestore's offline persistence queues this write even
    // without a connection, so it doesn't need to block navigating away.
    fun saveNameIfChanged() {
        val trimmed = nameInput.trim()
        val idToRename = householdId
        if (idToRename != null && trimmed.isNotBlank() && trimmed != householdName) {
            coroutineScope.launch { householdRepository.renameHousehold(idToRename, trimmed) }
        }
    }

    fun saveNameAndGoBack() {
        saveNameIfChanged()
        onBack()
    }

    BackHandler(onBack = ::saveNameAndGoBack)

    fun shareInvite() {
        val code = householdId ?: return
        // Pushes the invite's expiry back out to a fresh INVITE_VALIDITY_DAYS window before the
        // link is actually shared, so a link handed out just now is never already stale — see
        // HouseholdRepository.refreshInviteExpiry.
        coroutineScope.launch {
            householdRepository.refreshInviteExpiry(code)
            val message = context.getString(R.string.household_share_invite_text_format, HouseholdInviteLink.build(code), code)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        }
    }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InviteCard(
                householdCode = householdId,
                inviteExpiresAt = inviteExpiresAt,
                onShare = ::shareInvite,
                onCopy = {
                    val code = householdId ?: return@InviteCard
                    clipboardManager.setText(AnnotatedString(HouseholdInviteLink.build(code)))
                    coroutineScope.launch { snackbarHostState.showSnackbar(invitedCopiedMessage, duration = SnackbarDuration.Short) }
                },
            )

            NameCard(
                name = householdName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_default_name),
                isPremium = isPremium,
                isEditing = isEditingName,
                nameInput = nameInput,
                onNameInputChange = { if (it.length <= HouseholdRepository.HOUSEHOLD_NAME_MAX_LENGTH) nameInput = it },
                onStartEdit = { isEditingName = true },
                onConfirmEdit = {
                    isEditingName = false
                    saveNameIfChanged()
                },
            )

            MembersCard(
                members = members,
                capacityInfo = capacityInfo,
                createdByUid = householdCreatedBy,
                lastActiveByName = lastActiveByName,
                onRemoveClick = { removeMemberTarget = it },
                onInviteMoreClick = ::shareInvite,
            )

            if (otherHouseholds.isNotEmpty()) {
                SwitchHouseholdSection(
                    households = otherHouseholds,
                    isSwitching = isSwitching,
                    onHouseholdClick = { switchTarget = it },
                    onForgetClick = { householdSession.forgetHousehold(it.id) },
                )
            }

            DangerZoneSection(
                isDeleting = isDeleting,
                onLeaveClick = { showLeaveConfirm = true },
                onDeleteClick = { showDeleteConfirm = true },
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
                // Stays open with a spinner while the switch is in flight — it used to close
                // immediately on tap, so a slow join (or the offline case) gave no feedback at
                // all until the snackbar showed up seconds later.
                TextButton(
                    enabled = !isSwitching,
                    onClick = { switchToHousehold(target) },
                ) {
                    if (isSwitching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.household_switch_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isSwitching, onClick = { switchTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    removeMemberTarget?.let { target ->
        val targetName = target.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.more_household_member_unnamed)
        AlertDialog(
            onDismissRequest = { removeMemberTarget = null },
            title = { Text(stringResource(R.string.household_remove_member_dialog_title_format, targetName)) },
            text = { Text(stringResource(R.string.household_remove_member_dialog_text_format, targetName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeMemberTarget = null
                        coroutineScope.launch { householdMembersRepository.removeMember(target.uid) }
                    },
                ) { Text(stringResource(R.string.household_remove_member_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeMemberTarget = null }) { Text(stringResource(R.string.common_cancel)) }
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

// A plain day-month is enough here — this is a soft "share again soon" nudge, not a
// precise-to-the-minute deadline, so neither the hour nor the year need to show.
private val inviteExpiryDateFormatter = SimpleDateFormat("d MMMM", Locale.getDefault())

/**
 * The always-visible way to add someone — was a header icon-button (share) plus a low-key code
 * footer at the very bottom of the screen; now its own card, first in the list, since inviting
 * more people is the single most useful thing to do from this screen for most households. Dark
 * green ([TopAppBarContainerGradientEnd], same token [com.dtraas.homestock.ui.more.PremiumCard]
 * reuses) so it reads as the one promoted action on the page rather than a card among cards.
 * [InviteQrCode] lets a second device join by pointing its camera at this screen instead of
 * typing the code by hand.
 */
@Composable
private fun InviteCard(
    householdCode: String?,
    inviteExpiresAt: Long?,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TopAppBarContainerGradientEnd),
        shape = SoftCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetEyebrow(text = stringResource(R.string.household_invite_card_eyebrow), color = OnTopAppBarContainerAccent)
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = SoftCardShapeCompact, color = Color.White, modifier = Modifier.size(72.dp)) {
                    if (householdCode != null) {
                        InviteQrCode(content = HouseholdInviteLink.build(householdCode), modifier = Modifier.padding(8.dp).fillMaxSize())
                    }
                }
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        text = householdCode ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    // Only shown once an invite link has actually been shared at least once (see
                    // HouseholdRepository.observeInviteExpiresAt's doc) — a household that's
                    // never used "Uitnodiging delen" has nothing to show here.
                    if (inviteExpiresAt != null) {
                        val isExpired = inviteExpiresAt < System.currentTimeMillis()
                        Text(
                            text = if (isExpired) {
                                stringResource(R.string.household_invite_expired)
                            } else {
                                stringResource(R.string.household_invite_valid_until_format, inviteExpiryDateFormatter.format(Date(inviteExpiresAt)))
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isExpired) MaterialTheme.colorScheme.errorContainer else OnTopAppBarContainerAccent,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onShare,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.household_invite_share_action), modifier = Modifier.padding(start = 8.dp))
                }
                Surface(
                    onClick = onCopy,
                    shape = SoftCardShapeCompact,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.household_invite_copy_cd),
                        tint = Color.White,
                        modifier = Modifier.padding(14.dp).size(18.dp),
                    )
                }
            }
        }
    }
}

/** Renders [content] (the invite URL) as a scannable QR bitmap via zxing-core's plain Java
 *  encoder — no camera/Android APIs involved, just a matrix of black/white pixels, so this is a
 *  synchronous, main-thread-safe [remember] rather than something needing a coroutine. */
@Composable
private fun InviteQrCode(content: String, modifier: Modifier = Modifier) {
    // encode() throws a checked WriterException in Java for content it can't fit/represent —
    // shouldn't happen for a short homestock:// URL, but the code itself (right below the QR in
    // InviteCard) is still there as a fallback either way, so a null bitmap here just means no
    // image renders rather than a crash.
    val qrBitmap = remember(content) {
        runCatching {
            val size = 240
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        }.getOrNull()
    }
    if (qrBitmap != null) {
        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
    }
}

/**
 * The household's display name — a bold read-only row with a "Opgeslagen" checkmark by default
 * (tapping it opens the [OutlinedTextField] to actually rename it), rather than an always-visible
 * text field. There's still no explicit save button: confirming (the checkmark button while
 * editing) saves immediately, and simply leaving the screen without confirming still saves too
 * (see [HouseholdSettingsScreen]'s `saveNameAndGoBack`) — a stray edit is never silently lost.
 */
@Composable
private fun NameCard(
    name: String,
    isPremium: Boolean,
    isEditing: Boolean,
    nameInput: String,
    onNameInputChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onConfirmEdit: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SheetEyebrow(text = stringResource(R.string.household_name_label), modifier = Modifier.weight(1f))
            if (isPremium) PremiumBadge()
        }
        if (isEditing) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = onNameInputChange,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onConfirmEdit) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_save))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onStartEdit),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.household_name_saved_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * Every member's row, the free-tier capacity ("2 van 3 · Premium = onbeperkt" — omitted entirely
 * once Premium lifts the cap, see [HouseholdCapacityInfo.limit]'s doc), and — while a slot is
 * still free — a dashed "Nog N plekken vrij" row that shares the invite the same way the card
 * above does, so filling the household doesn't require scrolling back up.
 */
@Composable
private fun MembersCard(
    members: List<HouseholdMember>,
    capacityInfo: HouseholdCapacityInfo,
    createdByUid: String?,
    lastActiveByName: Map<String?, Long>,
    onRemoveClick: (HouseholdMember) -> Unit,
    onInviteMoreClick: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SheetEyebrow(text = stringResource(R.string.more_household_members_title), modifier = Modifier.weight(1f))
            if (capacityInfo.limit != null) {
                Text(
                    text = stringResource(R.string.household_capacity_trailing_format, capacityInfo.memberCount, capacityInfo.limit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            members.forEach { member ->
                MemberRow(
                    member = member,
                    isOwner = createdByUid != null && member.uid == createdByUid,
                    lastActiveAt = lastActiveByName[member.displayName],
                    onRemoveClick = { onRemoveClick(member) },
                )
            }
            val remaining = capacityInfo.limit?.let { limit -> (limit - capacityInfo.memberCount).coerceAtLeast(0) }
            if (remaining != null && remaining > 0) {
                InviteMoreRow(remaining = remaining, onClick = onInviteMoreClick)
            }
        }
    }
}

/**
 * [lastActiveAt] is the most recent activity-log timestamp matched to this member's display
 * name (see [HouseholdSettingsScreen]'s `lastActiveByName`) — null for a member who hasn't
 * logged any activity yet (e.g. joined but hasn't scanned/added anything) or has no name set
 * at all, in which case nothing extra is shown. The "⋮" overflow (remove from household) only
 * ever shows on a *different* member's row — removing yourself is "Huishouden verlaten" instead
 * (see [DangerZoneSection]), which also lets this device forget its own membership locally.
 */
@Composable
private fun MemberRow(member: HouseholdMember, isOwner: Boolean, lastActiveAt: Long?, onRemoveClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val trimmedName = member.displayName?.takeIf { it.isNotBlank() }
        Surface(
            shape = CircleShape,
            color = if (member.isCurrentDevice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (member.photoUrl != null) {
                    AsyncImage(
                        model = member.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (trimmedName != null) {
                    Text(
                        text = initialsOf(trimmedName),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (member.isCurrentDevice) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trimmedName ?: stringResource(R.string.more_household_member_unnamed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                val badgeParts = buildList {
                    if (member.isCurrentDevice) add(stringResource(R.string.more_household_member_you))
                    if (isOwner) add(stringResource(R.string.household_owner_badge))
                }
                if (badgeParts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = badgeParts.joinToString(" · ") { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
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
        if (!member.isCurrentDevice) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.household_remove_member_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.household_remove_member_cd)) },
                        leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onRemoveClick() },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteMoreRow(remaining: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(MaterialTheme.colorScheme.outlineVariant, cornerRadius = 12.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.household_slots_remaining_format, remaining, remaining),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
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
        SheetEyebrow(text = stringResource(R.string.household_switch_section_title))
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
 * "Huishouden verlaten"/"Huishouden verwijderen", each its own labeled row rather than two small
 * icon buttons side by side — the danger tier gets its own "Gevoelig" eyebrow, separate from
 * [MembersCard], and verwijderen carries a light error-tinted background so the danger reads at
 * a glance instead of living only in [DeleteHouseholdSheet]'s copy (2026-08 dialog review).
 */
@Composable
private fun DangerZoneSection(isDeleting: Boolean, onLeaveClick: () -> Unit, onDeleteClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetEyebrow(text = stringResource(R.string.household_danger_zone_eyebrow), modifier = Modifier.padding(start = 4.dp))
        DangerRow(
            icon = Icons.AutoMirrored.Filled.Logout,
            title = stringResource(R.string.more_leave),
            subtitle = stringResource(R.string.household_leave_subtitle),
            titleColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            enabled = !isDeleting,
            onClick = onLeaveClick,
        )
        DangerRow(
            icon = Icons.Filled.DeleteForever,
            title = stringResource(R.string.more_delete_household),
            subtitle = stringResource(R.string.household_delete_subtitle),
            titleColor = MaterialTheme.colorScheme.error,
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            enabled = !isDeleting,
            onClick = onDeleteClick,
        )
    }
}

@Composable
private fun DangerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color,
    containerColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = SoftCardShapeCompact,
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = titleColor)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
