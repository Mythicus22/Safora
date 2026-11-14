package com.example.safora.core.navigation

sealed class Screen(val route: String, val title: String) {
    object Splash : Screen("splash", "Splash")
    object Onboarding : Screen("onboarding", "Setup SAFORA")
    object Dashboard : Screen("dashboard", "Dashboard")
    object DevicePairing : Screen("device_pairing", "Wearable Device")
    object Monitoring : Screen("monitoring", "Live Monitoring")
    object Emergency : Screen("emergency", "Active Incident")
    object Map : Screen("map", "Safety Map")
    object Contacts : Screen("contacts", "Trusted Contacts")
    object Evidence : Screen("evidence", "Evidence Vault")
    object Settings : Screen("settings", "Settings")
}

sealed class BottomNavTab(val route: String, val label: String) {
    object Home : BottomNavTab(Screen.Dashboard.route, "Home")
    object SafetyMap : BottomNavTab(Screen.Map.route, "Map")
    object Vault : BottomNavTab(Screen.Evidence.route, "Evidence")
    object Profile : BottomNavTab(Screen.Settings.route, "Profile")
}
