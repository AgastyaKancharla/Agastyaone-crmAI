package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun ReceptionistDashboardScreen(onSignOut: () -> Unit) {
    DashboardScaffold(
        title = "Clinic dashboard",
        subtitle = "Receptionist",
        tiles = listOf(
            DashboardTile("Schedule"),
            DashboardTile("Billing"),
        ),
        onSignOut = onSignOut,
    )
}
