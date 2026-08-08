package com.dtraas.homestock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.dtraas.homestock.data.repository.HouseholdInviteLink
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.household.HouseholdScreen
import com.dtraas.homestock.ui.navigation.Destination
import com.dtraas.homestock.ui.navigation.HomeStockApp
import com.dtraas.homestock.ui.theme.HomeStockTheme
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor

// AppCompatActivity (rather than plain ComponentActivity) is required for
// AppCompatDelegate.setApplicationLocales to recreate this activity with the
// newly chosen language (Instellingen > Algemeen > Taal).
class MainActivity : AppCompatActivity() {

    // A plain Compose MutableState works fine as an Activity field — it's snapshot-aware
    // regardless of where it's declared, so reading it inside setContent's composable still
    // recomposes when onNewIntent updates it. Backs both launcher shortcuts (see shortcuts.xml)
    // and, later, "resume where a household invite link pointed" while the app is already
    // running (singleTop launchMode, so re-tapping a shortcut/link reuses this instance
    // instead of spawning a new one).
    private var pendingRoute by mutableStateOf<String?>(null)

    // From a homestock://join?code=XXXXXX link (see HouseholdInviteLink and
    // HouseholdSettingsScreen's "Deel uitnodiging" button) — only meaningful while this device
    // isn't in a household yet, since that's the only time HouseholdScreen is shown at all (see
    // setContent below). Read once by HouseholdScreen/HouseholdViewModel to prefill the join
    // step; not consumed/cleared the way pendingRoute is; there's nothing to conflict with.
    private var pendingJoinCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = shortcutRouteForIntent(intent)
        pendingJoinCode = HouseholdInviteLink.codeFrom(intent.data)
        setContent {
            val application = LocalContext.current.applicationContext as HomeStockApplication
            val themeMode by application.container.themePreferences.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HomeStockTheme(darkTheme = darkTheme) {
                // enableEdgeToEdge() alone draws app content behind a transparent status
                // bar and relies on each top app bar's own background to show through —
                // in practice that band can stop short of the physical top edge on some
                // devices/OS versions. Setting the system status bar color explicitly to
                // the same tone the top app bar uses (see HomeStockTopAppBar) guarantees
                // the color reaches all the way up, regardless of that quirk.
                val topAppBarContainerColor = LocalTopAppBarContainerColor.current
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                SideEffect {
                    window.statusBarColor = topAppBarContainerColor.toArgb()
                    // Derived from the bar's own luminance rather than just darkTheme: the
                    // bar is now a full-strength saturated sage in both themes, dark enough
                    // to need light (white) status bar icons either way — but dynamic color
                    // can still land on a pale, wallpaper-derived tone that needs dark icons.
                    insetsController.isAppearanceLightStatusBars = topAppBarContainerColor.luminance() > 0.5f
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    val householdId by application.container.householdSession.householdId.collectAsState()
                    if (householdId == null) {
                        HouseholdScreen(prefillJoinCode = pendingJoinCode)
                    } else {
                        HomeStockApp(
                            pendingRoute = pendingRoute,
                            onPendingRouteConsumed = { pendingRoute = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutRouteForIntent(intent)?.let { pendingRoute = it }
        HouseholdInviteLink.codeFrom(intent.data)?.let { pendingJoinCode = it }
    }

    private fun shortcutRouteForIntent(intent: Intent?): String? = when (intent?.action) {
        ACTION_SHORTCUT_SCAN -> Destination.Scan.route
        ACTION_SHORTCUT_SHOPPING_LIST -> Destination.ShoppingList.route
        else -> null
    }

    companion object {
        const val ACTION_SHORTCUT_SCAN = "com.dtraas.homestock.action.SHORTCUT_SCAN"
        const val ACTION_SHORTCUT_SHOPPING_LIST = "com.dtraas.homestock.action.SHORTCUT_SHOPPING_LIST"
    }
}
