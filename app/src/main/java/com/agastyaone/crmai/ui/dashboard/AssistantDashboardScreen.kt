package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun AssistantDashboardScreen(onSignOut: () -> Unit) {
    DashboardScaffold(
        title = "Clinic dashboard",
        subtitle = "Assistant / Hygienist",
        tiles = listOf(
            DashboardTile("Patients (view only)"),
        ),
        onSignOut = onSignOut,
    )
}
