package com.dtraas.homestock.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.model.ActivityType
import com.dtraas.homestock.data.model.DeveloperNotice
import com.dtraas.homestock.data.repository.HouseholdMember
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
fun NotificationsScreen(onBack: () -> Unit, onNavigateToProduct: (String) -> Unit = {}) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: NotificationsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                NotificationsViewModel(
                    application.container.activityLogRepository,
                    application.container.inventoryRepository,
                    application.container.householdMembersRepository,
                    application.container.dismissedNoticesStore,
                )
            }
        },
    )
    val developerNotices by viewModel.developerNotices.collectAsState()
    val appActivity by viewModel.appActivity.collectAsState()
    val members by viewModel.members.collectAsState()
    val urgentItem by viewModel.urgentItem.collectAsState()
    val filter by viewModel.filter.collectAsState()

    // Opening this screen (any filter) is what the unread badge on Voorraad's Activiteit icon
    // counts — same "what's new" inbox semantics as before, just no longer tied to a specific
    // tab index now that tabs are gone.
    LaunchedEffect(Unit) { viewModel.markNoticesSeen() }

    Scaffold(
        // NotificationsHeader below already claims the status bar inset itself — without this,
        // Scaffold's default contentWindowInsets (safeDrawing, top included since there's no
        // topBar) hands that same inset to `padding` too, stacking a second status-bar-height
        // gap above the header instead of it starting flush at the true top of the screen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Terugknop/titel + de filterchips zaten voorheen in een platte HomeStockTopAppBar
            // met de chips los daaronder op wit; ze verhuizen hier naar dezelfde groene
            // gradient-header als de andere herbouwde schermen, zoals artboard 1i in het
            // geuploade Claude Design-canvas laat zien. "Alles" is op verzoek geschrapt — twee
            // chips blijven over, Huishouden en Meldingen (het oude "Tips").
            NotificationsHeader(
                onBack = onBack,
                filter = filter,
                onFilterChange = viewModel::onFilterChange,
            )

            if (filter == ActivityFilter.TIPS) {
                if (developerNotices.isEmpty()) {
                    EmptyState(stringResource(R.string.notifications_history_empty))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        items(developerNotices, key = { it.id }) { notice ->
                            DeveloperNoticeRow(
                                notice = notice,
                                onDismiss = { viewModel.dismissNotice(notice.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            } else {
                if (appActivity.isEmpty() && urgentItem == null) {
                    EmptyState(stringResource(R.string.notifications_history_empty))
                } else {
                    // Huishouden is nu de enige "hoofd"-weergave (Alles bestaat niet meer), dus
                    // de teaser naar Meldingen hoort hier thuis in plaats van achter een aparte
                    // Alles-modus.
                    val showTipsTeaser = filter == ActivityFilter.HOUSEHOLD && developerNotices.isNotEmpty()
                    ActivityTimeline(
                        urgentItem = urgentItem,
                        activity = appActivity,
                        members = members,
                        showTipsTeaser = showTipsTeaser,
                        tipsCount = developerNotices.size,
                        onNavigateToProduct = onNavigateToProduct,
                        onTipsTeaserClick = { viewModel.onFilterChange(ActivityFilter.TIPS) },
                    )
                }
            }
        }
    }
}

/**
 * The fixed (non-scrolling) green gradient header — back button + title row, then the two
 * filter chips (Huishouden / Meldingen). Replaces the old flat HomeStockTopAppBar with the chips
 * on plain white background underneath it.
 */
@Composable
private fun NotificationsHeader(
    onBack: () -> Unit,
    filter: ActivityFilter,
    onFilterChange: (ActivityFilter) -> Unit,
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
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-12).dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = contentColor)
            }
            Text(
                text = stringResource(R.string.nav_news),
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotificationsFilterChip(
                label = stringResource(R.string.notifications_tab_history),
                selected = filter == ActivityFilter.HOUSEHOLD,
                onClick = { onFilterChange(ActivityFilter.HOUSEHOLD) },
            )
            NotificationsFilterChip(
                label = stringResource(R.string.notifications_tab_notices),
                selected = filter == ActivityFilter.TIPS,
                onClick = { onFilterChange(ActivityFilter.TIPS) },
            )
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
 * The merged "Alles"/"Huishouden" view: an optional urgent card at the top, the household
 * activity log grouped under date-eyebrow headers ("VANDAAG", "GISTEREN", …), and — only on
 * "Alles" — one collapsed teaser row for developer tips at the very end, rather than
 * interleaving individual tips into a dated timeline they don't actually have real dates for.
 */
@Composable
private fun ActivityTimeline(
    urgentItem: InventoryItemWithProduct?,
    activity: List<ActivityLogWithProduct>,
    members: List<HouseholdMember>,
    showTipsTeaser: Boolean,
    tipsCount: Int,
    onNavigateToProduct: (String) -> Unit,
    onTipsTeaserClick: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (urgentItem != null) {
            item(key = "urgent") {
                UrgentCard(item = urgentItem, today = today, onClick = { onNavigateToProduct(urgentItem.barcode) })
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
            HouseholdActivityRow(entry = entry, photoUrl = members.photoUrlFor(entry.actorName), entryDate = entryDate, today = today)
        }

        if (showTipsTeaser) {
            item(key = "tips_teaser") {
                TipsTeaserRow(count = tipsCount, onClick = onTipsTeaserClick, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

/** Coral card for the single soonest-expiring item (today or tomorrow only — see
 *  NotificationsViewModel.urgentItem), with a "Bekijk" action straight to that product. */
@Composable
private fun UrgentCard(item: InventoryItemWithProduct, today: LocalDate, onClick: () -> Unit) {
    val expirationDate = remember(item.expirationDate) {
        Instant.ofEpochMilli(item.expirationDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val message = if (expirationDate == today) {
        stringResource(R.string.notifications_urgent_expiring_today_format, item.name)
    } else {
        stringResource(R.string.notifications_urgent_expiring_tomorrow_format, item.name)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text(stringResource(R.string.notifications_urgent_action))
            }
        }
    }
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

/** A household event: a 34dp member avatar (photo, or a fallback icon when the best-effort
 *  name match in [NotificationsViewModel.members] finds nothing) at the far left of the row,
 *  then exactly two lines — "<naam> <actie>" (the product name folded into the action text,
 *  bold) and "<dag> <tijdstip>" — matching the design review's mockup format. No background of
 *  its own, per the same review ("activiteit meldingen hoeven ook geen aparte achtergrondkleur
 *  te hebben"). */
@Composable
private fun HouseholdActivityRow(entry: ActivityLogWithProduct, photoUrl: String?, entryDate: LocalDate, today: LocalDate) {
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                // photoLoadFailed catches a URL that exists but doesn't actually load (deleted
                // Storage object, a stale/broken download URL, no network) — without this, that
                // case rendered a blank circle instead of falling back to the activity icon the
                // way a genuinely missing photoUrl already does below.
                var photoLoadFailed by remember(photoUrl) { mutableStateOf(false) }
                if (photoUrl != null && !photoLoadFailed) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onState = { state -> photoLoadFailed = state is AsyncImagePainter.State.Error },
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
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

/** Collapsed teaser for developer tips, shown at the end of "Alles" only — tapping it switches
 *  straight to the Tips filter chip instead of interleaving each tip into the dated timeline
 *  above, which (unlike household events) has no real per-tip date to group under. */
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
                Text(stringResource(R.string.notifications_tab_notices), style = MaterialTheme.typography.titleSmall)
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
