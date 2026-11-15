package com.example.safora.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

import androidx.compose.ui.platform.LocalContext
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.IncidentState
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

@Composable
fun AppNavHost(initialNavigateToProfile: Boolean = false) {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val incident by repository.currentIncident.collectAsState()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Automatically navigate to Emergency Screen whenever an active SOS incident is ongoing
    LaunchedEffect(incident.state, currentRoute) {
        val isSosActive = incident.state == IncidentState.VERIFYING ||
            incident.state == IncidentState.CONFIRMED_SOS ||
            incident.state == IncidentState.ALERTS_SENT ||
            incident.state == IncidentState.EVIDENCE_SYNCING

        if (isSosActive && currentRoute != Screen.Emergency.route && currentRoute != Screen.Splash.route) {
            navController.navigate(Screen.Emergency.route) {
                launchSingleTop = true
            }
        }
    }

    // Ensure continuous microphone listening is active across all screens
    LaunchedEffect(Unit) {
        if (PermissionHelper.hasAudioPermission(context)) {
            AudioMonitoringService.start(context)
        }
    }

    val bottomTabs = listOf(
        BottomNavTab.Home,
        BottomNavTab.Monitor,
        BottomNavTab.SafetyMap,
        BottomNavTab.Vault,
        BottomNavTab.Profile
    )

    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    LaunchedEffect(initialNavigateToProfile) {
        if (initialNavigateToProfile) {
            navController.navigate(Screen.Settings.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
                                        BottomNavTab.Monitor -> Icons.Default.GraphicEq
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
                    onNavigateToDashboard = {
                        val isSosActive = incident.state == IncidentState.VERIFYING ||
                            incident.state == IncidentState.CONFIRMED_SOS ||
                            incident.state == IncidentState.ALERTS_SENT ||
                            incident.state == IncidentState.EVIDENCE_SYNCING

                        val destination = when {
                            isSosActive -> Screen.Emergency.route
                            initialNavigateToProfile -> Screen.Settings.route
                            else -> Screen.Dashboard.route
                        }
                        navController.navigate(destination) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(Screen.Onboarding.route) {
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
                    onNavigateToMap = { navController.navigate(Screen.Map.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Settings.route) }
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
                    onDismissToDashboard = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
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
