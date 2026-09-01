package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun LabCoordinatorDashboardScreen(onSignOut: () -> Unit) {
    DashboardScaffold(
        title = "Clinic dashboard",
        subtitle = "Lab Coordinator",
        tiles = listOf(
            DashboardTile("Lab Orders"),
        ),
        onSignOut = onSignOut,
    )
}
