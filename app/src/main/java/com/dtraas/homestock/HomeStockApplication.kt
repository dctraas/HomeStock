package com.dtraas.homestock

import android.app.Application
import com.dtraas.homestock.di.AppContainer
import com.dtraas.homestock.work.ExpiryCheckWorker
import com.dtraas.homestock.work.ReceiptQueueWorker
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class HomeStockApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Before anything else touches Firebase: every Firestore/Auth/Functions call this app
        // makes from here on attaches an App Check token once this is installed. See this
        // function's own doc for what that does and doesn't protect, and why it's still inert
        // (adds a header, nothing more) until enforcement is separately turned on per-product
        // in the Firebase Console — see README.md.
        installAppCheck()
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

    /**
     * Registers this app with Firebase App Check — proof to Firebase that a request genuinely
     * comes from this app's own unmodified build running on a real device, not a scripted
     * client hitting the same Firestore/Cloud Functions endpoints directly with a guessed
     * household code or a hand-crafted request body. Complements, rather than replaces, the
     * household code itself (see firestore.rules' class doc) and the server-side checks already
     * in functions/src/index.ts (`requireUid`/`requirePremiumHousehold`) — App Check answers "is
     * this really the app calling?", those answer "is this caller allowed to do this?".
     *
     * [DebugAppCheckProviderFactory] in debug builds logs a debug token to Logcat on first
     * launch; that token needs registering once, per debug device, in Firebase Console →
     * App Check → Apps → this app → "Manage debug tokens" — without it, every Firestore/
     * Functions call from a debug build fails as soon as enforcement is ever turned on. Release
     * builds use [PlayIntegrityAppCheckProviderFactory] instead, which needs no such manual
     * token — Play itself attests the app/device at request time.
     */
    private fun installAppCheck() {
        val provider = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(provider)
    }
}
