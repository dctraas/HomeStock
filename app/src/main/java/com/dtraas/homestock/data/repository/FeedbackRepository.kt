package com.dtraas.homestock.data.repository

import android.content.Context
import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlinx.coroutines.tasks.await

/** What kind of feedback this is — see MoreScreen's `FeedbackDialog` category tiles, which
 *  replaced the old star rating as the required gate: a category is what makes a report
 *  actionable, a 1-5 rating on its own wasn't. */
enum class FeedbackCategory(val storageKey: String) {
    BUG("bug"),
    IDEA("idea"),
    COMPLIMENT("compliment"),
}

/**
 * Sends in-app feedback to a global, write-only Firestore collection — not scoped to a
 * household, since it's about the app itself. See firestore.rules: clients may create entries
 * here but never read, update, or list them.
 */
class FeedbackRepository(
    private val firestore: FirebaseFirestore,
    private val appVersionName: String,
    private val context: Context,
) {
    /** [includeDiagnostics] adds device model and app locale — never anything about the
     *  household's own inventory/lists — matching the sheet's "Versie, toestel en taal — geen
     *  voorraadgegevens" subtitle. [appVersionName] is always sent regardless, same as before. */
    suspend fun submit(category: FeedbackCategory, message: String, includeDiagnostics: Boolean) {
        val data = mutableMapOf<String, Any>(
            "category" to category.storageKey,
            "message" to message,
            "appVersion" to appVersionName,
            "createdAt" to System.currentTimeMillis(),
        )
        if (includeDiagnostics) {
            data["device"] = "${Build.MANUFACTURER} ${Build.MODEL}"
            data["locale"] = Locale.getDefault().toLanguageTag()
        }
        firestore.collection(COLLECTION).add(data).await()
    }

    private companion object {
        const val COLLECTION = "appFeedback"
    }
}
