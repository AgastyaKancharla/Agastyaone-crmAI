package com.agastyaone.crmai.data.patients

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
 * All reads/writes here go straight through the client SDK, scoped by [clinicId] the
 * caller already has from [com.agastyaone.crmai.data.auth.SessionState.Staff] - the
 * Firestore rules (not this class) are what actually enforce the role matrix (owner/
 * receptionist/assistant field restrictions, Lab Coordinator's total exclusion).
 */
class PatientRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun patientsCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("patients")

    private fun consentsCollection(clinicId: String, patientId: String) =
        patientsCollection(clinicId).document(patientId).collection("consents")

    private fun intakeFormsCollection(clinicId: String, patientId: String) =
        patientsCollection(clinicId).document(patientId).collection("intakeForms")

    private fun dataRequestsCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("dataRequests")

    fun observeRecentPatients(clinicId: String, limit: Long = 20): Flow<List<Patient>> =
        patientsCollection(clinicId)
            .orderBy("lastEditedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .asFlow()

    /** Firestore has no full-text search; this is a case-sensitive prefix match on `name`. */
    suspend fun searchPatientsByName(clinicId: String, prefix: String, limit: Long = 20): List<Patient> {
        val snapshot = patientsCollection(clinicId)
            .orderBy("name")
            .startAt(prefix)
            .endAt(prefix + '\uf8ff')
            .limit(limit)
            .get()
            .await()
        return snapshot.toObjects(Patient::class.java)
    }

    suspend fun searchPatientsByPhone(clinicId: String, phone: String, limit: Long = 20): List<Patient> {
        val snapshot = patientsCollection(clinicId)
            .whereEqualTo("phone", phone)
            .limit(limit)
            .get()
            .await()
        return snapshot.toObjects(Patient::class.java)
    }

    fun observePatient(clinicId: String, patientId: String): Flow<Patient?> = callbackFlow {
        val registration = patientsCollection(clinicId).document(patientId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Patient::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createPatient(clinicId: String, createdByUid: String, demographics: PatientDemographics): String {
        val ref = patientsCollection(clinicId).document()
        val data = hashMapOf<String, Any?>(
            "name" to demographics.name,
            "dob" to demographics.dob,
            "gender" to demographics.gender,
            "phone" to demographics.phone,
            "email" to demographics.email,
            "address" to demographics.address,
            "emergencyContactName" to demographics.emergencyContactName,
            "emergencyContactPhone" to demographics.emergencyContactPhone,
            "bloodGroup" to demographics.bloodGroup,
            "createdAt" to FieldValue.serverTimestamp(),
            "createdByUid" to createdByUid,
            "lastEditedAt" to FieldValue.serverTimestamp(),
            "lastEditedByUid" to createdByUid,
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun updateDemographics(
        clinicId: String,
        patientId: String,
        editedByUid: String,
        demographics: PatientDemographics,
    ) {
        val data = hashMapOf<String, Any?>(
            "name" to demographics.name,
            "dob" to demographics.dob,
            "gender" to demographics.gender,
            "phone" to demographics.phone,
            "email" to demographics.email,
            "address" to demographics.address,
            "emergencyContactName" to demographics.emergencyContactName,
            "emergencyContactPhone" to demographics.emergencyContactPhone,
            "bloodGroup" to demographics.bloodGroup,
            "lastEditedAt" to FieldValue.serverTimestamp(),
            "lastEditedByUid" to editedByUid,
        )
        patientsCollection(clinicId).document(patientId).update(data).await()
    }

    suspend fun updateClinicalDetails(
        clinicId: String,
        patientId: String,
        editedByUid: String,
        clinical: PatientClinicalDetails,
    ) {
        val data = hashMapOf<String, Any?>(
            "allergies" to clinical.allergies,
            "chronicConditions" to clinical.chronicConditions,
            "currentMedications" to clinical.currentMedications,
            "medicalHistoryNotes" to clinical.medicalHistoryNotes,
            "lastEditedAt" to FieldValue.serverTimestamp(),
            "lastEditedByUid" to editedByUid,
        )
        patientsCollection(clinicId).document(patientId).update(data).await()
    }

    fun observeConsents(clinicId: String, patientId: String): Flow<List<Consent>> =
        consentsCollection(clinicId, patientId).asFlow()

    suspend fun recordConsent(
        clinicId: String,
        patientId: String,
        recordedByUid: String,
        consentType: ConsentType,
        granted: Boolean,
        signatureUrl: String?,
    ) {
        val data = hashMapOf<String, Any?>(
            "granted" to granted,
            "grantedAt" to if (granted) FieldValue.serverTimestamp() else null,
            "revokedAt" to if (!granted) FieldValue.serverTimestamp() else null,
            "signatureUrl" to signatureUrl,
            "recordedByUid" to recordedByUid,
        )
        consentsCollection(clinicId, patientId).document(consentType.id).set(data).await()
    }

    suspend fun recordIntakeForm(
        clinicId: String,
        patientId: String,
        recordedByUid: String,
        formType: IntakeFormType,
        signedByName: String,
        signatureImageUrl: String?,
        notes: String? = null,
    ): String {
        val ref = intakeFormsCollection(clinicId, patientId).document()
        val data = hashMapOf<String, Any?>(
            "formType" to formType.id,
            "signedByName" to signedByName,
            "signatureImageUrl" to signatureImageUrl,
            "signedAt" to FieldValue.serverTimestamp(),
            "recordedByUid" to recordedByUid,
            "notes" to notes,
        )
        ref.set(data).await()
        return ref.id
    }

    fun observeDataRequests(clinicId: String): Flow<List<DataRequest>> =
        dataRequestsCollection(clinicId)
            .orderBy("requestedAt", Query.Direction.DESCENDING)
            .asFlow()

    suspend fun logDataRequest(
        clinicId: String,
        requestedByUid: String,
        patientId: String,
        requestType: DataRequestType,
        notes: String?,
    ): String {
        val ref = dataRequestsCollection(clinicId).document()
        val data = hashMapOf<String, Any?>(
            "patientId" to patientId,
            "requestType" to requestType.id,
            "requestedAt" to FieldValue.serverTimestamp(),
            "requestedByUid" to requestedByUid,
            "status" to DataRequestStatus.OPEN.id,
            "resolvedAt" to null,
            "notes" to notes,
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun updateDataRequestStatus(
        clinicId: String,
        requestId: String,
        status: DataRequestStatus,
        notes: String?,
    ) {
        val data = hashMapOf<String, Any?>(
            "status" to status.id,
            "notes" to notes,
            "resolvedAt" to if (status == DataRequestStatus.RESOLVED) FieldValue.serverTimestamp() else null,
        )
        dataRequestsCollection(clinicId).document(requestId).update(data).await()
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
