package com.dtraas.boodschapbeheer.di

import android.content.Context
import com.dtraas.boodschapbeheer.BuildConfig
import com.dtraas.boodschapbeheer.data.remote.OpenFoodFactsApi
import com.dtraas.boodschapbeheer.data.remote.TheMealDbApi
import com.dtraas.boodschapbeheer.data.repository.ActivityLogRepository
import com.dtraas.boodschapbeheer.data.repository.DeviceProfile
import com.dtraas.boodschapbeheer.data.repository.DismissedNoticesStore
import com.dtraas.boodschapbeheer.data.repository.FeedbackRepository
import com.dtraas.boodschapbeheer.data.repository.HouseholdRepository
import com.dtraas.boodschapbeheer.data.repository.HouseholdSession
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.NotificationPreferences
import com.dtraas.boodschapbeheer.data.repository.ProductRepository
import com.dtraas.boodschapbeheer.data.repository.RecipeRepository
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository
import com.dtraas.boodschapbeheer.data.repository.StatisticsRepository
import com.dtraas.boodschapbeheer.data.repository.StoreRepository
import com.dtraas.boodschapbeheer.data.repository.ThemePreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Small hand-rolled dependency container. The app is simple enough that a
 * DI framework (Hilt/Koin) would add more ceremony than value; everything
 * here is a plain singleton built once in [com.dtraas.boodschapbeheer.BoodschapBeheerApplication].
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

    val householdRepository: HouseholdRepository by lazy {
        HouseholdRepository(appContext, firestore, auth)
    }

    // Logging is debug-only: even at BASIC level, release builds shouldn't write network
    // activity (which barcodes were scanned, when) to logcat, which other apps or anyone
    // with physical/adb access to the device could otherwise read.
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
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
        ProductRepository(firestore, householdSession, api)
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
        InventoryRepository(firestore, householdSession, activityLogRepository, productRepository, shoppingListRepository)
    }

    val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository(firestore, householdSession)
    }

    val feedbackRepository: FeedbackRepository by lazy {
        FeedbackRepository(firestore, BuildConfig.VERSION_NAME)
    }

    private val mealDbApi: TheMealDbApi = Retrofit.Builder()
        .baseUrl(TheMealDbApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TheMealDbApi::class.java)

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(mealDbApi, inventoryRepository, shoppingListRepository)
    }
}
