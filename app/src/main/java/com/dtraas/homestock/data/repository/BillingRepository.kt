package com.dtraas.homestock.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.dtraas.homestock.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three ways to buy Premium (see PremiumScreen) — two recurring subscriptions and one
 *  permanent one-time purchase for people who'd rather not subscribe at all. Kept as three
 *  separate Play Console products rather than base plans of one subscription product — the
 *  app already had [PREMIUM_YEARLY_PRODUCT_ID] as its own product before this, and separate
 *  products keep [BillingRepository] from needing to parse base-plan ids out of offer tokens. */
enum class PremiumPlan(val productId: String, val productType: String) {
    MONTHLY(BillingRepository.PREMIUM_MONTHLY_PRODUCT_ID, BillingClient.ProductType.SUBS),
    YEARLY(BillingRepository.PREMIUM_YEARLY_PRODUCT_ID, BillingClient.ProductType.SUBS),
    LIFETIME(BillingRepository.PREMIUM_LIFETIME_PRODUCT_ID, BillingClient.ProductType.INAPP),
}

/** The recurring price shown after any trial phase — the *last* pricing phase in a
 *  subscription offer's list, since a free-trial offer prepends a zero-price phase before the
 *  real recurring one. For a plan with no trial (a single-phase offer) this is that one phase.
 *  `null` for a one-time (INAPP) product — see [formattedOneTimePrice] for those. */
val ProductDetails.formattedRecurringPrice: String?
    get() = subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

/** True if this subscription's (only) offer starts with a zero-price phase — i.e. buying it
 *  requests a free trial. Play itself decides server-side whether this Play account is still
 *  eligible for that trial; this only reflects what the *offer* is configured to include, not
 *  a guarantee this specific purchase will actually be free — see [BillingRepository]'s class
 *  doc. */
val ProductDetails.hasTrialOffer: Boolean
    get() = subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros == 0L

/** The one-time price for a non-subscription (INAPP) product — the lifetime plan or the
 *  unlimited-members household add-on. `null` for a subscription — see [formattedRecurringPrice]. */
val ProductDetails.formattedOneTimePrice: String?
    get() = oneTimePurchaseOfferDetails?.formattedPrice

/**
 * HomeStock Premium — three ways to buy it (see [PremiumPlan]: monthly/yearly subscriptions,
 * each expected to carry a free-trial offer configured in the Play Console, plus a one-time
 * "Levenslang" purchase) and one household add-on ([PREMIUM_UNLIMITED_MEMBERS_PRODUCT_ID], a
 * one-time purchase that lifts the Premium household member cap — see
 * [HouseholdMembersRepository]). None of these four products can be created by this class;
 * they must already exist in the Play Console with these exact ids (two SUBS, two INAPP) —
 * this repository can't create them.
 *
 * [isPremium] is this device's own entitlement, always re-derived from Play's purchase
 * records rather than trusted from a local cache alone — true for an active monthly/yearly
 * subscription *or* an owned lifetime purchase (a one-time purchase never expires, so once
 * owned it stays owned without Play re-confirming anything on a schedule the way a
 * subscription does). A household's shared premium status (any member unlocks it for
 * everyone) is handled one layer up, in [HouseholdMembersRepository]; so is
 * [hasUnlimitedMembersAddon].
 *
 * [isPremium] also honors [debugPremiumOverride] in debug builds only — a locally persisted
 * toggle for testing the gated screens and the household member cap without needing real Play
 * Console products set up. It's a no-op ([setDebugPremiumOverride] returns immediately) and
 * always reads as false in a release build, so it can't leak into a real install.
 */
class BillingRepository(context: Context, private val analyticsRepository: AnalyticsRepository) {
    private val appContext = context.applicationContext
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val _isPremiumFromPlay = MutableStateFlow(false)
    private val _hasUnlimitedMembersAddonFromPlay = MutableStateFlow(false)

    private val debugPrefs = appContext.getSharedPreferences(DEBUG_PREFS_NAME, Context.MODE_PRIVATE)
    private val _debugPremiumOverride = MutableStateFlow(debugPrefs.getBoolean(KEY_DEBUG_PREMIUM_OVERRIDE, false))
    val debugPremiumOverride: StateFlow<Boolean> = _debugPremiumOverride

    val isPremium: StateFlow<Boolean> = combine(_isPremiumFromPlay, _debugPremiumOverride) { fromPlay, debugOverride ->
        fromPlay || (BuildConfig.DEBUG && debugOverride)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, false)

    /** Whether this device owns the "Onbeperkt huisgenoten" household add-on. Debug override
     *  piggybacks on [debugPremiumOverride] too — no separate toggle, since testing the addon
     *  behavior needs Premium active anyway. */
    val hasUnlimitedMembersAddon: StateFlow<Boolean> =
        combine(_hasUnlimitedMembersAddonFromPlay, _debugPremiumOverride) { fromPlay, debugOverride ->
            fromPlay || (BuildConfig.DEBUG && debugOverride)
        }.stateIn(repositoryScope, SharingStarted.Eagerly, false)

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    /** Keyed by product id (see [PremiumPlan.productId]/[PREMIUM_UNLIMITED_MEMBERS_PRODUCT_ID])
     *  — empty until Play answers the initial query, and permanently missing an entry for any
     *  product id that doesn't exist yet in the Play Console. */
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            repositoryScope.launch { handlePurchases(purchases) }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        connect()
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    repositoryScope.launch {
                        queryProductDetails()
                        refreshPurchases()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // The library reconnects automatically on the next billing call; the last
                // known state is kept rather than reset, so a brief disconnect doesn't flash
                // a premium screen back to locked.
            }
        })
    }

    // A single query mixing SUBS and INAPP products is fine — QueryProductDetailsParams.Product
    // carries its own type per entry, the query itself isn't type-scoped the way
    // QueryPurchasesParams below is.
    private suspend fun queryProductDetails() {
        val idsAndTypes = listOf(
            PREMIUM_MONTHLY_PRODUCT_ID to BillingClient.ProductType.SUBS,
            PREMIUM_YEARLY_PRODUCT_ID to BillingClient.ProductType.SUBS,
            PREMIUM_LIFETIME_PRODUCT_ID to BillingClient.ProductType.INAPP,
            PREMIUM_UNLIMITED_MEMBERS_PRODUCT_ID to BillingClient.ProductType.INAPP,
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                idsAndTypes.map { (id, type) ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(type)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        _productDetails.value = (result.productDetailsList ?: emptyList()).associateBy { it.productId }
    }

    /** Re-checks Play's purchase records; called on connect and from a "Restore aankopen"
     *  action. Unlike the query above, purchases have to be fetched per product type — there's
     *  no combined SUBS+INAPP call. */
    suspend fun refreshPurchases() {
        val subsResult = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        val inAppResult = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        handlePurchases(subsResult.purchasesList + inAppResult.purchasesList)
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val purchasedProductIds = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .toSet()
        _isPremiumFromPlay.value = PremiumPlan.entries.any { it.productId in purchasedProductIds }
        _hasUnlimitedMembersAddonFromPlay.value = PREMIUM_UNLIMITED_MEMBERS_PRODUCT_ID in purchasedProductIds

        // Unacknowledged purchases are refunded automatically by Play after 3 days, so this
        // must run on every purchase we see, not just ones made this session — true for
        // subscriptions and both one-time products here alike (neither one-time product is
        // ever consumed: both are meant to stay permanently owned, not be repurchasable). Only
        // fires once per purchase (the *next* time this list is fetched, it's acknowledged
        // already and filtered back out here), which is also exactly the point to fire the
        // one-time "a purchase actually completed" analytics event rather than on every
        // refresh — see AnalyticsRepository.logPurchaseCompleted's doc for what [isTrial]
        // here does and doesn't guarantee.
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { purchase ->
                val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(ackParams)
                purchase.products.forEach { productId ->
                    val isTrialOffer = _productDetails.value[productId]?.hasTrialOffer == true
                    analyticsRepository.logPurchaseCompleted(productId, isTrial = isTrialOffer)
                }
            }
    }

    /** Opens Play's checkout sheet for [plan]; a no-op until that plan's product details have
     *  loaded. For a subscription plan, requests its first (and normally only) offer — see the
     *  class doc: Play itself decides whether that offer's free-trial phase actually applies to
     *  this account, this repository never needs to compute trial eligibility itself. */
    fun launchPurchaseFlow(activity: Activity, plan: PremiumPlan) {
        val details = _productDetails.value[plan.productId] ?: return
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        if (plan.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
            paramsBuilder.setOfferToken(offerToken)
        }
        analyticsRepository.logPurchaseStarted(plan.productId)
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    /** Opens Play's checkout sheet for the one-time "Onbeperkt huisgenoten" household add-on
     *  (see [HouseholdMembersRepository]); a no-op until its product details have loaded. */
    fun launchUnlimitedMembersPurchaseFlow(activity: Activity) {
        val details = _productDetails.value[PREMIUM_UNLIMITED_MEMBERS_PRODUCT_ID] ?: return
        analyticsRepository.logPurchaseStarted(PREMIUM_UNLIMITED_MEMBERS_PRODUCT_ID)
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    /** Debug builds only — see the class doc. Silently ignored in release. */
    fun setDebugPremiumOverride(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        debugPrefs.edit().putBoolean(KEY_DEBUG_PREMIUM_OVERRIDE, enabled).apply()
        _debugPremiumOverride.value = enabled
    }

    companion object {
        // Must be created as subscription products with these exact ids in the Play Console,
        // each with a base offer that includes a free-trial phase — see
        // RemoteConfigRepository.trialDays for the length shown in the app's own copy, which
        // must be kept in sync with that offer by hand (Play Billing has no API to read a
        // trial's configured length back out of an offer).
        const val PREMIUM_MONTHLY_PRODUCT_ID = "premium_monthly"
        const val PREMIUM_YEARLY_PRODUCT_ID = "premium_yearly"

        // Must be created as a one-time (managed) in-app product in the Play Console — for
        // people who'd rather pay once than subscribe.
        const val PREMIUM_LIFETIME_PRODUCT_ID = "premium_lifetime"

        // Must be created as a one-time (managed) in-app product in the Play Console — the
        // household member-cap add-on, see HouseholdMembersRepository and
        // RemoteConfigRepository.premiumMemberCap for the cap it lifts.
        const val PREMIUM_UNLIMITED_MEMBERS_PRODUCT_ID = "premium_unlimited_members"

        private const val DEBUG_PREFS_NAME = "billing_debug_prefs"
        private const val KEY_DEBUG_PREMIUM_OVERRIDE = "debug_premium_override"
    }
}
