package com.agastyaone.crmai.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.data.auth.SessionState

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_INVITE_STAFF = "inviteStaff"

/** Everything reachable once the signed-in user has a resolved clinic role. */
@Composable
fun AppNavHost(session: SessionState.Staff, onSignOut: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_DASHBOARD) {
        composable(ROUTE_DASHBOARD) {
            when (session.role) {
                Role.OWNER -> OwnerDashboardScreen(
                    onSignOut = onSignOut,
                    onOpenStaff = { navController.navigate(ROUTE_INVITE_STAFF) },
                )
                Role.RECEPTIONIST -> ReceptionistDashboardScreen(onSignOut = onSignOut)
                Role.ASSISTANT -> AssistantDashboardScreen(onSignOut = onSignOut)
                Role.LAB_COORDINATOR -> LabCoordinatorDashboardScreen(onSignOut = onSignOut)
            }
        }
        composable(ROUTE_INVITE_STAFF) {
            InviteStaffScreen(onDone = { navController.popBackStack() })
        }
    }
}
