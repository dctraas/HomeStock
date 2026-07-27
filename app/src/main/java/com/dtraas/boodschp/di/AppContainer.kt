package com.dtraas.boodschp.di

import android.content.Context
import androidx.room.Room
import com.dtraas.boodschp.data.local.AppDatabase
import com.dtraas.boodschp.data.remote.OpenFoodFactsApi
import com.dtraas.boodschp.data.repository.ActivityLogRepository
import com.dtraas.boodschp.data.repository.InventoryRepository
import com.dtraas.boodschp.data.repository.ProductRepository
import com.dtraas.boodschp.data.repository.ShoppingListRepository
import com.dtraas.boodschp.data.repository.StatisticsRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Small hand-rolled dependency container. The app is simple enough that a
 * DI framework (Hilt/Koin) would add more ceremony than value; everything
 * here is a plain singleton built once in [com.dtraas.boodschp.BoodschpApplication].
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME,
    )
        // The app hasn't shipped yet, so schema changes during active
        // development just reset the local dev database instead of
        // requiring a hand-written Migration for every iteration.
        .fallbackToDestructiveMigration()
        .build()

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
        ProductRepository(database.productDao(), api)
    }

    val activityLogRepository: ActivityLogRepository by lazy {
        ActivityLogRepository(database.activityLogDao())
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(database, database.inventoryDao(), database.scanHistoryDao(), activityLogRepository)
    }

    val shoppingListRepository: ShoppingListRepository by lazy {
        ShoppingListRepository(database.shoppingListDao())
    }

    val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository(database.scanHistoryDao(), database.inventoryDao())
    }
}
