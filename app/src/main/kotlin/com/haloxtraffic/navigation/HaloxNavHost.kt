package com.haloxtraffic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.haloxtraffic.feature.capture.LiveEnforcementScreen
import com.haloxtraffic.feature.casefile.CaseFileScreen
import com.haloxtraffic.feature.map.MapScreen
import com.haloxtraffic.feature.reports.ReportsScreen
import com.haloxtraffic.feature.settings.SettingsScreen
import com.haloxtraffic.onboarding.OnboardingScreen
import com.haloxtraffic.ui.HomeScreen

/** Top-level routes. Onboarding gates first entry; Home is the hub to every section. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIVE = "live"
    const val CASES = "cases"
    const val MAP = "map"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
}

@Composable
fun HaloxNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Routes.ONBOARDING, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(Routes.LIVE) {
            LiveEnforcementScreen(onSessionEnded = { navController.popBackStack() })
        }
        composable(Routes.CASES) { CaseFileScreen() }
        composable(Routes.MAP) { MapScreen() }
        composable(Routes.REPORTS) { ReportsScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
