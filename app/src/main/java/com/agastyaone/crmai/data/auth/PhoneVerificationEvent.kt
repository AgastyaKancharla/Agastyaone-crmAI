package com.agastyaone.crmai.data.auth

import com.google.firebase.auth.PhoneAuthCredential

/** Callback-shaped events from [com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber]. */
sealed interface PhoneVerificationEvent {
    data class CodeSent(val verificationId: String) : PhoneVerificationEvent
    data class AutoVerified(val credential: PhoneAuthCredential) : PhoneVerificationEvent
    data class Failed(val message: String) : PhoneVerificationEvent
}
