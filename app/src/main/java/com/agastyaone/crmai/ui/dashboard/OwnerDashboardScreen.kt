package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun OwnerDashboardScreen(
    onSignOut: () -> Unit,
    onOpenStaff: () -> Unit,
    onOpenPatients: () -> Unit,
    onOpenDataRequests: () -> Unit,
    onOpenSchedule: () -> Unit,
) {
    DashboardScaffold(
        title = "Clinic dashboard",
        subtitle = "Owner",
        tiles = listOf(
            DashboardTile("Schedule", onClick = onOpenSchedule),
            DashboardTile("Patients", onClick = onOpenPatients),
            DashboardTile("Clinical"),
            DashboardTile("Billing"),
            DashboardTile("Reports"),
            DashboardTile("Inventory"),
            DashboardTile("Lab"),
            DashboardTile("Staff", onClick = onOpenStaff),
            DashboardTile("Data Requests", onClick = onOpenDataRequests),
        ),
        onSignOut = onSignOut,
    )
}
