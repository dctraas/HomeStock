package com.dtraas.boodschp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dtraas.boodschp.ui.navigation.BoodschpApp
import com.dtraas.boodschp.ui.theme.BoodschpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoodschpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoodschpApp()
                }
            }
        }
    }
}
