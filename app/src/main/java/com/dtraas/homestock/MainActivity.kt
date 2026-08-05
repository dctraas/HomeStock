package com.dtraas.homestock

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
import com.dtraas.homestock.data.repository.ThemeMode
import com.dtraas.homestock.ui.household.HouseholdScreen
import com.dtraas.homestock.ui.navigation.HomeStockApp
import com.dtraas.homestock.ui.theme.HomeStockTheme

// AppCompatActivity (rather than plain ComponentActivity) is required for
// AppCompatDelegate.setApplicationLocales to recreate this activity with the
// newly chosen language (Instellingen > Algemeen > Taal).
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = LocalContext.current.applicationContext as HomeStockApplication
            val themeMode by application.container.themePreferences.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HomeStockTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val householdId by application.container.householdSession.householdId.collectAsState()
                    if (householdId == null) {
                        HouseholdScreen()
                    } else {
                        HomeStockApp()
                    }
                }
            }
        }
    }
}
