package com.agastyaone.crmai.data.insurance

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore reads/writes for `tenants/{clinicId}/insuranceClaims/{claimId}` -
 * firestore.rules (owner/receptionist full CRUD, assistant/labCoordinator no access)
 * enforces the role matrix, not this class.
 */
class InsuranceClaimRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun claimsCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("insuranceClaims")

    fun observeClaimsForClinic(clinicId: String): Flow<List<InsuranceClaim>> =
        claimsCollection(clinicId)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .asFlow()

    fun observeClaimsForInvoice(clinicId: String, invoiceId: String): Flow<List<InsuranceClaim>> =
        claimsCollection(clinicId)
            .whereEqualTo("invoiceId", invoiceId)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .asFlow()

    fun observeClaim(clinicId: String, claimId: String): Flow<InsuranceClaim?> = callbackFlow {
        val registration = claimsCollection(clinicId).document(claimId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(InsuranceClaim::class.java))
            }
        awaitClose { registration.remove() }
    }

    /**
     * A claim's Storage documents live under a path keyed by its claimId (see
     * storage.rules), so the ID must exist before any document upload starts - callers
     * reserve one with this, upload documents against it, then call [createClaim] with
     * the same ID once every document's download URL is in hand.
     */
    fun newClaimId(clinicId: String): String = claimsCollection(clinicId).document().id

    suspend fun createClaim(
        clinicId: String,
        claimId: String,
        submittedByUid: String,
        patientId: String,
        invoiceId: String,
        tpaName: String,
        claimAmount: Double,
        documentUrls: List<String>,
    ) {
        val data = hashMapOf<String, Any?>(
            "patientId" to patientId,
            "invoiceId" to invoiceId,
            "tpaName" to tpaName,
            "claimAmount" to claimAmount,
            "status" to ClaimStatus.SUBMITTED.id,
            "documentUrls" to documentUrls,
            "submittedAt" to Timestamp.now(),
            "submittedByUid" to submittedByUid,
            "resolvedAt" to null,
            "resolutionNotes" to null,
        )
        claimsCollection(clinicId).document(claimId).set(data).await()
    }

    /**
     * Moves a claim through the status pipeline. [resolutionNotes] only makes sense once
     * the claim reaches a resolved status (approved/denied/paid) - callers pass null for
     * an in-flight status change (e.g. submitted -> underReview).
     */
    suspend fun updateStatus(
        clinicId: String,
        claimId: String,
        status: ClaimStatus,
        resolutionNotes: String?,
    ) {
        val isResolved = status == ClaimStatus.APPROVED || status == ClaimStatus.DENIED || status == ClaimStatus.PAID
        claimsCollection(clinicId).document(claimId).update(
            mapOf(
                "status" to status.id,
                "resolvedAt" to if (isResolved) Timestamp.now() else null,
                "resolutionNotes" to resolutionNotes,
            ),
        ).await()
    }

    suspend fun deleteClaim(clinicId: String, claimId: String) {
        claimsCollection(clinicId).document(claimId).delete().await()
    }
}

private inline fun <reified T : Any> Query.asFlow(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObjects(T::class.java) ?: emptyList())
    }
    awaitClose { registration.remove() }
}
