package com.dtraas.homestock.data.repository

import android.net.Uri
import android.util.Log
import com.android.billingclient.api.Purchase
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.remote.observeSnapshots
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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

/** Best-effort avatar lookup by [actorName] — activityLog/scan entries only ever stamped a plain
 *  name, not a uid, so this is an exact (trimmed) [HouseholdMember.displayName] match rather than
 *  a guaranteed join; a member who's renamed themselves since, or a name with incidental leading/
 *  trailing whitespace, simply falls back to whatever placeholder the caller already shows for an
 *  unmatched name. Shared by NotificationsScreen's activity feed and Statistieken's "Wie doet
 *  wat" cards — was duplicated inline in both before, with no trimming, which meant it silently
 *  stopped matching for a display name saved with (or later gaining) surrounding whitespace. */
fun List<HouseholdMember>.photoUrlFor(actorName: String?): String? =
    actorName?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { name -> firstOrNull { it.displayName?.trim() == name }?.photoUrl }

/** What [HouseholdMembersRepository.canJoin] decided — Premium lifts the member cap entirely
 *  (see [HouseholdCapacityInfo]), so the only way a join can be blocked at all now is the
 *  free-tier cap. */
enum class HouseholdJoinResult { ALLOWED, BLOCKED_FREE_LIMIT }

/** Instellingen > Huishouden's view of how full the household is, and why — [limit] is `null`
 *  once the household is Premium (that tier has no member cap at all); otherwise it's
 *  [HouseholdMembersRepository.FREE_MEMBER_LIMIT], so the UI can show "X / Y leden". */
data class HouseholdCapacityInfo(
    val memberCount: Int,
    val limit: Int?,
    val isPremium: Boolean,
) {
    val isAtOrNearLimit: Boolean get() = limit != null && memberCount >= limit - 1
}

/**
 * Tracks which distinct devices belong to a household, at `households/{id}/members/{uid}`
 * (keyed by this device's Firebase Anonymous Auth uid — the only stable per-device
 * identifier the app has; see [HouseholdRepository.ensureSignedIn]). Backs three things: the
 * free-tier cap of [FREE_MEMBER_LIMIT] people per household; the fact that Premium lifts that
 * cap entirely, no separate cap tier or add-on purchase involved (see [PremiumPlan]'s doc for
 * why); and the household members list (Instellingen > Huishouden), so housemates can see who
 * else is linked.
 *
 * The Premium unlock is household-wide — if any member has an active subscription, every
 * device in that household gets it, not just the one that paid.
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
    private val analyticsRepository: AnalyticsRepository,
    private val functions: FirebaseFunctions,
    private val firebaseMessaging: FirebaseMessaging,
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

        // Best-effort server-side confirmation of each active purchase, via the
        // `verifyPurchase` Cloud Function — see [BillingRepository.activePurchases]' doc for
        // why this exists alongside (not instead of, for now) the client-derived write above.
        combine(householdSession.householdId, billingRepository.activePurchases) { householdId, purchases -> householdId to purchases }
            .onEach { (householdId, purchases) -> if (householdId != null) verifyPurchases(householdId, purchases) }
            .launchIn(repositoryScope)

        // Keeps this device's own member doc's displayName in sync with DeviceProfile —
        // only fires on an actual name change (or once on the household this device is
        // currently in), not the photo itself (that's comparatively expensive to re-upload,
        // so it's synced explicitly instead — see syncCurrentDevicePhoto).
        combine(householdSession.householdId, deviceProfile.displayName) { householdId, name -> householdId to name }
            .onEach { (householdId, name) -> if (householdId != null) syncDisplayName(householdId, name) }
            .launchIn(repositoryScope)

        // Keeps this device's own member doc's fcmToken in sync whenever the active household
        // changes (join/switch) — [updateFcmToken] alone only fires when FCM actually
        // (re)issues a token, not when this device joins/switches to a household with an
        // already-issued token. Backs the real-time cross-device pushes in
        // functions/src/index.ts (huisgenoot-activiteit, huishouden-wijziging) — see
        // [com.dtraas.homestock.messaging.HomeStockMessagingService].
        householdSession.householdId.filterNotNull()
            .onEach { householdId ->
                runCatching { firebaseMessaging.token.await() }.getOrNull()?.let { token -> syncFcmToken(householdId, token) }
            }
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

    /** See [HouseholdCapacityInfo] — everything Instellingen > Huishouden needs to show the
     *  current member cap (or that there isn't one, once Premium). */
    fun observeCapacityInfo(): Flow<HouseholdCapacityInfo> =
        householdSession.householdId.flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf(HouseholdCapacityInfo(memberCount = 0, limit = FREE_MEMBER_LIMIT, isPremium = false))
            } else {
                membersCollection(householdId).observeSnapshots().map { snapshot ->
                    val isPremium = snapshot.documents.any { it.getBoolean("isPremium") == true }
                    HouseholdCapacityInfo(
                        memberCount = snapshot.size(),
                        limit = if (isPremium) null else FREE_MEMBER_LIMIT,
                        isPremium = isPremium,
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
     * already a member (e.g. reinstalling the app), the household is under [FREE_MEMBER_LIMIT],
     * or the household is Premium (no cap at all — see [HouseholdCapacityInfo]). Otherwise
     * [HouseholdJoinResult.BLOCKED_FREE_LIMIT] — also logs the matching analytics event, since
     * a blocked join is the clearest possible "show this household the Premium upsell" signal.
     */
    suspend fun canJoin(householdId: String): HouseholdJoinResult {
        val uid = auth.currentUser?.uid ?: return HouseholdJoinResult.BLOCKED_FREE_LIMIT
        val snapshot = membersCollection(householdId).get().await()
        if (snapshot.documents.any { it.id == uid }) return HouseholdJoinResult.ALLOWED

        val isHouseholdPremium = snapshot.documents.any { it.getBoolean("isPremium") == true }
        if (isHouseholdPremium) return HouseholdJoinResult.ALLOWED

        if (snapshot.size() < FREE_MEMBER_LIMIT) return HouseholdJoinResult.ALLOWED

        analyticsRepository.logHouseholdJoinBlockedFreeLimit()
        return HouseholdJoinResult.BLOCKED_FREE_LIMIT
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
     * A failed upload (e.g. no connection, or the Storage bucket not actually provisioned in
     * the Firebase console — a manual one-time setup step easy to miss) is logged (Logcat, tag
     * [TAG]) rather than surfaced to the household — there's no obvious place in the profile
     * dialog's flow to show an error for a background sync — but it used to be silently
     * swallowed with no trace at all, which made "why does Activiteit show the wrong thing"
     * impossible to actually diagnose. It'll simply be retried the next time the photo changes.
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
            }.onFailure { e -> Log.w(TAG, "syncCurrentDevicePhoto: clearing photoUrl failed", e) }
        } else {
            runCatching {
                val uploadedUrl = photoRef(householdId, uid).putFile(Uri.fromFile(File(localPath))).await()
                    .storage.downloadUrl.await().toString()
                membersCollection(householdId).document(uid)
                    .set(mapOf("photoUrl" to uploadedUrl), SetOptions.merge()).await()
            }.onFailure { e -> Log.w(TAG, "syncCurrentDevicePhoto: upload/write failed", e) }
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

    /** Asks the `verifyPurchase` Cloud Function to confirm each of [purchases] directly with
     *  Google and, on success, overwrite this uid's member doc with the verified result — see
     *  [BillingRepository.activePurchases]' doc for the full rationale. Best-effort: any
     *  failure (offline, or the Play Console access `verifyPurchase` needs not being granted
     *  yet — see its doc in functions/src/index.ts) is silently swallowed, leaving
     *  [syncPremiumStatus]'s client-derived write as the only source for [isPremium] until it
     *  succeeds, exactly as it always has been. */
    private suspend fun verifyPurchases(householdId: String, purchases: List<Purchase>) {
        for (purchase in purchases) {
            val productId = purchase.products.firstOrNull() ?: continue
            if (productId != BillingRepository.PREMIUM_MONTHLY_PRODUCT_ID && productId != BillingRepository.PREMIUM_YEARLY_PRODUCT_ID) continue
            runCatching {
                val requestData = hashMapOf(
                    "householdId" to householdId,
                    "productId" to productId,
                    "purchaseToken" to purchase.purchaseToken,
                )
                functions.getHttpsCallable("verifyPurchase").call(requestData).await()
            }
        }
    }

    private suspend fun syncDisplayName(householdId: String, name: String?) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("displayName" to name), SetOptions.merge()).await()
    }

    private suspend fun syncFcmToken(householdId: String, token: String) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
    }

    /** Called by [com.dtraas.homestock.messaging.HomeStockMessagingService] whenever FCM issues
     *  this device a new token — written to the currently active household's member doc; see
     *  [init]'s own token sync above for the complementary "token unchanged, active household
     *  changed" case this alone doesn't cover. A no-op while this device isn't in a household
     *  yet (the token is picked up by the [init] sync as soon as it joins one). */
    suspend fun updateFcmToken(token: String) {
        val householdId = householdSession.householdId.value ?: return
        syncFcmToken(householdId, token)
    }

    companion object {
        const val FREE_MEMBER_LIMIT = 2
        private const val TAG = "HouseholdMembersRepo"
    }
}
