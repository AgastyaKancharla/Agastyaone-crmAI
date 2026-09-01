package com.agastyaone.crmai.core

import com.agastyaone.crmai.data.auth.AuthRepository
import com.agastyaone.crmai.data.functions.CloudFunctionsRepository

/**
 * Deliberately minimal manual DI for this phase - a Hilt/Koin graph is overkill
 * for the handful of singletons Phase 1 needs. Revisit once module count grows.
 */
object ServiceLocator {
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val cloudFunctionsRepository: CloudFunctionsRepository by lazy { CloudFunctionsRepository() }
}
