package com.agastyaone.crmai.core

import com.agastyaone.crmai.data.auth.AuthRepository
import com.agastyaone.crmai.data.functions.CloudFunctionsRepository
import com.agastyaone.crmai.data.patients.PatientRepository
import com.agastyaone.crmai.data.storage.SignatureUploader

/**
 * Deliberately minimal manual DI for this phase - a Hilt/Koin graph is overkill
 * for the handful of singletons this app needs. Revisit once module count grows.
 */
object ServiceLocator {
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val cloudFunctionsRepository: CloudFunctionsRepository by lazy { CloudFunctionsRepository() }
    val patientRepository: PatientRepository by lazy { PatientRepository() }
    val signatureUploader: SignatureUploader by lazy { SignatureUploader() }
}
