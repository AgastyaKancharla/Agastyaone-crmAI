package com.agastyaone.crmai.data.patients

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class DataRequestType(val id: String) {
    ACCESS("access"),
    CORRECTION("correction"),
    ERASURE("erasure"),
}

enum class DataRequestStatus(val id: String) {
    OPEN("open"),
    IN_PROGRESS("inProgress"),
    RESOLVED("resolved");

    companion object {
        fun fromId(id: String): DataRequestStatus = entries.firstOrNull { it.id == id } ?: OPEN
    }
}

/**
 * `tenants/{clinicId}/dataRequests/{requestId}` - a DPDP access/correction/erasure request log.
 * This phase only tracks the request; it doesn't execute erasure automatically.
 */
data class DataRequest(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val requestType: String = "",
    val requestedAt: Timestamp? = null,
    val requestedByUid: String? = null,
    val status: String = DataRequestStatus.OPEN.id,
    val resolvedAt: Timestamp? = null,
    val notes: String? = null,
)
