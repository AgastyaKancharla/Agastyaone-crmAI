package com.agastyaone.crmai.data.functions

import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around the HTTPS callable Cloud Functions that mutate identity
 * (custom claims) and cross-cutting state (audit log). Client code never writes
 * `tenants/{clinicId}/staff` or sets claims directly - only these functions do,
 * running with the Admin SDK so they can bypass Firestore rules safely.
 */
class CloudFunctionsRepository(
    private val functions: FirebaseFunctions = Firebase.functions("asia-south1"),
) {

    data class OwnerSignupResult(val clinicId: String)

    suspend fun createClinicAndOwner(
        clinicName: String,
        address: String,
        city: String,
        state: String,
        gstin: String?,
        ownerName: String,
        ownerPhone: String?,
        ownerEmail: String?,
    ): OwnerSignupResult {
        val data = hashMapOf(
            "clinicName" to clinicName,
            "address" to address,
            "city" to city,
            "state" to state,
            "gstin" to gstin,
            "ownerName" to ownerName,
            "ownerPhone" to ownerPhone,
            "ownerEmail" to ownerEmail,
        )
        val result = functions.getHttpsCallable("createClinicAndOwner").call(data).await()
        @Suppress("UNCHECKED_CAST")
        val response = result.data as Map<String, Any?>
        return OwnerSignupResult(clinicId = response["clinicId"] as String)
    }

    suspend fun inviteStaff(
        role: String,
        name: String,
        phone: String?,
        email: String?,
    ) {
        val data = hashMapOf(
            "role" to role,
            "name" to name,
            "phone" to phone,
            "email" to email,
        )
        functions.getHttpsCallable("inviteStaff").call(data).await()
    }

    /**
     * Called by the invited user right after they sign in for the first time. Pass a
     * specific [inviteId] if the client already knows it; otherwise the function looks
     * up a pending invite matching the caller's own verified phone number/email.
     */
    suspend fun acceptInvite(inviteId: String? = null) {
        val data = hashMapOf("inviteId" to inviteId)
        functions.getHttpsCallable("acceptInvite").call(data).await()
    }

    suspend fun updateStaffRole(targetUid: String, newRole: String) {
        val data = hashMapOf("targetUid" to targetUid, "newRole" to newRole)
        functions.getHttpsCallable("updateStaffRole").call(data).await()
    }
}
