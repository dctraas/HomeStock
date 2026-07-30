package com.dtraas.boodschapbeheer

import android.app.Application
import com.dtraas.boodschapbeheer.di.AppContainer
import com.dtraas.boodschapbeheer.work.ExpiryCheckWorker

class BoodschapBeheerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ExpiryCheckWorker.createNotificationChannel(this)
        ExpiryCheckWorker.schedule(this)
    }
}
