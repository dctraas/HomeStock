package com.dtraas.boodschapbeheer.di

import android.content.Context
import com.dtraas.boodschapbeheer.data.remote.OpenFoodFactsApi
import com.dtraas.boodschapbeheer.data.repository.ActivityLogRepository
import com.dtraas.boodschapbeheer.data.repository.HouseholdRepository
import com.dtraas.boodschapbeheer.data.repository.HouseholdSession
import com.dtraas.boodschapbeheer.data.repository.InventoryRepository
import com.dtraas.boodschapbeheer.data.repository.ProductRepository
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository
import com.dtraas.boodschapbeheer.data.repository.StatisticsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    val householdSession: HouseholdSession = HouseholdSession(context)

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val householdRepository: HouseholdRepository by lazy {
        HouseholdRepository(firestore, auth)
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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
        ActivityLogRepository(firestore, householdSession)
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(firestore, householdSession, activityLogRepository)
    }

    val shoppingListRepository: ShoppingListRepository by lazy {
        ShoppingListRepository(firestore, householdSession)
    }

    val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository(firestore, householdSession)
    }
}
