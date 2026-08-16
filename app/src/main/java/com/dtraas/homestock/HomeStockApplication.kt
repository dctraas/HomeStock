package com.dtraas.homestock

import android.app.Application
import com.dtraas.homestock.di.AppContainer
import com.dtraas.homestock.work.ExpiryCheckWorker
import com.dtraas.homestock.work.ReceiptQueueWorker

class HomeStockApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ExpiryCheckWorker.createNotificationChannel(this)
        ExpiryCheckWorker.schedule(this)
        // Safety net for a process death between ReceiptQueueRepository.enqueue()'s file write
        // and its own schedule() call — re-arms the drain on every app start whenever anything
        // is still pending, rather than leaving a receipt stuck in the queue forever.
        if (container.receiptQueueRepository.pendingCount.value > 0) {
            ReceiptQueueWorker.schedule(this)
        }
    }
}
