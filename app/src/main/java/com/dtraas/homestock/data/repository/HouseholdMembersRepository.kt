package com.dtraas.homestock.data.repository

import android.net.Uri
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.remote.observeSnapshots
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
    // Allergens/dietary exclusions this member set for themselves (see updateExcludedAllergens)
    // — only ever writable by that member's own device, readable by the whole household so
    // recipe suggestions can steer clear of everyone's restrictions at once.
    val excludedAllergens: Set<Allergen> = emptySet(),
)

/** What [HouseholdMembersRepository.canJoin] decided, and why — the "why" is what lets
 *  HouseholdViewModel show a different message (and, for the Premium case, a different
 *  upsell) for "this household isn't Premium yet" versus "this household is Premium but has
 *  hit its member cap and hasn't bought the unlimited-members add-on". */
enum class HouseholdJoinResult { ALLOWED, BLOCKED_FREE_LIMIT, BLOCKED_PREMIUM_CAP }

/** Instellingen > Huishouden's view of how full the household is, and why — [limit] is `null`
 *  when there effectively is no cap (the unlimited-members add-on is owned); otherwise it's
 *  whichever cap currently applies ([HouseholdMembersRepository.FREE_MEMBER_LIMIT] or the
 *  Premium cap from [RemoteConfigRepository.premiumMemberCap]), so the UI can show "X / Y
 *  leden" and, once premium and close to or at that cap, nudge toward the add-on. */
data class HouseholdCapacityInfo(
    val memberCount: Int,
    val limit: Int?,
    val isPremium: Boolean,
    val hasUnlimitedMembers: Boolean,
) {
    val isAtOrNearLimit: Boolean get() = limit != null && memberCount >= limit - 1
}

/**
 * Tracks which distinct devices belong to a household, at `households/{id}/members/{uid}`
 * (keyed by this device's Firebase Anonymous Auth uid — the only stable per-device
 * identifier the app has; see [HouseholdRepository.ensureSignedIn]). Backs four things: the
 * free-tier cap of [FREE_MEMBER_LIMIT] people per household; the higher Premium cap (from
 * [RemoteConfigRepository.premiumMemberCap]) once any member has an active subscription or
 * owns the lifetime purchase; the fact that a household that's bought the one-time
 * "Onbeperkt huisgenoten" add-on has no cap at all; and the household members list
 * (Instellingen > Huishouden), so housemates can see who else is linked.
 *
 * Both the subscription/lifetime Premium unlock and the unlimited-members add-on are
 * household-wide — if any member has either, every device in that household gets it, not
 * just the one that paid.
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
    private val remoteConfigRepository: RemoteConfigRepository,
    private val analyticsRepository: AnalyticsRepository,
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

        // Same idea, for the "Onbeperkt huisgenoten" add-on — lets every device in the
        // household see the cap lifted as soon as anyone buys it, not just the buyer.
        combine(householdSession.householdId, billingRepository.hasUnlimitedMembersAddon) { householdId, hasAddon -> householdId to hasAddon }
            .onEach { (householdId, hasAddon) -> if (householdId != null) syncUnlimitedMembersStatus(householdId, hasAddon) }
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

    /** True if any device in the household owns the "Onbeperkt huisgenoten" add-on. */
    fun observeHouseholdHasUnlimitedMembers(): Flow<Boolean> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(false)
            } else {
                membersCollection(householdId).observeSnapshots().map { snapshot ->
                    snapshot.documents.any { it.getBoolean("hasUnlimitedMembers") == true }
                }
            }
        }

    /** See [HouseholdCapacityInfo] — everything Instellingen > Huishouden needs to show the
     *  current member cap and, once relevant, nudge toward the unlimited-members add-on. */
    fun observeCapacityInfo(): Flow<HouseholdCapacityInfo> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(HouseholdCapacityInfo(memberCount = 0, limit = FREE_MEMBER_LIMIT, isPremium = false, hasUnlimitedMembers = false))
            } else {
                combine(
                    membersCollection(householdId).observeSnapshots(),
                    remoteConfigRepository.premiumMemberCap,
                ) { snapshot, premiumCap ->
                    val isPremium = snapshot.documents.any { it.getBoolean("isPremium") == true }
                    val hasUnlimitedMembers = snapshot.documents.any { it.getBoolean("hasUnlimitedMembers") == true }
                    HouseholdCapacityInfo(
                        memberCount = snapshot.size(),
                        limit = when {
                            hasUnlimitedMembers -> null
                            isPremium -> premiumCap.toInt()
                            else -> FREE_MEMBER_LIMIT
                        },
                        isPremium = isPremium,
                        hasUnlimitedMembers = hasUnlimitedMembers,
                    )
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
                            @Suppress("UNCHECKED_CAST")
                            val allergenKeys = doc.get("excludedAllergens") as? List<String> ?: emptyList()
                            HouseholdMember(
                                uid = doc.id,
                                displayName = doc.getString("displayName"),
                                photoUrl = doc.getString("photoUrl"),
                                isPremium = doc.getBoolean("isPremium") == true,
                                isCurrentDevice = doc.id == currentUid,
                                excludedAllergens = allergenKeys.mapNotNullTo(mutableSetOf()) { key ->
                                    runCatching { Allergen.valueOf(key) }.getOrNull()
                                },
                            )
                        }
                }
            }
        }

    /**
     * Whether this device may join [householdId]: always [HouseholdJoinResult.ALLOWED] if it's
     * already a member (e.g. reinstalling the app) or the household is under whichever cap
     * currently applies to it — see [HouseholdCapacityInfo]. Otherwise [BLOCKED_FREE_LIMIT] (not
     * Premium at all yet) or [BLOCKED_PREMIUM_CAP] (Premium, but at its member cap without the
     * unlimited-members add-on) — also logs the matching analytics event, since a join blocked
     * by the Premium cap is the clearest possible "show this household the add-on" signal.
     */
    suspend fun canJoin(householdId: String): HouseholdJoinResult {
        val uid = auth.currentUser?.uid ?: return HouseholdJoinResult.BLOCKED_FREE_LIMIT
        val snapshot = membersCollection(householdId).get().await()
        if (snapshot.documents.any { it.id == uid }) return HouseholdJoinResult.ALLOWED

        val memberCount = snapshot.size()
        if (memberCount < FREE_MEMBER_LIMIT) return HouseholdJoinResult.ALLOWED

        val isHouseholdPremium = snapshot.documents.any { it.getBoolean("isPremium") == true }
        if (!isHouseholdPremium) {
            analyticsRepository.logHouseholdJoinBlockedFreeLimit()
            return HouseholdJoinResult.BLOCKED_FREE_LIMIT
        }

        val hasUnlimitedMembers = snapshot.documents.any { it.getBoolean("hasUnlimitedMembers") == true }
        if (hasUnlimitedMembers) return HouseholdJoinResult.ALLOWED

        if (memberCount < remoteConfigRepository.premiumMemberCap.value) return HouseholdJoinResult.ALLOWED

        analyticsRepository.logHouseholdJoinBlockedPremiumCap()
        return HouseholdJoinResult.BLOCKED_PREMIUM_CAP
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
                    "hasUnlimitedMembers" to billingRepository.hasUnlimitedMembersAddon.value,
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

    /** This device's own allergens/dietary exclusions, saved so the rest of the household can
     *  see them (see [HouseholdMember.excludedAllergens]) — never writes another member's doc. */
    suspend fun updateExcludedAllergens(allergens: Set<Allergen>) {
        val householdId = householdSession.householdId.value ?: return
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid)
            .set(mapOf("excludedAllergens" to allergens.map { it.name }), SetOptions.merge())
            .await()
    }

    /** Union of every member's own [HouseholdMember.excludedAllergens] — the household-wide
     *  default recipe suggestions should steer clear of, so nobody has to remember to exclude
     *  a housemate's allergen by hand every time they browse recipes. */
    fun observeHouseholdExcludedAllergens(): Flow<Set<Allergen>> =
        observeMembers().map { members -> members.flatMapTo(mutableSetOf()) { it.excludedAllergens } }

    private suspend fun syncPremiumStatus(householdId: String, isPremium: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("isPremium" to isPremium), SetOptions.merge()).await()
    }

    private suspend fun syncUnlimitedMembersStatus(householdId: String, hasAddon: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("hasUnlimitedMembers" to hasAddon), SetOptions.merge()).await()
    }

    private suspend fun syncDisplayName(householdId: String, name: String?) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("displayName" to name), SetOptions.merge()).await()
    }

    companion object {
        const val FREE_MEMBER_LIMIT = 2
    }
}
