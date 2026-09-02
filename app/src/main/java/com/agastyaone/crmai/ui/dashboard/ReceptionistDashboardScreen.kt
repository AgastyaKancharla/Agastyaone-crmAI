package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun ReceptionistDashboardScreen(
    onSignOut: () -> Unit,
    onOpenPatients: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenBilling: () -> Unit,
) {
    DashboardScaffold(
        title = "Clinic dashboard",
        subtitle = "Receptionist",
        tiles = listOf(
            DashboardTile("Schedule", onClick = onOpenSchedule),
            DashboardTile("Patients", onClick = onOpenPatients),
            DashboardTile("Billing", onClick = onOpenBilling),
        ),
        onSignOut = onSignOut,
    )
}
