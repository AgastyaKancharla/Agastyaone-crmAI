package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun AssistantDashboardScreen(onSignOut: () -> Unit, onOpenPatients: () -> Unit) {
    DashboardScaffold(
        title = "Clinic dashboard",
        subtitle = "Assistant / Hygienist",
        tiles = listOf(
            // Full record is viewable; only the clinical fields (allergies, chronic
            // conditions, current medications, medical history) are editable here -
            // demographics stay receptionist/owner territory.
            DashboardTile("Patients", onClick = onOpenPatients),
        ),
        onSignOut = onSignOut,
    )
}
