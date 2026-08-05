package com.dtraas.homestock

import android.app.Application
import com.dtraas.homestock.di.AppContainer
import com.dtraas.homestock.work.ExpiryCheckWorker

class HomeStockApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ExpiryCheckWorker.createNotificationChannel(this)
        ExpiryCheckWorker.schedule(this)
    }
}
