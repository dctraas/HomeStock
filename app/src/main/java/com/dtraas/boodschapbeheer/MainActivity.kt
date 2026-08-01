package com.dtraas.boodschapbeheer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dtraas.boodschapbeheer.data.repository.ThemeMode
import com.dtraas.boodschapbeheer.ui.household.HouseholdScreen
import com.dtraas.boodschapbeheer.ui.navigation.BoodschapBeheerApp
import com.dtraas.boodschapbeheer.ui.theme.BoodschapBeheerTheme

// AppCompatActivity (rather than plain ComponentActivity) is required for
// AppCompatDelegate.setApplicationLocales to recreate this activity with the
// newly chosen language (Instellingen > Algemeen > Taal).
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = LocalContext.current.applicationContext as BoodschapBeheerApplication
            val themeMode by application.container.themePreferences.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BoodschapBeheerTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val householdId by application.container.householdSession.householdId.collectAsState()
                    if (householdId == null) {
                        HouseholdScreen()
                    } else {
                        BoodschapBeheerApp()
                    }
                }
            }
        }
    }
}
