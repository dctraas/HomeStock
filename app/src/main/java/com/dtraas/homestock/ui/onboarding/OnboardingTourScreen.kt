package com.dtraas.homestock.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.StringRes
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import kotlinx.coroutines.launch

/** One page of [OnboardingTourScreen] — an icon, a title and a one/two-sentence explanation of
 *  a core part of the app. Kept intentionally short: this is a first-impression overview, not
 *  a manual — every one of these features has its own, more detailed UI to discover later. */
private data class OnboardingPage(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(Icons.Filled.Home, R.string.onboarding_welcome_title, R.string.onboarding_welcome_description),
    OnboardingPage(Icons.Filled.Inventory2, R.string.onboarding_inventory_title, R.string.onboarding_inventory_description),
    OnboardingPage(Icons.Filled.ShoppingCart, R.string.onboarding_shopping_list_title, R.string.onboarding_shopping_list_description),
    OnboardingPage(Icons.Filled.QrCodeScanner, R.string.onboarding_scanning_title, R.string.onboarding_scanning_description),
    OnboardingPage(Icons.Filled.RestaurantMenu, R.string.onboarding_recipes_title, R.string.onboarding_recipes_description),
    OnboardingPage(Icons.Filled.Groups, R.string.onboarding_household_title, R.string.onboarding_household_description),
    OnboardingPage(Icons.Filled.CheckCircle, R.string.onboarding_ready_title, R.string.onboarding_ready_description),
)

/**
 * Shown once, the very first time a device lands in the main app (see
 * [com.dtraas.homestock.ui.navigation.HomeStockApp] and
 * [com.dtraas.homestock.data.repository.OnboardingTourPreferences]) — a short swipeable
 * overview of what the app actually does, since household setup itself (name/photo, create-or-
 * join) never explains any of Voorraad/Boodschappenlijst/Recepten/scannen/delen. A full-screen
 * custom [Dialog] rather than a NavHost destination: it needs to sit *on top of* the already-
 * loaded main app (so dismissing it — skip or finish, same effect — reveals a ready-to-use
 * Voorraad screen underneath) without being a real navigation entry that back-stack/deep-link
 * logic would need to account for.
 */
@Composable
fun OnboardingTourScreen(onFinish: () -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val analyticsRepository = application.container.analyticsRepository
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Dialog(
        onDismissRequest = onFinish,
        // A tour that covers the whole screen, not a centered card — usePlatformDefaultWidth
        // = false is what lets a Compose Dialog actually fill the window instead of being
        // capped to the platform's default dialog width.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    // No skip button on the last page — "Aan de slag" below already does the
                    // exact same thing, a second identical-looking exit here would be noise.
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
                    OnboardingPageContent(onboardingPages[page])
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    onboardingPages.indices.forEach { index ->
                        val isCurrent = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isCurrent) 10.dp else 8.dp)
                                .background(
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isLastPage) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_next))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = SoftBadgeShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(page.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
