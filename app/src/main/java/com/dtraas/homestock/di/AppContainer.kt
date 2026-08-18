package com.dtraas.homestock.di

import android.content.Context
import com.dtraas.homestock.BuildConfig
import com.dtraas.homestock.data.remote.OpenFoodFactsApi
import com.dtraas.homestock.data.repository.AccountLinkRepository
import com.dtraas.homestock.data.repository.ActivityLogRepository
import com.dtraas.homestock.data.repository.AiRecognitionRepository
import com.dtraas.homestock.data.repository.AnalyticsRepository
import com.dtraas.homestock.data.repository.BillingRepository
import com.dtraas.homestock.data.repository.DeviceProfile
import com.dtraas.homestock.data.repository.DismissedNoticesStore
import com.dtraas.homestock.data.repository.FeedbackRepository
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.data.repository.HouseholdSession
import com.dtraas.homestock.data.repository.InventoryPreferences
import com.dtraas.homestock.data.repository.InventoryRepository
import com.dtraas.homestock.data.repository.MealPlanRepository
import com.dtraas.homestock.data.repository.NotificationPreferences
import com.dtraas.homestock.data.repository.OnboardingTourPreferences
import com.dtraas.homestock.data.repository.ProductRepository
import com.dtraas.homestock.data.repository.ReceiptQueueRepository
import com.dtraas.homestock.data.repository.ReceiptRecognitionRepository
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RemoteConfigRepository
import com.dtraas.homestock.data.repository.ShoppingListRepository
import com.dtraas.homestock.data.repository.StatisticsRepository
import com.dtraas.homestock.data.repository.StoreRepository
import com.dtraas.homestock.data.repository.ThemePreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Small hand-rolled dependency container. The app is simple enough that a
 * DI framework (Hilt/Koin) would add more ceremony than value; everything
 * here is a plain singleton built once in [com.dtraas.homestock.HomeStockApplication].
 *
 * Data (inventory, shopping list, products, activity log) lives in Cloud
 * Firestore, shared by every device that has joined the same household —
 * see [HouseholdSession] and [HouseholdRepository].
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val householdSession: HouseholdSession = HouseholdSession(context)
    val notificationPreferences: NotificationPreferences = NotificationPreferences(context)
    val deviceProfile: DeviceProfile = DeviceProfile(context)
    val dismissedNoticesStore: DismissedNoticesStore = DismissedNoticesStore(context)
    val themePreferences: ThemePreferences = ThemePreferences(context)
    val inventoryPreferences: InventoryPreferences = InventoryPreferences(context)
    val analyticsRepository: AnalyticsRepository = AnalyticsRepository(context)
    val remoteConfigRepository: RemoteConfigRepository = RemoteConfigRepository(context)
    val onboardingTourPreferences: OnboardingTourPreferences = OnboardingTourPreferences(context)

    // Firestore persists writes to disk by default on Android, but the cache size is
    // capped (~100MB) unless set explicitly — for a household's full inventory/shopping
    // history that cap can be reached, silently evicting older data. Unlimited keeps
    // everything available offline; the household's data isn't large enough for this to
    // matter for disk space. Reads and writes work the same offline either way — Firestore
    // queues writes locally and syncs automatically once the connection returns.
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build(),
            )
            .build()
    }
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    val householdRepository: HouseholdRepository by lazy {
        HouseholdRepository(appContext, firestore, auth, householdSession)
    }

    val accountLinkRepository: AccountLinkRepository by lazy {
        AccountLinkRepository(appContext, auth, functions)
    }

    val billingRepository: BillingRepository by lazy {
        BillingRepository(appContext, analyticsRepository)
    }

    val householdMembersRepository: HouseholdMembersRepository by lazy {
        HouseholdMembersRepository(
            firestore, storage, householdSession, auth, billingRepository, deviceProfile, analyticsRepository, functions,
        )
    }

    // Region must match where the Cloud Function is deployed (see functions/src/index.ts's
    // setGlobalOptions) — a mismatched region silently fails every call with NOT_FOUND.
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")

    val aiRecognitionRepository: AiRecognitionRepository by lazy {
        AiRecognitionRepository(functions, householdSession)
    }

    val receiptRecognitionRepository: ReceiptRecognitionRepository by lazy {
        ReceiptRecognitionRepository(functions, householdSession)
    }

    // Open Food Facts documents that it throttles/blocks requests carrying a generic HTTP
    // client User-Agent (e.g. OkHttp's own default) to fight scraping abuse — without an
    // app-identifying one, barcode lookups intermittently or permanently fail with an HTTP
    // error that has nothing to do with the device's actual connectivity, which is exactly
    // what surfaces to the user as a misleading "Geen verbinding" (see ScanResultViewModel,
    // which maps every non-"product not found" failure to that message).
    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "HomeStock/${BuildConfig.VERSION_NAME} (Android; +https://github.com/dctraas/HomeStock)")
            .build()
        chain.proceed(request)
    }

    // Logging is debug-only: even at BASIC level, release builds shouldn't write network
    // activity (which barcodes were scanned, when) to logcat, which other apps or anyone
    // with physical/adb access to the device could otherwise read.
    //
    // OkHttp's 10s default connect/read/write timeouts are tight enough that a brief cellular
    // hiccup or a slow Open Food Facts response intermittently trips a SocketTimeoutException —
    // which ScanResultViewModel then shows as a misleading "Geen verbinding", even though the
    // device is online and the request would have succeeded given a bit more time. Widening the
    // timeouts (and leaving retryOnConnectionFailure on, which is also the default) makes that
    // class of false negative much rarer; ProductRepository's own retry on top of this handles
    // the rest.
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    private val api: OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl(OpenFoodFactsApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenFoodFactsApi::class.java)

    val productRepository: ProductRepository by lazy {
        ProductRepository(firestore, storage, householdSession, api)
    }

    val activityLogRepository: ActivityLogRepository by lazy {
        ActivityLogRepository(appContext, firestore, householdSession, deviceProfile)
    }

    val shoppingListRepository: ShoppingListRepository by lazy {
        ShoppingListRepository(appContext, firestore, householdSession)
    }

    val storeRepository: StoreRepository by lazy {
        StoreRepository(firestore, householdSession)
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(firestore, householdSession, activityLogRepository, productRepository, shoppingListRepository, inventoryPreferences)
    }

    val receiptQueueRepository: ReceiptQueueRepository by lazy {
        ReceiptQueueRepository(appContext, receiptRecognitionRepository, productRepository, inventoryRepository)
    }

    val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository(firestore, householdSession)
    }

    val feedbackRepository: FeedbackRepository by lazy {
        FeedbackRepository(firestore, BuildConfig.VERSION_NAME)
    }

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(firestore, functions, householdSession, inventoryRepository, shoppingListRepository)
    }

    val mealPlanRepository: MealPlanRepository by lazy {
        MealPlanRepository(firestore, householdSession)
    }
}
