package com.example.safora.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.safora.features.contacts.ContactsScreen
import com.example.safora.features.dashboard.DashboardScreen
import com.example.safora.features.device.DevicePairingScreen
import com.example.safora.features.emergency.EmergencyScreen
import com.example.safora.features.evidence.EvidenceScreen
import com.example.safora.features.map.MapScreen
import com.example.safora.features.monitoring.MonitoringScreen
import com.example.safora.features.onboarding.OnboardingScreen
import com.example.safora.features.settings.SettingsScreen
import com.example.safora.features.splash.SplashScreen
import com.example.safora.ui.theme.*

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomTabs = listOf(
        BottomNavTab.Home,
        BottomNavTab.SafetyMap,
        BottomNavTab.Vault,
        BottomNavTab.Profile
    )

    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CharcoalSurface,
                    contentColor = TextPrimary
                ) {
                    bottomTabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        BottomNavTab.Home -> Icons.Default.Shield
                                        BottomNavTab.SafetyMap -> Icons.Default.Map
                                        BottomNavTab.Vault -> Icons.Default.Lock
                                        BottomNavTab.Profile -> Icons.Default.Person
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BluePrimary,
                                selectedTextColor = BluePrimary,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = CharcoalSurfaceElevated
                            ),
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onTimeout = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToPairing = { navController.navigate(Screen.DevicePairing.route) },
                    onNavigateToMonitoring = { navController.navigate(Screen.Monitoring.route) },
                    onNavigateToEmergency = { navController.navigate(Screen.Emergency.route) },
                    onNavigateToContacts = { navController.navigate(Screen.Contacts.route) },
                    onNavigateToMap = { navController.navigate(Screen.Map.route) }
                )
            }
            composable(Screen.DevicePairing.route) {
                DevicePairingScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Monitoring.route) {
                MonitoringScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Emergency.route) {
                EmergencyScreen(
                    onDismissToDashboard = { navController.popBackStack() },
                    onNavigateToEvidence = { navController.navigate(Screen.Evidence.route) }
                )
            }
            composable(Screen.Map.route) {
                MapScreen()
            }
            composable(Screen.Evidence.route) {
                EvidenceScreen()
            }
            composable(Screen.Contacts.route) {
                ContactsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToPairing = { navController.navigate(Screen.DevicePairing.route) },
                    onNavigateToContacts = { navController.navigate(Screen.Contacts.route) }
                )
            }
        }
    }
}
