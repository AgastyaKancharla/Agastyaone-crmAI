package com.agastyaone.crmai.data.scheduling

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class AppointmentStatus(val id: String) {
    SCHEDULED("scheduled"),
    CONFIRMED("confirmed"),
    CHECKED_IN("checkedIn"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    NO_SHOW("noShow");

    companion object {
        fun fromId(id: String): AppointmentStatus = entries.firstOrNull { it.id == id } ?: SCHEDULED
    }
}

enum class AppointmentSource(val id: String) {
    ONLINE("online"),
    WALK_IN("walkIn"),
    PHONE("phone");

    companion object {
        fun fromId(id: String): AppointmentSource = entries.firstOrNull { it.id == id } ?: PHONE
    }
}

/**
 * `tenants/{clinicId}/appointments/{appointmentId}`. [patientName] is denormalized for
 * fast calendar/list rendering without a join; [patientId] is what firestore.rules'
 * patientExists() actually checks referential integrity against.
 */
data class Appointment(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val dentistUid: String? = null,
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val status: String = AppointmentStatus.SCHEDULED.id,
    val source: String = AppointmentSource.PHONE.id,
    val notes: String? = null,
    val createdByUid: String? = null,
    val createdAt: Timestamp? = null,
    val cancelledReason: String? = null,
)
