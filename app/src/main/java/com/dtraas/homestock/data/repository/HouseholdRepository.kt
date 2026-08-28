package com.dtraas.homestock.data.repository

import android.content.Context
import com.dtraas.homestock.R
import com.dtraas.homestock.data.remote.observeSnapshot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Something went wrong that isn't a plain network/Firestore exception. */
class HouseholdNotFoundException(message: String) : Exception(message)

/** The code/link used to join no longer works — see [HouseholdRepository.refreshInviteExpiry]
 *  and [HouseholdRepository.joinHousehold]'s expiry check. Doesn't apply to a device that's
 *  already a member (e.g. reinstalling, or switching back via [HouseholdRepository.joinHousehold]
 *  from the household switcher) — only to a *new* join attempted after the invite went stale. */
class HouseholdInviteExpiredException(message: String) : Exception(message)

/**
 * Creates and joins households — the mechanism by which multiple devices share the
 * same Firestore data. A household is just a document at `households/{code}`; knowing
 * the code is the only thing needed to join, there is no separate invite flow.
 */
class HouseholdRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val householdSession: HouseholdSession,
) {
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
                    doc.set(
                        mapOf(
                            "createdAt" to System.currentTimeMillis(),
                            FIELD_NAME to trimmedName,
                            // Backs HouseholdSettingsScreen's "EIGENAAR" badge — see
                            // observeHouseholdCreatedBy's doc for why a household created before
                            // this field existed simply shows no owner rather than a wrong one.
                            "createdBy" to auth.currentUser?.uid,
                        ),
                    ).await()
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
     * The household's self-set monthly food-waste budget (Inzicht & Verspilling's "doel: onder
     * €X") — null until a member sets one via [setWasteGoal], in which case the screen shows a
     * "stel doel in" prompt instead of a target.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWasteGoal(): Flow<Double?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).observeSnapshot()
                    .map { it.getDouble(FIELD_WASTE_GOAL) }
            }
        }

    /** Sets (or, with `goal = null`, clears) the household's monthly waste-value budget. */
    suspend fun setWasteGoal(householdId: String, goal: Double?): Result<Unit> = try {
        firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).update(FIELD_WASTE_GOAL, goal).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * When this household was created — the delete-confirmation sheet's "DIT VERDWIJNT" card
     * turns this into "X maanden geschiedenis" so the count is real, not a guess. Households
     * created before `createdAt` existed have no such field, hence the nullable return.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHouseholdCreatedAt(): Flow<Long?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).observeSnapshot()
                    .map { it.getLong("createdAt") }
            }
        }

    /**
     * The uid of whichever device created this household — HouseholdSettingsScreen compares this
     * against each [HouseholdMember.uid] to show its "EIGENAAR" badge. Null both for a household
     * created before this field existed (see [createHousehold]) and while no household is
     * selected — either way, no member shows the badge rather than guessing one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHouseholdCreatedBy(): Flow<String?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).observeSnapshot()
                    .map { it.getString("createdBy") }
            }
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
            val household = firestore.collection(HOUSEHOLDS_COLLECTION).document(normalized)
            val snapshot = household.get().await()
            if (!snapshot.exists()) {
                return Result.failure(HouseholdNotFoundException(context.getString(R.string.household_not_found_format, normalized)))
            }

            // A stale invite only blocks a *new* member — a device that's already registered
            // (reinstalling, or the household switcher rejoining a household this device left
            // and came back to) always gets back in, exactly as before this check existed.
            val inviteExpiresAt = snapshot.getLong(FIELD_INVITE_EXPIRES_AT)
            if (inviteExpiresAt != null && inviteExpiresAt < System.currentTimeMillis()) {
                val uid = auth.currentUser?.uid
                val alreadyMember = uid != null && household.collection("members").document(uid).get().await().exists()
                if (!alreadyMember) {
                    return Result.failure(HouseholdInviteExpiredException(context.getString(R.string.household_invite_expired_error)))
                }
            }

            Result.success(normalized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Live "geldig tot" timestamp for the current household's invite link (Instellingen >
     * Huishouden) — millis since epoch, or null once no invite has ever been shared (a
     * household created before this existed, or one that's never tapped "Deel uitnodiging"),
     * in which case there's nothing to enforce and its code works to join with indefinitely,
     * exactly as it always has.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInviteExpiresAt(): Flow<Long?> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(null)
            } else {
                firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId).observeSnapshot()
                    .map { it.getLong(FIELD_INVITE_EXPIRES_AT) }
            }
        }

    /**
     * Pushes the invite link's expiry [INVITE_VALIDITY_DAYS] days out from now — called right
     * before the "Deel uitnodiging" share sheet opens (so a freshly shared link is never already
     * stale) and by the "Vernieuwen" affordance shown once one has gone stale. Doesn't change the
     * household's code itself (that stays permanent, see the class doc) — only how long a *new*
     * join with it keeps working; see [joinHousehold].
     */
    suspend fun refreshInviteExpiry(householdId: String): Result<Long> {
        val expiresAt = System.currentTimeMillis() + INVITE_VALIDITY_DAYS * 24L * 60 * 60 * 1000
        return try {
            firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId)
                .update(FIELD_INVITE_EXPIRES_AT, expiresAt).await()
            Result.success(expiresAt)
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
        private const val FIELD_INVITE_EXPIRES_AT = "inviteExpiresAt"
        private const val FIELD_WASTE_GOAL = "wasteGoal"

        /** How long a freshly (re)shared invite link keeps working for a new join — see
         *  [refreshInviteExpiry]. */
        const val INVITE_VALIDITY_DAYS = 7

        // The code is the household's only access control (see firestore.rules), so it's
        // generated with a CSPRNG rather than Kotlin's non-cryptographic default Random.
        private val secureRandom = java.security.SecureRandom()

        private fun generateCode(): String =
            (1..CODE_LENGTH).map { CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)] }.joinToString("")
    }
}
