package com.agastyaone.crmai.data.patients

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * A consent is a live, revocable *state*, not a one-time signed document - a patient
 * can withdraw WhatsApp marketing consent next month without re-signing anything.
 * That's why it's a separate collection from [IntakeForm] even though both are
 * captured in the same intake-flow UI: this is what makes "what's this patient's
 * current WhatsApp marketing consent?" a real, queryable fact instead of something
 * buried inside a form PDF.
 */
enum class ConsentType(val id: String, val requiresSignature: Boolean) {
    TREATMENT_RECORDS("treatmentRecords", requiresSignature = true),
    WHATSAPP_MARKETING("whatsappMarketing", requiresSignature = false),
    REVIEW_REQUESTS("reviewRequests", requiresSignature = false),
    DATA_SHARING_TPA("dataSharingTpa", requiresSignature = true);

    companion object {
        fun fromId(id: String): ConsentType? = entries.firstOrNull { it.id == id }
    }
}

/** `tenants/{clinicId}/patients/{patientId}/consents/{consentType}` - document ID equals [consentType]. */
data class Consent(
    @DocumentId val consentType: String = "",
    val granted: Boolean = false,
    val grantedAt: Timestamp? = null,
    val revokedAt: Timestamp? = null,
    val signatureUrl: String? = null,
    val recordedByUid: String? = null,
)
