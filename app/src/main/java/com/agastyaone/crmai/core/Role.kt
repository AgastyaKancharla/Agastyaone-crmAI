package com.agastyaone.crmai.core

/**
 * Mirrors the `role` custom claim set on the Firebase Auth ID token by the
 * `setStaffClaims` / `createClinicAndOwner` Cloud Functions. Values must stay in
 * sync with functions/src/roles.ts.
 */
enum class Role(val claimValue: String) {
    OWNER("owner"),
    RECEPTIONIST("receptionist"),
    ASSISTANT("assistant"),
    LAB_COORDINATOR("labCoordinator");

    companion object {
        fun fromClaim(value: String?): Role? = entries.firstOrNull { it.claimValue == value }
    }
}
