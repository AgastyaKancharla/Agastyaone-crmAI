package com.agastyaone.crmai.ui.auth

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Everything reachable before the user has a resolved [com.agastyaone.crmai.data.auth.SessionState].
 * Platform admin sign-in is a distinct route with its own screen/back-end path, deliberately not
 * reachable from the clinic owner-signup or staff-invite flow.
 */
@Composable
fun AuthNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = AuthRoutes.WELCOME) {
        composable(AuthRoutes.WELCOME) {
            WelcomeScreen(
                onOwnerSignup = { navController.navigate(AuthRoutes.OWNER_SIGNUP) },
                onStaffSignIn = { navController.navigate(AuthRoutes.STAFF_SIGN_IN) },
                onPlatformAdmin = { navController.navigate(AuthRoutes.PLATFORM_ADMIN_SIGN_IN) },
            )
        }
        composable(AuthRoutes.STAFF_SIGN_IN) {
            StaffSignInScreen(onBack = { navController.popBackStack() })
        }
        composable(AuthRoutes.OWNER_SIGNUP) {
            OwnerSignupScreen(onBack = { navController.popBackStack() })
        }
        composable(AuthRoutes.PLATFORM_ADMIN_SIGN_IN) {
            PlatformAdminSignInScreen(onBack = { navController.popBackStack() })
        }
    }
}
