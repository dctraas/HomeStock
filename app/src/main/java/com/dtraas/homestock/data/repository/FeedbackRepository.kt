package com.dtraas.homestock.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Sends in-app feedback (star rating + message) to a global, write-only Firestore
 * collection — not scoped to a household, since it's about the app itself. See
 * firestore.rules: clients may create entries here but never read, update, or list them.
 */
class FeedbackRepository(
    private val firestore: FirebaseFirestore,
    private val appVersionName: String,
) {
    suspend fun submit(rating: Int, message: String) {
        firestore.collection(COLLECTION).add(
            mapOf(
                "rating" to rating,
                "message" to message,
                "appVersion" to appVersionName,
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    private companion object {
        const val COLLECTION = "appFeedback"
    }
}
