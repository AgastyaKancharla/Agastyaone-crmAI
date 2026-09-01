package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun OwnerDashboardScreen(onSignOut: () -> Unit, onOpenStaff: () -> Unit) {
    DashboardScaffold(
        title = "Clinic dashboard",
        subtitle = "Owner",
        tiles = listOf(
            DashboardTile("Schedule"),
            DashboardTile("Patients"),
            DashboardTile("Clinical"),
            DashboardTile("Billing"),
            DashboardTile("Reports"),
            DashboardTile("Inventory"),
            DashboardTile("Lab"),
            DashboardTile("Staff", onClick = onOpenStaff),
        ),
        onSignOut = onSignOut,
    )
}
