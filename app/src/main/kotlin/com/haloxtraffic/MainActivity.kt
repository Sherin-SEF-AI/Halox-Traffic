package com.haloxtraffic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.navigation.HaloxNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HaloxTheme {
                Surface(Modifier.fillMaxSize(), color = HaloxTheme.colors.void) {
                    val navController = rememberNavController()
                    HaloxNavHost(navController)
                }
            }
        }
    }
}
