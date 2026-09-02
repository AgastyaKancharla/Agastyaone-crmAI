package com.agastyaone.crmai.data.charting

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Owner/dentist-only for every write, per firestore.rules - the assistant's read-only access
 * is enforced there too, not by anything in this class.
 */
class TreatmentPlanRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun treatmentPlansCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("treatmentPlans")

    fun observeTreatmentPlansForPatient(clinicId: String, patientId: String): Flow<List<TreatmentPlan>> =
        treatmentPlansCollection(clinicId)
            .whereEqualTo("patientId", patientId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow()

    fun observeTreatmentPlan(clinicId: String, planId: String): Flow<TreatmentPlan?> = callbackFlow {
        val registration = treatmentPlansCollection(clinicId).document(planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(TreatmentPlan::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createTreatmentPlan(
        clinicId: String,
        createdByUid: String,
        patientId: String,
        lineItems: List<TreatmentLineItem>,
    ): String {
        val ref = treatmentPlansCollection(clinicId).document()
        val data = hashMapOf<String, Any?>(
            "patientId" to patientId,
            "createdByUid" to createdByUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to TreatmentPlanStatus.DRAFT.id,
            "lineItems" to lineItems.map { it.toRaw() },
            "totalEstimate" to lineItems.sumOf { it.estimatedCost },
            "patientApprovalSignatureUrl" to null,
            "patientApprovedAt" to null,
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun updateStatus(clinicId: String, planId: String, status: TreatmentPlanStatus) {
        treatmentPlansCollection(clinicId).document(planId).update("status", status.id).await()
    }

    /** Moves a plan from `proposed` to `accepted` and records the patient's signature. */
    suspend fun recordApproval(clinicId: String, planId: String, signatureUrl: String) {
        treatmentPlansCollection(clinicId).document(planId).update(
            mapOf(
                "status" to TreatmentPlanStatus.ACCEPTED.id,
                "patientApprovalSignatureUrl" to signatureUrl,
                "patientApprovedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
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
