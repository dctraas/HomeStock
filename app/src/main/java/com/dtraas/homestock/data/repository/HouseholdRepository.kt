package com.dtraas.homestock.data.repository

import android.content.Context
import android.net.Uri
import com.dtraas.homestock.R
import com.dtraas.homestock.data.remote.observeSnapshot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val householdSession: HouseholdSession,
) {
    private fun photoRef(householdId: String) =
        storage.reference.child("households/$householdId/photo.jpg")

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    /** Generates a fresh, unused household code and creates its document with [name] as its title. */
    suspend fun createHousehold(name: String): Result<String> {
        return try {
            ensureSignedIn()
            val trimmedName = name.trim().take(HOUSEHOLD_NAME_MAX_LENGTH)
            var createdCode: String? = null
            repeat(MAX_CODE_ATTEMPTS) {
                if (createdCode != null) return@repeat
                val code = generateCode()
                val doc = firestore.collection(HOUSEHOLDS_COLLECTION).document(code)
                if (!doc.get().await().exists()) {
                    doc.set(mapOf("createdAt" to System.currentTimeMillis(), FIELD_NAME to trimmedName)).await()
                    createdCode = code
                }
            }
            createdCode?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException(context.getString(R.string.household_generate_code_failed)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Renames an existing household (Instellingen > Huishouden > naam wijzigen). */
    suspend fun renameHousehold(householdId: String, name: String): Result<Unit> {
        val trimmedName = name.trim().take(HOUSEHOLD_NAME_MAX_LENGTH)
        if (trimmedName.isEmpty()) return Result.failure(IllegalArgumentException("Naam mag niet leeg zijn"))
        return try {
            firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).update(FIELD_NAME, trimmedName).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The current household's name, live — shown as the Voorraad screen's title instead of
     * a generic label. Households created before this field existed have no [FIELD_NAME], so
     * this can resolve to null; callers fall back to a generic title in that case.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHouseholdName(): Flow<String?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).observeSnapshot()
                    .map { it.getString(FIELD_NAME) }
            }
        }

    /**
     * The current household's photo, live — shown in Instellingen > Huishouden and, once set,
     * behind/beside the household name elsewhere in the app. Most households have none, in
     * which case this resolves to null and the UI falls back to a plain placeholder icon.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHouseholdPhoto(): Flow<String?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).observeSnapshot()
                    .map { it.getString(FIELD_PHOTO_URL) }
            }
        }

    /**
     * Uploads [sourceUri] (from the system Photo Picker) as the household's shared photo,
     * overwriting whatever was there before — one photo per household, not per device, unlike
     * [DeviceProfile]'s own local one. Any household member can set/replace it; there's no
     * separate permission tier for this, same as renaming the household.
     */
    suspend fun uploadHouseholdPhoto(householdId: String, sourceUri: Uri): Result<Unit> = try {
        val downloadUrl = photoRef(householdId).putFile(sourceUri).await().storage.downloadUrl.await().toString()
        firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            .set(mapOf(FIELD_PHOTO_URL to downloadUrl), SetOptions.merge()).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Removes the household's shared photo, reverting the UI to its plain placeholder. */
    suspend fun removeHouseholdPhoto(householdId: String): Result<Unit> = try {
        // Deleting a Storage object that was never uploaded throws "not found" — harmless, and
        // kept separate from the Firestore write below so that write still runs even then.
        runCatching { photoRef(householdId).delete().await() }
        firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId)
            .set(mapOf(FIELD_PHOTO_URL to FieldValue.delete()), SetOptions.merge()).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Joins an existing household by its code, failing if no such household exists. */
    suspend fun joinHousehold(code: String): Result<String> {
        return try {
            ensureSignedIn()
            val normalized = code.trim().uppercase()
            // Rejects anything that isn't a plausible generateCode() output *before* it ever
            // reaches .document(normalized) — Firestore's CollectionReference.document(path)
            // treats "/" in the string as path separators, not literal characters, so an
            // unvalidated code (typed by hand, or from a crafted homestock://join deep link)
            // could otherwise resolve to some other document nested under households/ instead
            // of a top-level household — see firestore.rules for why that alone doesn't cross a
            // real permission boundary, but the app should never treat an arbitrary nested doc
            // as if it were a household.
            if (normalized.isEmpty() || normalized.length != CODE_LENGTH || normalized.any { it !in CODE_CHARS }) {
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
        private val SUBCOLLECTIONS =
            listOf("products", "inventory", "shoppingList", "activityLog", "scanHistory", "stores", "members", "mealPlan")

        // No 0/O or 1/I — easy to misread and easy to misdictate over the phone.
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** Public so the join-household UI can validate input length before even trying. */
        const val CODE_LENGTH = 6

        // Kept short enough to stay readable as the Voorraad screen's title.
        const val HOUSEHOLD_NAME_MAX_LENGTH = 24

        private const val FIELD_NAME = "name"
        private const val FIELD_PHOTO_URL = "photoUrl"

        // The code is the household's only access control (see firestore.rules), so it's
        // generated with a CSPRNG rather than Kotlin's non-cryptographic default Random.
        private val secureRandom = java.security.SecureRandom()

        private fun generateCode(): String =
            (1..CODE_LENGTH).map { CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)] }.joinToString("")
    }
}
