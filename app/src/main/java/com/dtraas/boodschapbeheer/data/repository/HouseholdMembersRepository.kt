package com.dtraas.boodschapbeheer.data.repository

import com.dtraas.boodschapbeheer.data.remote.observeSnapshots
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

/**
 * Tracks which distinct devices belong to a household, at `households/{id}/members/{uid}`
 * (keyed by this device's Firebase Anonymous Auth uid — the only stable per-device
 * identifier the app has; see [HouseholdRepository.ensureSignedIn]). Backs two things: the
 * free-tier cap of [FREE_MEMBER_LIMIT] people per household, and household-wide Premium —
 * if any member has an active subscription, every device in that household is unlocked,
 * not just the one that paid.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdMembersRepository(
    private val firestore: FirebaseFirestore,
    private val householdSession: HouseholdSession,
    private val auth: FirebaseAuth,
    private val billingRepository: BillingRepository,
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private fun membersCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("members")

    init {
        // Keeps this device's own member doc's isPremium flag in sync with Play, so other
        // members see the household unlock as soon as anyone in it subscribes. Fires once
        // right away with whatever isPremium happens to be at that moment (often still
        // "false" pre-connect) and corrects itself the moment Play's real answer comes in.
        combine(householdSession.householdId, billingRepository.isPremium) { householdId, isPremium -> householdId to isPremium }
            .onEach { (householdId, isPremium) -> if (householdId != null) syncPremiumStatus(householdId, isPremium) }
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

    /** Registers this device as a household member. Call once, right after a create/join succeeds. */
    suspend fun registerCurrentDevice(householdId: String) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid)
            .set(mapOf("joinedAt" to System.currentTimeMillis(), "isPremium" to billingRepository.isPremium.value))
            .await()
    }

    suspend fun unregisterCurrentDevice() {
        val householdId = householdSession.householdId.value ?: return
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).delete().await()
    }

    private suspend fun syncPremiumStatus(householdId: String, isPremium: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        membersCollection(householdId).document(uid).set(mapOf("isPremium" to isPremium), SetOptions.merge()).await()
    }

    companion object {
        const val FREE_MEMBER_LIMIT = 2
    }
}
