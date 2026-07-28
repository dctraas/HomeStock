package com.dtraas.boodschapbeheer.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Something went wrong that isn't a plain network/Firestore exception. */
class HouseholdNotFoundException(code: String) : Exception("Huishouden met code $code niet gevonden")

/**
 * Creates and joins households — the mechanism by which multiple devices share the
 * same Firestore data. A household is just a document at `households/{code}`; knowing
 * the code is the only thing needed to join, there is no separate invite flow.
 */
class HouseholdRepository(
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
                ?: Result.failure(IllegalStateException("Kon geen unieke huishouden-code genereren"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Joins an existing household by its code, failing if no such household exists. */
    suspend fun joinHousehold(code: String): Result<String> {
        return try {
            ensureSignedIn()
            val normalized = code.trim().uppercase()
            if (normalized.isEmpty()) return Result.failure(HouseholdNotFoundException(normalized))
            val snapshot = firestore.collection(HOUSEHOLDS_COLLECTION).document(normalized).get().await()
            if (!snapshot.exists()) {
                Result.failure(HouseholdNotFoundException(normalized))
            } else {
                Result.success(normalized)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val HOUSEHOLDS_COLLECTION = "households"
        const val MAX_CODE_ATTEMPTS = 5

        // No 0/O or 1/I — easy to misread and easy to misdictate over the phone.
        const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 6

        fun generateCode(): String = (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")
    }
}
