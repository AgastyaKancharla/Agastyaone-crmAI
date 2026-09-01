package com.agastyaone.crmai.data.auth

import com.agastyaone.crmai.core.Role

/**
 * Snapshot of the signed-in user's identity as trusted by the backend: the
 * Firebase Auth custom claims (`role`, `clinicId`, `platformAdmin`), not any
 * client-editable Firestore field.
 */
sealed interface SessionState {

    data object SignedOut : SessionState

    /** Signed in, but the ID token has not been fetched/refreshed yet. */
    data object Loading : SessionState

    data object PlatformAdmin : SessionState

    /** Signed in with no `role`/`clinicId` claim yet - e.g. an invite that hasn't been accepted. */
    data object AwaitingClinicSetup : SessionState

    data class Staff(
        val uid: String,
        val clinicId: String,
        val role: Role,
    ) : SessionState
}
