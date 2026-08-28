package com.dtraas.homestock.ui.components

/** First letters of up to the first two words of [name], uppercased — "Jip de Vries" -> "JD".
 *  Falls back to an empty string for a blank/empty [name] (callers show an icon instead). Shared
 *  between MoreScreen's own profile row and HouseholdSettingsScreen's member avatars — used to
 *  be private to MoreScreen.kt, moved here once a second screen needed the exact same fallback. */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
