package com.dtraas.boodschp

import android.app.Application
import com.dtraas.boodschp.di.AppContainer

class BoodschpApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
