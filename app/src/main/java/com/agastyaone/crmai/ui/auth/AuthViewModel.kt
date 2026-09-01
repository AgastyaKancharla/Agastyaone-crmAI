package com.agastyaone.crmai.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.auth.AuthRepository
import com.agastyaone.crmai.data.auth.SessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    /**
     * Resolves a cold-start "already signed in" [SessionState.Loading] into its real
     * claims. Deliberately NOT run automatically on every Loading transition: sign-in
     * and owner-signup screens call [com.agastyaone.crmai.data.auth.AuthRepository.refreshSession]
     * themselves at the point in their flow where claims are actually ready to read -
     * e.g. only after `createClinicAndOwner` has run, not the moment the Firebase Auth
     * account is created - so an automatic collector here would race them.
     */
    fun refreshIfColdStart() {
        if (sessionState.value is SessionState.Loading) {
            viewModelScope.launch { authRepository.refreshSession() }
        }
    }

    fun signOut() = authRepository.signOut()
}
