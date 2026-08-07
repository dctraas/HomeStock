package com.dtraas.homestock.data.repository

/**
 * The homestock://join?code=XXXXXX deep link (see HouseholdSettingsScreen's "Deel uitnodiging"
 * button, which builds one, and MainActivity, which parses one back out of an incoming intent).
 * [SCHEME]/[HOST] must stay in sync with AndroidManifest.xml's matching `<data>` element by
 * hand — manifest placeholders can't reference Kotlin constants.
 */
object HouseholdInviteLink {
    const val SCHEME = "homestock"
    const val HOST = "join"
    private const val CODE_PARAM = "code"

    fun build(code: String): String = "$SCHEME://$HOST?$CODE_PARAM=$code"

    /** Extracts the household code from [uri], or null if it isn't a household invite link. */
    fun codeFrom(uri: android.net.Uri?): String? {
        if (uri == null || uri.scheme != SCHEME || uri.host != HOST) return null
        return uri.getQueryParameter(CODE_PARAM)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
    }
}
