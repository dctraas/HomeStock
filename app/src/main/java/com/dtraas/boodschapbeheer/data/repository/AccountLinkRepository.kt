package com.dtraas.boodschapbeheer.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Upgrades the app's anonymous Firebase Auth session (see [HouseholdRepository.ensureSignedIn])
 * to a permanent, Google-backed account — without losing any data. Firebase's account linking
 * keeps the same UID, it just attaches a second, non-anonymous credential to it; every
 * household/product/inventory document already scoped to this device stays exactly as is.
 *
 * Without linking, uninstalling the app or switching devices loses access to whichever
 * household this device was signed into: an anonymous session has nothing else identifying
 * it, and there's no "forgot password"-style recovery for it.
 */
class AccountLinkRepository(context: Context, private val auth: FirebaseAuth) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether the one-time "koppel je account" prompt (shown right after creating/joining a
     * household — see BoodschapBeheerApp) has already been offered on this device. Set once
     * the prompt is shown, regardless of whether the user acts on it or dismisses it — it's
     * meant as a single nudge, not a recurring nag; the Meer > Account koppelen row is the
     * permanent, always-available way to link later.
     */
    val hasShownLinkPrompt: Boolean
        get() = prefs.getBoolean(KEY_HAS_SHOWN_PROMPT, false)

    fun markLinkPromptShown() {
        prefs.edit().putBoolean(KEY_HAS_SHOWN_PROMPT, true).apply()
    }

    // Checked via providerData rather than FirebaseUser.isAnonymous: isAnonymous is meant to
    // answer "was this account ever claimed by a real identity", not "is a Google credential
    // attached right now" — whether it reverts to true after unlinkGoogleAccount() isn't
    // something Firebase documents, so relying on it here would risk this screen quietly
    // showing "linked" right after a successful unlink. Checking providerData for the specific
    // provider is exactly what both linking and unlinking actually add/remove, so it can't drift.
    private fun isGoogleLinked(user: FirebaseUser?): Boolean =
        user?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    /** True once this device's session has a Google credential attached. */
    fun observeIsLinked(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(isGoogleLinked(it.currentUser)) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** The linked Google account's email, or null if not linked. */
    val linkedEmail: String?
        get() = auth.currentUser?.providerData?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }?.email

    /**
     * Links the current anonymous session to the Google account identified by [idToken].
     *
     * Can fail with [com.google.firebase.auth.FirebaseAuthUserCollisionException] if that
     * Google account is already linked to a *different* Firebase user — e.g. this person
     * already linked it on another device or in another household. There's deliberately no
     * automatic merge for that case: silently moving this device's household into someone
     * else's account (or vice versa) would be surprising and hard to undo. The caller shows
     * a clear explanation instead (see AccountLinkScreen) and leaves both sessions untouched.
     */
    suspend fun linkWithGoogleIdToken(idToken: String): Result<Unit> = try {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        user.linkWithCredential(credential).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Removes the Google credential from this session, reverting it to anonymous-only. This
     * undoes the protection linking provided — after this, uninstalling the app or switching
     * devices loses access to the household again, same as before ever linking (see
     * AccountLinkScreen's confirmation dialog, which explains that before calling this).
     */
    suspend fun unlinkGoogleAccount(): Result<Unit> = try {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        user.unlink(GoogleAuthProvider.PROVIDER_ID).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val PREFS_NAME = "account_link"
        const val KEY_HAS_SHOWN_PROMPT = "has_shown_link_prompt"
    }
}
