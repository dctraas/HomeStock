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

/** The two ways to buy Premium (see PremiumScreen) — monthly or yearly billing of the exact
 *  same one Premium tier, nothing else differs between them but price and period. Kept as two
 *  separate Play Console products rather than base plans of one subscription product — the app
 *  already had [PREMIUM_YEARLY_PRODUCT_ID] as its own product before this, and separate
 *  products keep [BillingRepository] from needing to parse base-plan ids out of offer tokens.
 *
 *  Deliberately just these two: Premium used to also offer a one-time "Levenslang" purchase and
 *  a separately-purchasable "Onbeperkt huisgenoten" add-on on top of a capped Premium tier —
 *  two extra purchase decisions for what's otherwise one clear product. Both were folded into
 *  this single tier (see [HouseholdMembersRepository.observeCapacityInfo]) rather than kept as
 *  parallel options. */
enum class PremiumPlan(val productId: String, val productType: String) {
    MONTHLY(BillingRepository.PREMIUM_MONTHLY_PRODUCT_ID, BillingClient.ProductType.SUBS),
    YEARLY(BillingRepository.PREMIUM_YEARLY_PRODUCT_ID, BillingClient.ProductType.SUBS),
}

/** The recurring price shown after any trial phase — the *last* pricing phase in a
 *  subscription offer's list, since a free-trial offer prepends a zero-price phase before the
 *  real recurring one. For a plan with no trial (a single-phase offer) this is that one phase. */
val ProductDetails.formattedRecurringPrice: String?
    get() = subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

/** True if this subscription's (only) offer starts with a zero-price phase — i.e. buying it
 *  requests a free trial. Play itself decides server-side whether this Play account is still
 *  eligible for that trial; this only reflects what the *offer* is configured to include, not
 *  a guarantee this specific purchase will actually be free — see [BillingRepository]'s class
 *  doc. */
val ProductDetails.hasTrialOffer: Boolean
    get() = subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros == 0L

/**
 * HomeStock Premium — one tier, bought as either of two subscription cadences (see
 * [PremiumPlan]: monthly or yearly, each expected to carry a free-trial offer configured in
 * the Play Console). Neither product can be created by this class; both must already exist in
 * the Play Console with these exact ids — this repository can't create them.
 *
 * [isPremium] is this device's own entitlement, always re-derived from Play's purchase records
 * rather than trusted from a local cache alone — true for an active monthly or yearly
 * subscription. A household's shared premium status (any member unlocks it for everyone,
 * including lifting the member cap entirely) is handled one layer up, in
 * [HouseholdMembersRepository].
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

    private val _activePurchases = MutableStateFlow<List<Purchase>>(emptyList())
    /** Every currently-owned, Play-confirmed subscription purchase (not just its derived
     *  [isPremium] boolean) — [HouseholdMembersRepository] uses this to ask the `verifyPurchase`
     *  Cloud Function to confirm each one server-side and write a tamper-proof `isPremium` onto
     *  this device's member doc. See that function's doc comment in functions/src/index.ts for
     *  why this exists alongside (not instead of) the client-derived value below: server
     *  verification only becomes the actual security boundary once firestore.rules is
     *  tightened to require it, which needs the Play Console access grant documented there
     *  first — until then this is authoritative when it succeeds, and a no-op fallback to the
     *  client-derived value otherwise (e.g. offline, or that grant not set up yet). */
    val activePurchases: StateFlow<List<Purchase>> = _activePurchases

    private val debugPrefs = appContext.getSharedPreferences(DEBUG_PREFS_NAME, Context.MODE_PRIVATE)
    private val _debugPremiumOverride = MutableStateFlow(debugPrefs.getBoolean(KEY_DEBUG_PREMIUM_OVERRIDE, false))
    val debugPremiumOverride: StateFlow<Boolean> = _debugPremiumOverride

    val isPremium: StateFlow<Boolean> = combine(_isPremiumFromPlay, _debugPremiumOverride) { fromPlay, debugOverride ->
        fromPlay || (BuildConfig.DEBUG && debugOverride)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, false)

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    /** Keyed by product id (see [PremiumPlan.productId]) — empty until Play answers the initial
     *  query, and permanently missing an entry for any product id that doesn't exist yet in
     *  the Play Console. */
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

    // Both Premium products are subscriptions now (see PremiumPlan's doc for why the former
    // one-time products were folded away) — just one type-scoped query. Kept going through
    // buildProductDetailsParams rather than inlined: the Billing Library itself rejects a
    // product list mixing SUBS and INAPP in one call ("All products should be of the same
    // product type.", thrown by Builder.setProductList, confirmed by a crash on a real device
    // back when this queried both types) — if a one-time product ever comes back, this helper
    // is already the right shape to add a second type-scoped call again instead of reintroducing
    // that crash.
    private suspend fun queryProductDetails() {
        val subsIds = PremiumPlan.entries.map { it.productId }
        val subsResult = client.queryProductDetails(buildProductDetailsParams(subsIds, BillingClient.ProductType.SUBS))
        _productDetails.value = (subsResult.productDetailsList ?: emptyList()).associateBy { it.productId }
    }

    private fun buildProductDetailsParams(productIds: List<String>, productType: String): QueryProductDetailsParams =
        QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(productType)
                        .build()
                },
            )
            .build()

    /** Re-checks Play's purchase records; called on connect and from a "Restore aankopen"
     *  action. Only SUBS purchases exist now (see [queryProductDetails]'s doc). */
    suspend fun refreshPurchases() {
        val subsResult = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        handlePurchases(subsResult.purchasesList)
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val purchasedProductIds = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .toSet()
        _isPremiumFromPlay.value = PremiumPlan.entries.any { it.productId in purchasedProductIds }
        _activePurchases.value = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        // Unacknowledged purchases are refunded automatically by Play after 3 days, so this
        // must run on every purchase we see, not just ones made this session. Only fires once
        // per purchase (the *next* time this list is fetched, it's acknowledged already and
        // filtered back out here), which is also exactly the point to fire the one-time "a
        // purchase actually completed" analytics event rather than on every refresh — see
        // AnalyticsRepository.logPurchaseCompleted's doc for what [isTrial] here does and
        // doesn't guarantee.
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
        //
        // premium_lifetime and premium_unlimited_members used to exist alongside these as a
        // one-time no-subscription purchase and a separate member-cap add-on respectively —
        // both folded into this single tier (see PremiumPlan's doc); their Play Console
        // products can stay defined (nothing un-purchases them for anyone who already bought
        // one), the app just no longer offers or queries them.
        const val PREMIUM_MONTHLY_PRODUCT_ID = "premium_monthly"
        const val PREMIUM_YEARLY_PRODUCT_ID = "premium_yearly"

        private const val DEBUG_PREFS_NAME = "billing_debug_prefs"
        private const val KEY_DEBUG_PREMIUM_OVERRIDE = "debug_premium_override"
    }
}
