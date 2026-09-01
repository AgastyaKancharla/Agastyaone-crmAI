package com.agastyaone.crmai.data.auth

import android.app.Activity
import com.agastyaone.crmai.core.Role
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for "who is signed in and what can they do". Custom claims
 * (`role`, `clinicId`, `platformAdmin`) are the trust boundary - the same claims the
 * Firestore security rules and the Cloud Functions check - so this repository always
 * reads them from a force-refreshed ID token rather than from any Firestore document.
 */
class AuthRepository(private val auth: com.google.firebase.auth.FirebaseAuth = Firebase.auth) {

    private val _sessionState = MutableStateFlow<SessionState>(
        if (auth.currentUser == null) SessionState.SignedOut else SessionState.Loading
    )
    val sessionState: StateFlow<SessionState> = _sessionState

    init {
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) {
                _sessionState.value = SessionState.SignedOut
            } else {
                _sessionState.value = SessionState.Loading
            }
        }
    }

    /** Force-refreshes the ID token and republishes [sessionState] from its claims. */
    suspend fun refreshSession(): SessionState {
        val user = auth.currentUser ?: return SessionState.SignedOut.also { _sessionState.value = it }
        val tokenResult = user.getIdToken(true).await()
        val claims = tokenResult.claims

        val state = when {
            claims["platformAdmin"] == true -> SessionState.PlatformAdmin
            claims["clinicId"] is String && claims["role"] is String -> {
                val role = Role.fromClaim(claims["role"] as String)
                val clinicId = claims["clinicId"] as String
                if (role != null && clinicId.isNotBlank()) {
                    SessionState.Staff(uid = user.uid, clinicId = clinicId, role = role)
                } else {
                    SessionState.AwaitingClinicSetup
                }
            }
            else -> SessionState.AwaitingClinicSetup
        }
        _sessionState.value = state
        return state
    }

    fun startPhoneVerification(
        phoneNumber: String,
        activity: Activity,
    ): Flow<PhoneVerificationEvent> = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                trySend(PhoneVerificationEvent.AutoVerified(credential))
            }

            override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                trySend(PhoneVerificationEvent.Failed(exception.message ?: "Phone verification failed"))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                trySend(PhoneVerificationEvent.CodeSent(verificationId))
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)

        awaitClose { }
    }

    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).await()
    }

    suspend fun signInWithOtp(verificationId: String, smsCode: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
        signInWithPhoneCredential(credential)
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUpWithEmail(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    fun signOut() {
        auth.signOut()
    }

    val currentUid: String? get() = auth.currentUser?.uid
}
