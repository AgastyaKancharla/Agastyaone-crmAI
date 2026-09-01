package com.agastyaone.crmai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agastyaone.crmai.data.auth.SessionState
import com.agastyaone.crmai.ui.auth.AuthNavHost
import com.agastyaone.crmai.ui.auth.AuthViewModel
import com.agastyaone.crmai.ui.auth.WaitingForClinicSetupScreen
import com.agastyaone.crmai.ui.dashboard.AppNavHost
import com.agastyaone.crmai.ui.dashboard.PlatformAdminDashboardScreen

/**
 * Routes purely off the resolved [SessionState] - never off a Firestore document -
 * because custom claims are the only thing the security rules and this app agree to
 * trust. SignedOut and Loading both render [AuthNavHost] so an in-progress sign-in or
 * owner-signup screen (and its live coroutine) is never torn down mid-flow; those
 * screens call `refreshSession()` themselves once claims are actually ready.
 */
@Composable
fun AgastyaOneRoot() {
    val authViewModel: AuthViewModel = viewModel()
    val session by authViewModel.sessionState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.refreshIfColdStart()
    }

    when (val currentSession = session) {
        SessionState.SignedOut, SessionState.Loading -> AuthNavHost()
        SessionState.AwaitingClinicSetup -> WaitingForClinicSetupScreen(onSignOut = authViewModel::signOut)
        SessionState.PlatformAdmin -> PlatformAdminDashboardScreen(onSignOut = authViewModel::signOut)
        is SessionState.Staff -> AppNavHost(session = currentSession, onSignOut = authViewModel::signOut)
    }
}
