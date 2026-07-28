package com.dtraas.boodschp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dtraas.boodschp.ui.household.HouseholdScreen
import com.dtraas.boodschp.ui.navigation.BoodschpApp
import com.dtraas.boodschp.ui.theme.BoodschpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoodschpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val application = LocalContext.current.applicationContext as BoodschpApplication
                    val householdId by application.container.householdSession.householdId.collectAsState()
                    if (householdId == null) {
                        HouseholdScreen()
                    } else {
                        BoodschpApp()
                    }
                }
            }
        }
    }
}
