package com.agastyaone.crmai.data.patients

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * `tenants/{clinicId}/patients/{patientId}`.
 *
 * Fields are split into two groups the Firestore rules enforce separately (see
 * firestore.rules): [DEMOGRAPHIC_FIELDS] stay receptionist/owner territory, while
 * [CLINICAL_FIELDS] are dentist/assistant-only, even though there's no charting yet.
 */
data class Patient(
    @DocumentId val id: String = "",
    val name: String = "",
    val dob: String? = null,
    val gender: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val bloodGroup: String? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val currentMedications: List<String> = emptyList(),
    val medicalHistoryNotes: String? = null,
    val createdAt: Timestamp? = null,
    val createdByUid: String? = null,
    val lastEditedAt: Timestamp? = null,
    val lastEditedByUid: String? = null,
) {
    val hasAllergies: Boolean get() = allergies.isNotEmpty()
}

/** The non-clinical fields a receptionist may create/edit. Mirrors clinicalFields() in firestore.rules. */
data class PatientDemographics(
    val name: String,
    val dob: String?,
    val gender: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val bloodGroup: String?,
)

/** The clinical fields only an assistant/owner may edit. Mirrors demographicFields() in firestore.rules. */
data class PatientClinicalDetails(
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val currentMedications: List<String>,
    val medicalHistoryNotes: String?,
)
