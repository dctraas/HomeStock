package com.dtraas.boodschapbeheer.data.repository

import android.net.Uri
import com.dtraas.boodschapbeheer.data.remote.observeSnapshots
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await

/** One person linked to the current household — a row in the household members list. */
data class HouseholdMember(
    val uid: String,
    val displayName: String?,
    val photoUrl: String?,
    val isPremium: Boolean,
    val isCurrentDevice: Boolean,
)

/**
 * Tracks which distinct devices belong to a household, at `households/{id}/members/{uid}`
 * (keyed by this device's Firebase Anonymous Auth uid — the only stable per-device
 * identifier the app has; see [HouseholdRepository.ensureSignedIn]). Backs three things: the
 * free-tier cap of [FREE_MEMBER_LIMIT] people per household, household-wide Premium — if any
 * member has an active subscription, every device in that household is unlocked, not just
 * the one that paid — and the household members list (Instellingen > Huishouden), so
 * housemates can see who else is linked.
 *
 * [DeviceProfile]'s name/photo are otherwise purely local; syncing them here (name as plain
 * text, photo uploaded to Firebase Storage — a device's own photo file isn't reachable by
 * other devices) is what lets a housemate's name and photo show up on someone else's screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdMembersRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val householdSession: HouseholdSession,
    private val auth: FirebaseAuth,
    private val billingRepository: BillingRepository,
    private val deviceProfile: DeviceProfile,
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private fun membersCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("members")

    private fun photoRef(householdId: String, uid: String) =
        storage.reference.child("households/$householdId/members/$uid/photo.jpg")

    init {
        // Keeps this device's own member doc's isPremium flag in sync with Play, so other
        // members see the household unlock as soon as anyone in it subscribes. Fires once
        // right away with whatever isPremium happens to be at that moment (often still
        // "false" pre-connect) and corrects itself the moment Play's real answer comes in.
        combine(householdSession.householdId, billingRepository.isPremium) { householdId, isPremium -> householdId to isPremium }
            .onEach { (householdId, isPremium) -> if (householdId != null) syncPremiumStatus(householdId, isPremium) }
            .launchIn(repositoryScope)

        // Keeps this device's own member doc's displayName in sync with DeviceProfile —
        // only fires on an actual name change (or once on the household this device is
        // currently in), not the photo itself (that's comparatively expensive to re-upload,
        // so it's synced explicitly instead — see syncCurrentDevicePhoto).
        combine(householdSession.householdId, deviceProfile.displayName) { householdId, name -> householdId to name }
            .onEach { (householdId, name) -> if (householdId != null) syncDisplayName(householdId, name) }
            .launchIn(repositoryScope)
    }

    fun observeMemberCount(): Flow<Int> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) flowOf(0) else membersCollection(householdId).observeSnapshots().map { it.size() }
        }

    /** True if any device in the household — not necessarily this one — has an active subscription. */
    fun observeHouseholdIsPremium(): Flow<Boolean> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(false)
            } else {
                membersCollection(householdId).observeSnapshots().map { snapshot ->
                    snapshot.documents.any { it.getBoolean("isPremium") == true }
                }
            }
        }

    /** Everyone linked to the current household, for Instellingen > Huishouden. */
    fun observeMembers(): Flow<List<HouseholdMember>> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(emptyList())
            } else {
                membersCollection(householdId).observeSnapshots().map { snapshot ->
                    val currentUid = auth.currentUser?.uid
                    snapshot.documents
                        .sortedBy { it.getLong("joinedAt") ?: Long.MAX_VALUE }
                        .map { doc ->
                            HouseholdMember(
                                uid = doc.id,
                                displayName = doc.getString("displayName"),
                                photoUrl = doc.getString("photoUrl"),
                                isPremium = doc.getBoolean("isPremium") == true,
                                isCurrentDevice = doc.id == currentUid,
                            )
                        }
                }
            }
        }

    /**
     * Whether this device may join [householdId]: always true if it's already a member
     * (e.g. reinstalling the app), true while the household is under the free limit,
     * otherwise only if the household is already Premium via another member.
     */
    suspend fun canJoin(householdId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val snapshot = membersCollection(householdId).get().await()
        if (snapshot.documents.any { it.id == uid }) return true
        if (snapshot.size() < FREE_MEMBER_LIMIT) return true
        return snapshot.documents.any { it.getBoolean("isPremium") == true }
    }

    /**
     * Registers this device as a household member, and — since [DeviceProfile] is normally
     * only synced reactively on the *next* change (see [init] and [syncCurrentDevicePhoto]) —
     * seeds its current name/photo right away too, so a name/photo set during onboarding
     * (before a household even existed to sync into) shows up immediately. Call once, right
     * after a create/join succeeds.
     */
    suspend fun registerCurrentDevice(householdId: String) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid)
            .set(
                mapOf(
                    "joinedAt" to System.currentTimeMillis(),
                    "isPremium" to billingRepository.isPremium.value,
                    "displayName" to deviceProfile.displayName.value,
                ),
            )
            .await()
        syncCurrentDevicePhoto()
    }

    suspend fun unregisterCurrentDevice() {
        val householdId = householdSession.householdId.value ?: return
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).delete().await()
        runCatching { photoRef(householdId, uid).delete().await() }
    }

    /**
     * Uploads (or removes) this device's current profile photo for the household to see.
     * Unlike the name, this isn't synced reactively on every app start — re-uploading the
     * actual image file on every cold start would waste data for no benefit — so call this
     * explicitly right after [DeviceProfile.setPhotoFromUri] or [DeviceProfile.clearPhoto].
     * A failed upload (e.g. no connection) is silently skipped; it'll simply be retried the
     * next time the photo is changed.
     */
    suspend fun syncCurrentDevicePhoto() {
        val householdId = householdSession.householdId.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val localPath = deviceProfile.photoPath.value
        if (localPath == null) {
            // Deleting a Storage object that was never uploaded (no photo was ever set)
            // throws "not found" — harmless, and kept separate from the Firestore write
            // below so that write still runs even then, rather than a failed delete
            // silently skipping it.
            runCatching { photoRef(householdId, uid).delete().await() }
            runCatching {
                membersCollection(householdId).document(uid)
                    .set(mapOf("photoUrl" to FieldValue.delete()), SetOptions.merge()).await()
            }
        } else {
            runCatching {
                val uploadedUrl = photoRef(householdId, uid).putFile(Uri.fromFile(File(localPath))).await()
                    .storage.downloadUrl.await().toString()
                membersCollection(householdId).document(uid)
                    .set(mapOf("photoUrl" to uploadedUrl), SetOptions.merge()).await()
            }
        }
    }

    private suspend fun syncPremiumStatus(householdId: String, isPremium: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("isPremium" to isPremium), SetOptions.merge()).await()
    }

    private suspend fun syncDisplayName(householdId: String, name: String?) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("displayName" to name), SetOptions.merge()).await()
    }

    companion object {
        const val FREE_MEMBER_LIMIT = 2
    }
}
