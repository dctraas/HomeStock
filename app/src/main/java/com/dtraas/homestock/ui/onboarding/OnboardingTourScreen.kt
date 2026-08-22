package com.dtraas.homestock.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.HouseholdInviteLink
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShape
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3
private const val PAGE_WELCOME = 0
private const val PAGE_STAPLES = 1
private const val PAGE_INVITE = 2

private val staples = StapleId.entries

/**
 * Shown once, the very first time a device lands in the main app (see
 * [com.dtraas.homestock.ui.navigation.HomeStockApp] and
 * [com.dtraas.homestock.data.repository.OnboardingTourPreferences]) — three short "do" steps
 * instead of a slideshow of explanations: a welcome beat, then two real actions (seed the
 * inventory with a few staples, invite a housemate) that leave a genuinely further-along app
 * behind once it's dismissed. A full-screen custom [Dialog] rather than a NavHost destination:
 * it needs to sit *on top of* the already-loaded main app (so dismissing it — skip or finish,
 * same effect — reveals a ready-to-use Voorraad screen underneath) without being a real
 * navigation entry that back-stack/deep-link logic would need to account for.
 */
@Composable
fun OnboardingTourScreen(onFinish: () -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val analyticsRepository = application.container.analyticsRepository
    val context = LocalContext.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                OnboardingViewModel(
                    productRepository = application.container.productRepository,
                    inventoryRepository = application.container.inventoryRepository,
                )
            }
        },
    )
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1
    val selectedStaples by viewModel.selectedStaples.collectAsState()
    val householdCode by application.container.householdSession.householdId.collectAsState()

    Dialog(
        onDismissRequest = onFinish,
        // A tour that covers the whole screen, not a centered card — usePlatformDefaultWidth
        // = false is what lets a Compose Dialog actually fill the window instead of being
        // capped to the platform's default dialog width.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(PAGE_COUNT) { index ->
                            Box(
                                modifier = Modifier
                                    .size(width = 28.dp, height = 4.dp)
                                    .background(
                                        color = if (index <= pagerState.currentPage) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        shape = RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                    // No skip button on the last page — the CTA below already does the exact
                    // same thing, a second identical-looking exit here would be noise.
                    if (!isLastPage) {
                        TextButton(
                            onClick = {
                                analyticsRepository.logOnboardingTourSkipped(pagerState.currentPage)
                                onFinish()
                            },
                        ) { Text(stringResource(R.string.onboarding_skip)) }
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        PAGE_WELCOME -> OnboardingWelcomePage()
                        PAGE_STAPLES -> OnboardingStaplesPage(selectedStaples = selectedStaples, onToggle = viewModel::toggleStaple)
                        PAGE_INVITE -> OnboardingInvitePage(
                            code = householdCode,
                            onShare = {
                                val message = context.getString(
                                    R.string.household_share_invite_text_format,
                                    householdCode?.let(HouseholdInviteLink::build).orEmpty(),
                                    householdCode.orEmpty(),
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

                val ctaLabel = when {
                    isLastPage -> stringResource(R.string.onboarding_get_started)
                    pagerState.currentPage == PAGE_STAPLES && selectedStaples.isNotEmpty() -> {
                        pluralStringResource(R.plurals.onboarding_staples_cta_format, selectedStaples.size, selectedStaples.size)
                    }
                    else -> stringResource(R.string.onboarding_next)
                }
                Button(
                    onClick = {
                        if (isLastPage) {
                            analyticsRepository.logOnboardingTourCompleted()
                            onFinish()
                        } else {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text(ctaLabel)
                }
                if (pagerState.currentPage == PAGE_STAPLES) {
                    Text(
                        text = stringResource(R.string.onboarding_staples_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIconBadge(Icons.Filled.Home)
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** "Wat staat er nu in je keuken?" — tapping a chip seeds that staple straight into the
 *  inventory (see [OnboardingViewModel.toggleStaple]), so this step leaves a genuinely
 *  non-empty Voorraad behind rather than just explaining that one exists. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingStaplesPage(selectedStaples: Set<StapleId>, onToggle: (StapleId, String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
        OnboardingIconBadge(Icons.Filled.Kitchen)
        Text(
            text = stringResource(R.string.onboarding_staples_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_staples_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            staples.forEach { staple ->
                val label = stringResource(staple.nameRes)
                FilterChip(
                    selected = staple in selectedStaples,
                    onClick = { onToggle(staple, label) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun OnboardingInvitePage(code: String?, onShare: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIconBadge(Icons.Filled.Groups)
        Text(
            text = stringResource(R.string.onboarding_invite_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_invite_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        if (code != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SoftCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.household_code_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = code,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.household_share_invite_cd), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingIconBadge(icon: ImageVector) {
    Surface(shape = SoftBadgeShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
