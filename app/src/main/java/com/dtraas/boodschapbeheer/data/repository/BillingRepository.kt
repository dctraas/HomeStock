package com.dtraas.boodschapbeheer.data.repository

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
import com.dtraas.boodschapbeheer.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * BoodschapBeheer Premium — a single yearly subscription (see [PREMIUM_YEARLY_PRODUCT_ID],
 * which must exist as a subscription product in the Play Console; this repository can't
 * create it) unlocking Recepten, Bonnetje scannen, Statistieken, and households of more
 * than 2 people. [isPremium] is this device's own entitlement, always re-derived from
 * Play's purchase records rather than trusted from a local cache alone. A household's
 * shared premium status (any member unlocks it for everyone) is handled one layer up, in
 * [HouseholdMembersRepository].
 *
 * [isPremium] also honors [debugPremiumOverride] in debug builds only — a locally
 * persisted toggle for testing the gated screens and the household member cap without
 * needing a real Play Console subscription set up. It's a no-op ([setDebugPremiumOverride]
 * returns immediately) and always reads as false in a release build, so it can't leak into
 * a real install.
 */
class BillingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val _isPremiumFromPlay = MutableStateFlow(false)

    private val debugPrefs = appContext.getSharedPreferences(DEBUG_PREFS_NAME, Context.MODE_PRIVATE)
    private val _debugPremiumOverride = MutableStateFlow(debugPrefs.getBoolean(KEY_DEBUG_PREMIUM_OVERRIDE, false))
    val debugPremiumOverride: StateFlow<Boolean> = _debugPremiumOverride

    val isPremium: StateFlow<Boolean> = combine(_isPremiumFromPlay, _debugPremiumOverride) { fromPlay, debugOverride ->
        fromPlay || (BuildConfig.DEBUG && debugOverride)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, false)

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            repositoryScope.launch { handlePurchases(purchases) }
        }
    }

    // enableOneTimeProducts() is required by the builder even though this app only sells a
    // subscription — Play Billing has no "subscriptions only, no pending-purchase config"
    // shorthand.
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
                // known isPremium value is kept rather than reset, so a brief disconnect
                // doesn't flash a premium screen back to locked.
            }
        })
    }

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_YEARLY_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        _productDetails.value = result.productDetailsList?.firstOrNull()
    }

    /** Re-checks Play's purchase records; called on connect and from a "Restore aankopen" action. */
    suspend fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val result = client.queryPurchasesAsync(params)
        handlePurchases(result.purchasesList)
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        _isPremiumFromPlay.value = purchases.any { purchase ->
            purchase.products.contains(PREMIUM_YEARLY_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        // Unacknowledged purchases are refunded automatically by Play after 3 days, so this
        // must run on every purchase we see, not just ones made this session.
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { purchase ->
                val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(ackParams)
            }
    }

    /** Opens Play's subscription checkout sheet for the yearly plan; a no-op until product details have loaded. */
    fun launchPurchaseFlow(activity: Activity) {
        val details = _productDetails.value ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
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
        // Must be created as a subscription product with this exact id in the Play Console.
        const val PREMIUM_YEARLY_PRODUCT_ID = "premium_yearly"

        private const val DEBUG_PREFS_NAME = "billing_debug_prefs"
        private const val KEY_DEBUG_PREMIUM_OVERRIDE = "debug_premium_override"
    }
}
