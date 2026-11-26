package com.scrollguard.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scrollguard.app.presentation.apps.AppBlockingScreen
import com.scrollguard.app.presentation.apps.AppBlockingViewModel
import com.scrollguard.app.presentation.blocking.DiagnosticsScreen
import com.scrollguard.app.presentation.blocking.SelectiveBlockingScreen
import com.scrollguard.app.presentation.blocking.SelectiveBlockingViewModel
import com.scrollguard.app.presentation.dashboard.DashboardScreen
import com.scrollguard.app.presentation.dashboard.DashboardViewModel
import com.scrollguard.app.presentation.onboarding.OnboardingScreen
import com.scrollguard.app.presentation.onboarding.OnboardingViewModel
import com.scrollguard.app.presentation.onboarding.PermissionSetupScreen
import com.scrollguard.app.presentation.reports.ReportsScreen
import com.scrollguard.app.presentation.reports.ReportsViewModel
import com.scrollguard.app.presentation.schedules.ScheduleScreen
import com.scrollguard.app.presentation.schedules.ScheduleViewModel
import com.scrollguard.app.presentation.settings.SettingsScreen
import com.scrollguard.app.presentation.settings.SettingsViewModel
import com.scrollguard.app.presentation.splash.SplashScreen
import com.scrollguard.app.presentation.strictmode.StrictModeScreen
import com.scrollguard.app.presentation.strictmode.StrictModeViewModel

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions"
    const val DASHBOARD = "dashboard"
    const val APPS = "apps"
    const val SELECTIVE = "selective"
    const val SCHEDULES = "schedules"
    const val REPORTS = "reports"
    const val STRICT_MODE = "strict_mode"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Home", Icons.Rounded.Dashboard),
    BottomTab(Routes.SELECTIVE, "Selective", Icons.Rounded.FilterList),
    BottomTab(Routes.APPS, "Apps", Icons.Rounded.Apps),
    BottomTab(Routes.SCHEDULES, "Schedules", Icons.Rounded.Schedule),
    BottomTab(Routes.REPORTS, "Reports", Icons.Rounded.BarChart)
)

@Composable
fun ScrollGuardNavHost() {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingDone by onboardingViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = bottomTabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onSplashComplete = {
                        val nextRoute = if (onboardingDone) Routes.DASHBOARD else Routes.ONBOARDING
                        navController.navigate(nextRoute) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onContinueToPermissions = {
                        navController.navigate(Routes.PERMISSIONS) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.PERMISSIONS) {
                PermissionSetupScreen(
                    viewModel = onboardingViewModel,
                    showBackButton = onboardingDone && navController.previousBackStackEntry != null,
                    onBack = { navController.popBackStack() },
                    onContinueToDashboard = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.PERMISSIONS) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DASHBOARD) {
                val vm: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = vm,
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onOpenSelectiveBlocking = { navController.navigate(Routes.SELECTIVE) },
                    onOpenAppBlocking = { navController.navigate(Routes.APPS) },
                    onOpenSchedules = { navController.navigate(Routes.SCHEDULES) },
                    onOpenStrictMode = { navController.navigate(Routes.STRICT_MODE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(Routes.APPS) {
                val vm: AppBlockingViewModel = hiltViewModel()
                AppBlockingScreen(
                    viewModel = vm,
                    onOpenSelectiveBlocking = { navController.navigate(Routes.SELECTIVE) }
                )
            }

            composable(Routes.SELECTIVE) {
                val vm: SelectiveBlockingViewModel = hiltViewModel()
                SelectiveBlockingScreen(
                    viewModel = vm,
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
                )
            }

            composable(Routes.DIAGNOSTICS) {
                val vm: SelectiveBlockingViewModel = hiltViewModel()
                DiagnosticsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SCHEDULES) {
                val vm: ScheduleViewModel = hiltViewModel()
                ScheduleScreen(viewModel = vm)
            }

            composable(Routes.REPORTS) {
                val vm: ReportsViewModel = hiltViewModel()
                ReportsScreen(viewModel = vm)
            }

            composable(Routes.STRICT_MODE) {
                val vm: StrictModeViewModel = hiltViewModel()
                StrictModeScreen(viewModel = vm)
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onOpenStrictMode = { navController.navigate(Routes.STRICT_MODE) },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
                )
            }
        }
    }
}
