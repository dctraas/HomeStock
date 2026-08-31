package com.dtraas.homestock.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.model.ActivityType
import com.dtraas.homestock.data.model.DeveloperNotice
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.ui.components.HomeStockBottomSheet
import com.dtraas.homestock.ui.components.SheetTitle
import com.dtraas.homestock.ui.components.sheetContentPadding
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor
import com.dtraas.homestock.ui.theme.OnTopAppBarContainerAccent
import com.dtraas.homestock.ui.theme.SageGreenPrimary
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: NotificationsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                NotificationsViewModel(
                    application.container.activityLogRepository,
                    application.container.householdMembersRepository,
                    application.container.dismissedNoticesStore,
                    application.container.activityReadStore,
                )
            }
        },
    )
    val developerNotices by viewModel.developerNotices.collectAsState()
    val appActivity by viewModel.filteredActivity.collectAsState()
    val members by viewModel.members.collectAsState()
    val selectedMemberUid by viewModel.selectedMemberUid.collectAsState()
    val lastActivitySeenAt by viewModel.lastActivitySeenAt.collectAsState()
    val unreadActivityCount by viewModel.unreadActivityCount.collectAsState()

    // Meldingen (het oude "Tips"-tabblad) is geen aparte modus meer — het leeft nu als de
    // "Berichten van HomeStock"-rij onderaan de tijdlijn, die deze sheet opent in plaats van het
    // hele scherm te vervangen.
    var showTipsSheet by remember { mutableStateOf(false) }

    // Opening this screen is what the unread badge on Voorraad's Meldingen icon counts — separate
    // from [lastActivitySeenAt] below, which only the banner's own "Markeer gelezen" action moves.
    LaunchedEffect(Unit) { viewModel.markNoticesSeen() }

    Scaffold(
        // NotificationsHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Terugknop/titel + filterchips zaten voorheen in een platte HomeStockTopAppBar met
            // de chips los daaronder op wit; ze verhuizen hier naar dezelfde groene
            // gradient-header als de andere herbouwde schermen. De Huishouden/Meldingen-chips
            // zijn vervangen door "Iedereen" + één chip per huisgenoot.
            NotificationsHeader(
                onBack = onBack,
                members = members,
                selectedMemberUid = selectedMemberUid,
                onSelectMember = viewModel::onMemberFilterChange,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (appActivity.isEmpty()) {
                    EmptyState(stringResource(R.string.notifications_history_empty))
                } else {
                    ActivityTimeline(
                        activity = appActivity,
                        lastSeenAt = lastActivitySeenAt,
                        unreadCount = unreadActivityCount,
                        onMarkSeen = viewModel::markActivitySeen,
                    )
                }
            }

            // Pinned below the (independently scrolling, weight(1f)'d) timeline above rather
            // than as its own last LazyColumn item — a message *from the app itself* stays put
            // as the household scrolls through their own activity, the same way the header stays
            // put above it. Shown whenever there's at least one notice, regardless of whether the
            // timeline itself has anything in it (previously tied to ActivityTimeline's own
            // content, which meant an empty activity log hid this row too, even with notices
            // waiting).
            if (developerNotices.isNotEmpty()) {
                TipsTeaserRow(
                    count = developerNotices.size,
                    onClick = { showTipsSheet = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    if (showTipsSheet) {
        TipsSheet(
            notices = developerNotices,
            onDismissNotice = viewModel::dismissNotice,
            onDismiss = { showTipsSheet = false },
        )
    }
}

/**
 * The fixed (non-scrolling) green gradient header — back button + title row, then a horizontally
 * scrollable row of member filter chips ("Iedereen" plus one per household member). Replaces the
 * old Huishouden/Meldingen tab pair — Meldingen now lives as the "Berichten van HomeStock" teaser
 * at the bottom of the timeline, see [TipsTeaserRow].
 */
@Composable
private fun NotificationsHeader(
    onBack: () -> Unit,
    members: List<HouseholdMember>,
    selectedMemberUid: String?,
    onSelectMember: (String?) -> Unit,
) {
    val contentColor = LocalTopAppBarContentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalTopAppBarContainerColor.current)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = contentColor)
            }
            Text(
                text = stringResource(R.string.nav_news),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
            )
        }
        // Solo households have nothing to narrow down — the row only earns its place once
        // there's more than one member to filter by.
        if (members.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NotificationsFilterChip(
                    label = stringResource(R.string.notifications_member_filter_everyone),
                    selected = selectedMemberUid == null,
                    onClick = { onSelectMember(null) },
                )
                members.forEach { member ->
                    // First name only, per the mockup ("Dennis"/"Marit") — the actual filter
                    // match in the ViewModel still keys off the member's full display name.
                    val label = member.displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
                        ?: return@forEach
                    NotificationsFilterChip(
                        label = label,
                        selected = selectedMemberUid == member.uid,
                        onClick = { onSelectMember(member.uid) },
                    )
                }
            }
        }
    }
}

/** A filter chip styled for the green header — a solid white pill when selected, a translucent
 *  white pill otherwise — same treatment as the dagstrip/day-cards on the other rebuilt headers
 *  this round, rather than [FilterChip]'s default Material colors which assume a light surface. */
@Composable
private fun NotificationsFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = SoftCardShapeCompact,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.14f),
            labelColor = OnTopAppBarContainerAccent,
            selectedContainerColor = Color.White,
            selectedLabelColor = SageGreenPrimary,
        ),
        border = null,
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val dayHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
private val timeOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@Composable
private fun dayHeaderLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.notifications_date_today)
    today.minusDays(1) -> stringResource(R.string.notifications_date_yesterday)
    else -> dayHeaderFormatter.format(date)
}

/**
 * The main (and now only) timeline view: an unread banner when there's anything new since
 * [lastSeenAt], and the household activity log grouped under date-eyebrow headers ("VANDAAG",
 * "GISTEREN", …) with an unread dot per row. The developer-tips teaser row used to be the last
 * item here; it's now pinned below this whole timeline instead (see [NotificationsScreen]), so
 * it isn't part of this list any more. Used to also open with an "urgent" expiring-item card —
 * removed per explicit request, expiry nudges belong in Voorraad, not in the household activity
 * feed (see [com.dtraas.homestock.ui.inventory.InventoryScreen]'s "Eerst opmaken" card instead).
 */
@Composable
private fun ActivityTimeline(
    activity: List<ActivityLogWithProduct>,
    lastSeenAt: Long,
    unreadCount: Int,
    onMarkSeen: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (unreadCount > 0) {
            item(key = "unread_banner") {
                UnreadBanner(
                    count = unreadCount,
                    since = lastSeenAt,
                    today = today,
                    onMarkSeen = onMarkSeen,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        var lastDate: LocalDate? = null
        items(activity, key = { it.id }) { entry ->
            val entryDate = remember(entry.timestamp) {
                Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            if (entryDate != lastDate) {
                lastDate = entryDate
                Text(
                    text = dayHeaderLabel(entryDate, today),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (entryDate == activity.first().let {
                        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                    }) 0.dp else 16.dp, bottom = 6.dp),
                )
            }
            HouseholdActivityRow(
                entry = entry,
                entryDate = entryDate,
                today = today,
                isUnread = entry.timestamp > lastSeenAt,
            )
        }
    }
}

/** "N wijzigingen sinds [dag] [tijd] · Markeer gelezen" — shown only while [count] (see
 *  [com.dtraas.homestock.ui.notifications.NotificationsViewModel.unreadActivityCount]) is above
 *  zero; tapping the action calls [onMarkSeen], which is what makes it (and every row's own
 *  unread dot) disappear. */
@Composable
private fun UnreadBanner(count: Int, since: Long, today: LocalDate, onMarkSeen: () -> Unit, modifier: Modifier = Modifier) {
    val sinceDate = remember(since) { Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()).toLocalDate() }
    val sinceDay = dayHeaderLabel(sinceDate, today)
    val sinceTime = remember(since) { timeOnlyFormatter.format(Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault())) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.notifications_unread_banner_format, count, count, sinceDay, sinceTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMarkSeen) {
                Text(stringResource(R.string.notifications_mark_read_action))
            }
        }
    }
}

/** "Dennis Traas" -> "DT", "Marieke" -> "MA" — first letter of each of the first two words, or
 *  (a single-word name has no second word to draw from) the first two letters of that one word
 *  instead. Always uppercased, always 2 characters for any non-blank [name]. */
private fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> ""
    }.uppercase()
}

private fun activityIcon(type: ActivityType): ImageVector = when (type) {
    ActivityType.SCANNED -> Icons.Filled.QrCodeScanner
    ActivityType.QUANTITY_CHANGED -> Icons.Filled.Tune
    ActivityType.REMOVED -> Icons.Filled.Delete
    ActivityType.ADDED_TO_SHOPPING_LIST -> Icons.Filled.AddShoppingCart
    // Distinct from plain REMOVED so a wasted-food entry reads differently at a glance in the
    // activity log too, matching how Statistics already tells "verspild" apart from ordinary
    // consumption (see ActivityType.WASTED's own doc).
    ActivityType.WASTED -> Icons.Filled.DeleteSweep
}

/** A household event: an unread dot (only while [isUnread]), a 34dp member avatar (that actor's
 *  initials, e.g. "Dennis Traas" -> "DT" and "Marieke" -> "MA" — see [initialsFor] — or a
 *  fallback icon when there's no actor name at all), then exactly two lines — "<naam> <actie>"
 *  (the product name folded into the action text, bold) and "<dag> <tijdstip>" — matching the
 *  design review's mockup format. No background of its own, per the same review ("activiteit
 *  meldingen hoeven ook geen aparte achtergrondkleur te hebben"). */
@Composable
private fun HouseholdActivityRow(entry: ActivityLogWithProduct, entryDate: LocalDate, today: LocalDate, isUnread: Boolean) {
    val type = ActivityType.fromStorageKey(entry.type)
    val time = remember(entry.timestamp) {
        timeOnlyFormatter.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()))
    }
    val day = dayHeaderLabel(entryDate, today)
    val actorLabel = entry.actorName ?: stringResource(R.string.activity_actor_unknown)
    // "<naam> heeft <product> <actie>." — every activity_detail_* string is already a past
    // participle (see that string's own doc), so the localized connector always fits.
    val connector = stringResource(R.string.activity_action_connector)
    val actionText = buildAnnotatedString {
        append(actorLabel)
        append(" ")
        append(connector)
        append(" ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(entry.productName) }
        append(" ")
        append(entry.detail.replaceFirstChar { it.lowercase() })
        append(".")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed-width slot whether or not the dot itself renders, so every row's avatar lines up
        // in the same column regardless of read state.
        Box(modifier = Modifier.size(8.dp), contentAlignment = Alignment.Center) {
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                val initials = entry.actorName?.trim()?.takeIf { it.isNotEmpty() }?.let { initialsFor(it) }
                if (initials != null) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = activityIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = actionText, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$day $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Collapsed teaser for developer tips, always shown once any exist — tapping it opens
 *  [TipsSheet] instead of interleaving each tip into the dated timeline, which (unlike household
 *  events) has no real per-tip date to group under. Used to be behind its own "Meldingen" tab
 *  chip; now it's this row's only entry point, styled as a message from the app itself
 *  ("Berichten") rather than a settings-y tab label. Pinned below the scrolling timeline (see
 *  [NotificationsScreen]) rather than living inside it, so it stays on screen the same way the
 *  header above the timeline does. */
@Composable
private fun TipsTeaserRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.notifications_tips_teaser_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = pluralStringResource(R.plurals.notifications_tips_teaser_subtitle_format, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The Meldingen list (developer tips), now opened from [TipsTeaserRow] as a bottom sheet
 *  instead of swapping the whole screen into a second "tab" — the same swipe-to-dismiss
 *  [DeveloperNoticeRow]s as before, just presented over the timeline rather than replacing it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipsSheet(notices: List<DeveloperNotice>, onDismissNotice: (String) -> Unit, onDismiss: () -> Unit) {
    HomeStockBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(sheetContentPadding)) {
            SheetTitle(title = stringResource(R.string.notifications_tab_notices))
            if (notices.isEmpty()) {
                // Rare — the teaser row that opens this sheet only shows up while there's at
                // least one notice, so this only triggers if the last one gets dismissed while
                // the sheet is already open.
                Text(
                    text = stringResource(R.string.notifications_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column {
                    // Keyed by notice.id — without this, a dismissed row's positional slot (and
                    // its own remembered SwipeToDismissBoxState) got reused for whichever notice
                    // slid up to take its place, so that *other* notice inherited the just-
                    // dismissed swipe state and rendered with a permanently red background it
                    // never actually earned ("een lelijk rood vlak blijft staan").
                    notices.forEach { notice ->
                        key(notice.id) {
                            DeveloperNoticeRow(notice = notice, onDismiss = { onDismissNotice(notice.id) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperNoticeRow(notice: DeveloperNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDismiss()
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        // No trash icon any more — the colored reveal alone is the swipe-to-delete feedback,
        // per "het prullenbakicoontje mag ook weg, verwijderen kun je doen door te swipen".
        // Transparent at rest, same fix as ShoppingListRow/ShoppingListGridTile's own swipe
        // backgrounds — SwipeToDismissBox always composes backgroundContent regardless of swipe
        // state, so without this check every item in the Meldingen tab sat on a permanently
        // visible errorContainer (red) background instead of only showing it during an active
        // swipe ("elk meldingen item heeft een rode achtergrond, graag zonder").
        backgroundContent = {
            val isSettled = dismissState.dismissDirection == SwipeToDismissBoxValue.Settled
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(SoftCardShapeCompact)
                    .background(if (isSettled) Color.Transparent else MaterialTheme.colorScheme.errorContainer),
            )
        },
    ) {
        // No card background of its own — "activiteit meldingen hoeven ook geen aparte
        // achtergrondkleur te hebben. Dit geldt voor zowel tabblad Huishouden als Meldingen" —
        // Card stays only as the SwipeToDismissBox content slot, transparent and shadowless.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = SoftCardShapeCompact,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = SoftBadgeShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.Campaign,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(stringResource(notice.titleRes), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(notice.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
