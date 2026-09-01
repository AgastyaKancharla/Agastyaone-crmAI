package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
fun PlatformAdminDashboardScreen(onSignOut: () -> Unit) {
    DashboardScaffold(
        title = "AgastyaOne platform admin",
        subtitle = "Platform Admin",
        tiles = listOf(
            DashboardTile("Clinics"),
            DashboardTile("Support"),
        ),
        onSignOut = onSignOut,
    )
}
