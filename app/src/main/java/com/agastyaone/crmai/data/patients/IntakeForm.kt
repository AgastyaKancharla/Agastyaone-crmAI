package com.agastyaone.crmai.data.patients

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class IntakeFormType(val id: String) {
    MEDICAL_HISTORY("medicalHistory"),
    CONSENT_GENERAL("consentGeneral"),
    CONSENT_XRAY("consentXray"),
    CONSENT_TREATMENT("consentTreatment"),
}

/**
 * `tenants/{clinicId}/patients/{patientId}/intakeForms/{formId}` - a one-time signed
 * snapshot. [notes] is an addition beyond the base schema: a signed medical-history
 * form is meaningless without the content that was actually reported, and it can't
 * live on the patient doc's own `medicalHistoryNotes` since receptionists (who
 * typically run intake) aren't allowed to write that clinical field directly.
 */
data class IntakeForm(
    @DocumentId val id: String = "",
    val formType: String = "",
    val signedByName: String? = null,
    val signatureImageUrl: String? = null,
    val signedAt: Timestamp? = null,
    val recordedByUid: String? = null,
    val notes: String? = null,
)
