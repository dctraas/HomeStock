package com.dtraas.boodschapbeheer.data.repository

import android.content.Context
import com.dtraas.boodschapbeheer.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Something went wrong that isn't a plain network/Firestore exception. */
class HouseholdNotFoundException(message: String) : Exception(message)

/**
 * Creates and joins households — the mechanism by which multiple devices share the
 * same Firestore data. A household is just a document at `households/{code}`; knowing
 * the code is the only thing needed to join, there is no separate invite flow.
 */
class HouseholdRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    /** Generates a fresh, unused household code and creates its document. */
    suspend fun createHousehold(): Result<String> {
        return try {
            ensureSignedIn()
            var createdCode: String? = null
            repeat(MAX_CODE_ATTEMPTS) {
                if (createdCode != null) return@repeat
                val code = generateCode()
                val doc = firestore.collection(HOUSEHOLDS_COLLECTION).document(code)
                if (!doc.get().await().exists()) {
                    doc.set(mapOf("createdAt" to System.currentTimeMillis())).await()
                    createdCode = code
                }
            }
            createdCode?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException(context.getString(R.string.household_generate_code_failed)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Joins an existing household by its code, failing if no such household exists. */
    suspend fun joinHousehold(code: String): Result<String> {
        return try {
            ensureSignedIn()
            val normalized = code.trim().uppercase()
            if (normalized.isEmpty()) {
                return Result.failure(HouseholdNotFoundException(context.getString(R.string.household_not_found_format, normalized)))
            }
            val snapshot = firestore.collection(HOUSEHOLDS_COLLECTION).document(normalized).get().await()
            if (!snapshot.exists()) {
                Result.failure(HouseholdNotFoundException(context.getString(R.string.household_not_found_format, normalized)))
            } else {
                Result.success(normalized)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes a household and everything under it — irreversible, and it
     * affects every device sharing the code, not just this one (see the confirmation
     * copy shown before this is called). Used for the AVG/GDPR right to erasure and
     * Google Play's data-deletion requirement; "leave household" alone only unlinks
     * this device, it doesn't remove the shared data.
     *
     * The Firestore client SDK has no server-side recursive delete, so each
     * subcollection is paged through and batch-deleted before the household document
     * itself. If this is interrupted partway (e.g. lost network), it can simply be
     * called again — deleting an already-empty collection or missing document is a no-op.
     */
    suspend fun deleteHousehold(householdId: String) {
        val household = firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId)
        SUBCOLLECTIONS.forEach { name -> deleteCollection(household.collection(name)) }
        household.delete().await()
    }

    private suspend fun deleteCollection(collection: CollectionReference) {
        while (true) {
            val snapshot = collection.limit(DELETE_BATCH_SIZE.toLong()).get().await()
            if (snapshot.isEmpty) return
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (snapshot.size() < DELETE_BATCH_SIZE) return
        }
    }

    companion object {
        private const val HOUSEHOLDS_COLLECTION = "households"
        private const val MAX_CODE_ATTEMPTS = 5

        // Kept comfortably under Firestore's 500-write-per-batch limit.
        private const val DELETE_BATCH_SIZE = 400

        // Every subcollection ever written under households/{id} — see each repository's
        // `collection(householdId, name)` helper. Keep in sync if a new one is added.
        private val SUBCOLLECTIONS = listOf("products", "inventory", "shoppingList", "activityLog", "scanHistory")

        // No 0/O or 1/I — easy to misread and easy to misdictate over the phone.
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** Public so the join-household UI can validate input length before even trying. */
        const val CODE_LENGTH = 6

        // The code is the household's only access control (see firestore.rules), so it's
        // generated with a CSPRNG rather than Kotlin's non-cryptographic default Random.
        private val secureRandom = java.security.SecureRandom()

        private fun generateCode(): String =
            (1..CODE_LENGTH).map { CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)] }.joinToString("")
    }
}
