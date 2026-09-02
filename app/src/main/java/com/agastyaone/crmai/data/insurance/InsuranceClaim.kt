package com.agastyaone.crmai.data.insurance

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class ClaimStatus(val id: String, val label: String) {
    SUBMITTED("submitted", "Submitted"),
    UNDER_REVIEW("underReview", "Under review"),
    APPROVED("approved", "Approved"),
    DENIED("denied", "Denied"),
    PAID("paid", "Paid");

    companion object {
        fun fromId(id: String): ClaimStatus = entries.firstOrNull { it.id == id } ?: SUBMITTED
    }
}

/**
 * `tenants/{clinicId}/insuranceClaims/{claimId}`. [claimAmount] is the amount claimed
 * from the TPA, which is routinely less than the linked invoice's `total` - partial
 * insurance coverage is the norm, not the exception (Phase 4c spec). The patient
 * remains responsible for the difference; the UI must make that explicit rather than
 * implying the claim covers the whole invoice.
 */
data class InsuranceClaim(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val invoiceId: String = "",
    val tpaName: String = "",
    val claimAmount: Double = 0.0,
    val status: String = ClaimStatus.SUBMITTED.id,
    val documentUrls: List<String> = emptyList(),
    val submittedAt: Timestamp? = null,
    val submittedByUid: String? = null,
    val resolvedAt: Timestamp? = null,
    val resolutionNotes: String? = null,
)
