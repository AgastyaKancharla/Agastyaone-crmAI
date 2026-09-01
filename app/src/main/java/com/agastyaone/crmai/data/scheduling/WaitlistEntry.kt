package com.agastyaone.crmai.data.scheduling

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class WaitlistStatus(val id: String) {
    WAITING("waiting"),
    OFFERED("offered"),
    BOOKED("booked"),
    EXPIRED("expired");

    companion object {
        fun fromId(id: String): WaitlistStatus = entries.firstOrNull { it.id == id } ?: WAITING
    }
}

/** `tenants/{clinicId}/waitlist/{entryId}`. */
data class WaitlistEntry(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val preferredDates: List<Timestamp> = emptyList(),
    val notes: String? = null,
    val addedAt: Timestamp? = null,
    val addedByUid: String? = null,
    val status: String = WaitlistStatus.WAITING.id,
)
