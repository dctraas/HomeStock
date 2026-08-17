package com.dtraas.homestock.ui.premium

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.repository.PremiumPlan
import com.dtraas.homestock.data.repository.formattedOneTimePrice
import com.dtraas.homestock.data.repository.formattedRecurringPrice
import com.dtraas.homestock.data.repository.hasTrialOffer
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftBadgeShape
import com.dtraas.homestock.ui.theme.SoftCardShape
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
                title = { Text(stringResource(R.string.premium_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                // See HouseholdScreen/AccountLinkScreen for the same reasoning: three plan
                // cards plus everything else here doesn't reliably fit one screen with "Groot
                // lettertype" or on a small device.
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                text = stringResource(if (isPremium) R.string.premium_active_title else R.string.premium_pitch_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(if (isPremium) R.string.premium_active_subtitle else R.string.premium_pitch_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Surface(
                shape = SoftCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PremiumBenefitRow(stringResource(R.string.premium_benefit_recipes))
                    PremiumBenefitRow(stringResource(R.string.premium_benefit_receipt_scan))
                    PremiumBenefitRow(stringResource(R.string.premium_benefit_ai_recognition))
                    PremiumBenefitRow(stringResource(R.string.premium_benefit_statistics))
                    PremiumBenefitRow(stringResource(R.string.premium_benefit_csv))
                    PremiumBenefitRow(stringResource(R.string.premium_benefit_custom_photo))
                    PremiumBenefitRow(stringResource(R.string.premium_benefit_household))
                }
            }

            if (!isPremium) {
                val monthlyDetails = productDetails[PremiumPlan.MONTHLY.productId]
                val yearlyDetails = productDetails[PremiumPlan.YEARLY.productId]
                val lifetimeDetails = productDetails[PremiumPlan.LIFETIME.productId]
                val savingsPercent = yearlySavingsPercent(monthlyDetails, yearlyDetails)

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (monthlyPlanEnabled) {
                        PlanCard(
                            label = stringResource(R.string.premium_plan_monthly),
                            priceText = monthlyDetails?.formattedRecurringPrice?.let {
                                stringResource(R.string.premium_price_per_month_format, it)
                            } ?: stringResource(R.string.premium_price_loading),
                            badgeText = trialBadgeText(monthlyDetails, trialDays),
                            selected = selectedPlan == PremiumPlan.MONTHLY,
                            onClick = { selectedPlan = PremiumPlan.MONTHLY; analyticsRepository.logPremiumPlanSelected("monthly") },
                        )
                    }
                    PlanCard(
                        label = stringResource(R.string.premium_plan_yearly),
                        priceText = yearlyDetails?.formattedRecurringPrice?.let {
                            stringResource(R.string.premium_price_per_year_format, it)
                        } ?: stringResource(R.string.premium_price_loading),
                        badgeText = trialBadgeText(yearlyDetails, trialDays)
                            ?: savingsPercent?.let { stringResource(R.string.premium_plan_savings_badge_format, it) },
                        selected = selectedPlan == PremiumPlan.YEARLY,
                        onClick = { selectedPlan = PremiumPlan.YEARLY; analyticsRepository.logPremiumPlanSelected("yearly") },
                    )
                    PlanCard(
                        label = stringResource(R.string.premium_plan_lifetime),
                        priceText = lifetimeDetails?.formattedOneTimePrice ?: stringResource(R.string.premium_price_loading),
                        badgeText = stringResource(R.string.premium_plan_lifetime_badge),
                        selected = selectedPlan == PremiumPlan.LIFETIME,
                        onClick = { selectedPlan = PremiumPlan.LIFETIME; analyticsRepository.logPremiumPlanSelected("lifetime") },
                    )
                }

                val selectedDetails = productDetails[selectedPlan.productId]
                val showsTrial = selectedPlan != PremiumPlan.LIFETIME && selectedDetails?.hasTrialOffer == true
                Button(
                    onClick = { activity?.let { billingRepository.launchPurchaseFlow(it, selectedPlan) } },
                    enabled = selectedDetails != null && activity != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                ) {
                    Text(
                        when {
                            showsTrial -> stringResource(R.string.premium_subscribe_trial_button, trialDays)
                            selectedPlan == PremiumPlan.LIFETIME -> stringResource(R.string.premium_buy_lifetime_button)
                            else -> stringResource(R.string.premium_subscribe_button)
                        },
                    )
                }
                Text(
                    text = if (selectedPlan == PremiumPlan.LIFETIME) {
                        stringResource(R.string.premium_terms_notice_lifetime)
                    } else {
                        stringResource(R.string.premium_terms_notice)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(
                    onClick = {
                        analyticsRepository.logRestorePurchasesTapped()
                        coroutineScope.launch {
                            billingRepository.refreshPurchases()
                            snackbarHostState.showSnackbar(restoredMessage, duration = SnackbarDuration.Short)
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.premium_restore_action))
                }
            }
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
    badgeText: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = SoftCardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = priceText,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PremiumBenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}
