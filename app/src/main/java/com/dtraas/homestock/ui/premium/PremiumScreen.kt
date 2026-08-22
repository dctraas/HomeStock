package com.dtraas.homestock.ui.premium

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.PremiumPlan
import com.dtraas.homestock.data.repository.formattedRecurringPrice
import com.dtraas.homestock.data.repository.hasTrialOffer
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import java.util.Locale
import kotlinx.coroutines.launch

/** Yearly's savings badge vs. paying the monthly price 12 times over — display-only math from
 *  both offers' raw micros (Play's own formatted price strings can't be arithmetic'd on
 *  directly), so it silently disappears rather than showing something wrong if either plan's
 *  pricing isn't loaded yet, or the two ever end up in different currencies. */
private fun yearlySavingsPercent(monthly: ProductDetails?, yearly: ProductDetails?): Int? {
    val monthlyPhase = monthly?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return null
    val yearlyPhase = yearly?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return null
    if (monthlyPhase.priceCurrencyCode != yearlyPhase.priceCurrencyCode) return null
    val yearOfMonthly = monthlyPhase.priceAmountMicros * 12
    if (yearOfMonthly <= 0) return null
    val savings = 1.0 - (yearlyPhase.priceAmountMicros.toDouble() / yearOfMonthly.toDouble())
    return (savings * 100).toInt().takeIf { it > 0 }
}

/** Yearly's own recurring price divided by 12, formatted the same "€%.2f" way every other
 *  price display in the app already does (see e.g. ShoppingListScreen's formatPrice) — the
 *  small "≈ €X,XX p/m" line under the yearly card's headline price. Play's own pricing phase
 *  only exposes raw micros, not a pre-divided monthly figure. */
private fun ProductDetails.monthlyEquivalentPrice(): String? {
    val phase = subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return null
    val perMonth = phase.priceAmountMicros / 12.0 / 1_000_000.0
    return String.format(Locale.getDefault(), "€%.2f", perMonth)
}

private data class PremiumBenefit(val icon: ImageVector, val titleRes: Int, val bodyRes: Int)

private val premiumBenefits = listOf(
    PremiumBenefit(Icons.Filled.Restaurant, R.string.premium_benefit_recipes, R.string.premium_benefit_recipes_body),
    PremiumBenefit(Icons.Filled.ReceiptLong, R.string.premium_benefit_receipt_scan, R.string.premium_benefit_receipt_scan_body),
    PremiumBenefit(Icons.Filled.AutoAwesome, R.string.premium_benefit_ai_recognition, R.string.premium_benefit_ai_recognition_body),
    PremiumBenefit(Icons.Filled.BarChart, R.string.premium_benefit_statistics, R.string.premium_benefit_statistics_body),
    PremiumBenefit(Icons.Filled.Groups, R.string.premium_benefit_household, R.string.premium_benefit_household_body),
    PremiumBenefit(Icons.Filled.PhotoCamera, R.string.premium_benefit_custom_photo, R.string.premium_benefit_custom_photo_body),
    PremiumBenefit(Icons.Filled.ImportExport, R.string.premium_benefit_csv, R.string.premium_benefit_csv_body),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val billingRepository = application.container.billingRepository
    val householdMembersRepository = application.container.householdMembersRepository
    val remoteConfigRepository = application.container.remoteConfigRepository
    val analyticsRepository = application.container.analyticsRepository
    // Household-wide, not just this device's own purchase — a housemate who already
    // subscribed shouldn't be shown a "Subscribe" button that would charge again.
    val isPremium by householdMembersRepository.observeHouseholdIsPremium().collectAsState(initial = false)
    val productDetails by billingRepository.productDetails.collectAsState()
    val trialDays by remoteConfigRepository.trialDays.collectAsState()
    val monthlyPlanEnabled by remoteConfigRepository.monthlyPlanEnabled.collectAsState()
    val activity = LocalContext.current as? Activity
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val restoredMessage = stringResource(R.string.premium_restore_done)

    LaunchedEffect(Unit) { analyticsRepository.logPremiumScreenViewed(source = "premium_screen") }

    var selectedPlan by remember { mutableStateOf(PremiumPlan.YEARLY) }

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    if (!isPremium) {
                        TextButton(
                            onClick = {
                                analyticsRepository.logRestorePurchasesTapped()
                                coroutineScope.launch {
                                    billingRepository.refreshPurchases()
                                    snackbarHostState.showSnackbar(restoredMessage, duration = SnackbarDuration.Short)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.premium_restore_action))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Plans + CTA are pinned here, outside the scrolling benefits list below, so the
        // purchase itself is always reachable without scrolling — per the design review.
        bottomBar = {
            if (!isPremium) {
                PremiumBottomBar(
                    productDetails = productDetails,
                    trialDays = trialDays,
                    monthlyPlanEnabled = monthlyPlanEnabled,
                    selectedPlan = selectedPlan,
                    onSelectPlan = { plan ->
                        selectedPlan = plan
                        analyticsRepository.logPremiumPlanSelected(if (plan == PremiumPlan.MONTHLY) "monthly" else "yearly")
                    },
                    onSubscribe = { activity?.let { billingRepository.launchPurchaseFlow(it, selectedPlan) } },
                    activity = activity,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (isPremium) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
                    Surface(
                        shape = SoftBadgeShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(88.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.premium_active_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        text = stringResource(R.string.premium_active_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.premium_pitch_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.premium_pitch_subtitle_format, trialDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
                )
            }

            premiumBenefits.forEachIndexed { index, benefit ->
                PremiumBenefitRow(benefit = benefit, tintIndex = index)
            }

            // Bottom padding so the last benefit row doesn't sit flush against the pinned
            // bottomBar above it.
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** One of the 3 accent container tones, cycled by index — every benefit gets a tinted icon
 *  tile per the design, but there's no reason to invent 7 distinct colors when the app's own
 *  3-accent palette already provides enough variety. */
@Composable
private fun benefitTint(index: Int): Pair<Color, Color> = when (index % 3) {
    0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

@Composable
private fun PremiumBenefitRow(benefit: PremiumBenefit, tintIndex: Int) {
    val (containerColor, contentColor) = benefitTint(tintIndex)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = containerColor,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = benefit.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = stringResource(benefit.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(benefit.bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Pinned outside the scrolling benefits list (see [PremiumScreen]'s Scaffold) so the purchase
 * itself never requires scrolling: the two plan cards side by side, the subscribe CTA, and the
 * fine print — everything a person needs to actually buy, always on screen.
 */
@Composable
private fun PremiumBottomBar(
    productDetails: Map<String, ProductDetails>,
    trialDays: Long,
    monthlyPlanEnabled: Boolean,
    selectedPlan: PremiumPlan,
    onSelectPlan: (PremiumPlan) -> Unit,
    onSubscribe: () -> Unit,
    activity: Activity?,
) {
    val monthlyDetails = productDetails[PremiumPlan.MONTHLY.productId]
    val yearlyDetails = productDetails[PremiumPlan.YEARLY.productId]
    val savingsPercent = yearlySavingsPercent(monthlyDetails, yearlyDetails)
    val selectedDetails = productDetails[selectedPlan.productId]
    val showsTrial = selectedDetails?.hasTrialOffer == true

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (monthlyPlanEnabled) {
                    PlanCard(
                        label = stringResource(R.string.premium_plan_monthly),
                        priceText = monthlyDetails?.formattedRecurringPrice?.let {
                            stringResource(R.string.premium_price_per_month_format, it)
                        } ?: stringResource(R.string.premium_price_loading),
                        subText = null,
                        badgeText = trialBadgeText(monthlyDetails, trialDays),
                        selected = selectedPlan == PremiumPlan.MONTHLY,
                        onClick = { onSelectPlan(PremiumPlan.MONTHLY) },
                        modifier = Modifier.weight(1f),
                    )
                }
                PlanCard(
                    label = stringResource(R.string.premium_plan_yearly),
                    priceText = yearlyDetails?.formattedRecurringPrice?.let {
                        stringResource(R.string.premium_price_per_year_format, it)
                    } ?: stringResource(R.string.premium_price_loading),
                    subText = yearlyDetails?.monthlyEquivalentPrice()?.let {
                        stringResource(R.string.premium_plan_monthly_equivalent_format, it)
                    },
                    badgeText = trialBadgeText(yearlyDetails, trialDays)
                        ?: savingsPercent?.let { stringResource(R.string.premium_plan_savings_badge_format, it) },
                    selected = selectedPlan == PremiumPlan.YEARLY,
                    onClick = { onSelectPlan(PremiumPlan.YEARLY) },
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = onSubscribe,
                enabled = selectedDetails != null && activity != null,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
            ) {
                Text(
                    if (showsTrial) {
                        stringResource(R.string.premium_subscribe_trial_button, trialDays)
                    } else {
                        stringResource(R.string.premium_subscribe_button)
                    },
                )
            }
            Text(
                text = if (showsTrial && selectedDetails?.formattedRecurringPrice != null) {
                    val perPeriod = if (selectedPlan == PremiumPlan.YEARLY) {
                        stringResource(R.string.premium_price_per_year_format, selectedDetails.formattedRecurringPrice!!)
                    } else {
                        stringResource(R.string.premium_price_per_month_format, selectedDetails.formattedRecurringPrice!!)
                    }
                    stringResource(R.string.premium_terms_trial_format, perPeriod)
                } else {
                    stringResource(R.string.premium_terms_notice)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/** The trial badge for a subscription plan card — `null` for a plan whose loaded offer has no
 *  trial phase (or isn't loaded yet), so [PlanCard] falls back to whatever other badge (e.g.
 *  yearly's savings badge) the caller passes instead. */
@Composable
private fun trialBadgeText(details: ProductDetails?, trialDays: Long): String? =
    if (details?.hasTrialOffer == true) stringResource(R.string.premium_plan_trial_badge_format, trialDays) else null

@Composable
private fun PlanCard(
    label: String,
    priceText: String,
    subText: String?,
    badgeText: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = SoftCardShapeCompact,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.selectable(selected = selected, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(20.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = priceText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (badgeText != null) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
