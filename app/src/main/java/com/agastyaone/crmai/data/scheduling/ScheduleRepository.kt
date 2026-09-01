package com.agastyaone.crmai.data.scheduling

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * All reads/writes here go straight through the client SDK, scoped by clinicId - the
 * Firestore rules (not this class) enforce the role matrix (owner/receptionist full
 * CRUD, assistant read-only, Lab Coordinator excluded entirely) and the patientExists()
 * referential-integrity check on create/update.
 */
class ScheduleRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun appointmentsCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("appointments")

    private fun waitlistCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("waitlist")

    /** Appointments starting within [rangeStart, rangeEnd) - used for both the day and week views. */
    fun observeAppointmentsInRange(
        clinicId: String,
        rangeStart: Timestamp,
        rangeEnd: Timestamp,
    ): Flow<List<Appointment>> = appointmentsCollection(clinicId)
        .whereGreaterThanOrEqualTo("startTime", rangeStart)
        .whereLessThan("startTime", rangeEnd)
        .orderBy("startTime")
        .asFlow()

    fun observeAppointment(clinicId: String, appointmentId: String): Flow<Appointment?> = callbackFlow {
        val registration = appointmentsCollection(clinicId).document(appointmentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Appointment::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createAppointment(
        clinicId: String,
        createdByUid: String,
        patientId: String,
        patientName: String,
        dentistUid: String?,
        startTime: Timestamp,
        endTime: Timestamp,
        source: AppointmentSource,
        notes: String?,
    ): String {
        val ref = appointmentsCollection(clinicId).document()
        val data = hashMapOf<String, Any?>(
            "patientId" to patientId,
            "patientName" to patientName,
            "dentistUid" to dentistUid,
            "startTime" to startTime,
            "endTime" to endTime,
            "status" to AppointmentStatus.SCHEDULED.id,
            "source" to source.id,
            "notes" to notes,
            "createdByUid" to createdByUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "cancelledReason" to null,
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun rescheduleAppointment(
        clinicId: String,
        appointmentId: String,
        startTime: Timestamp,
        endTime: Timestamp,
        dentistUid: String?,
    ) {
        val data = hashMapOf<String, Any?>(
            "startTime" to startTime,
            "endTime" to endTime,
            "dentistUid" to dentistUid,
        )
        appointmentsCollection(clinicId).document(appointmentId).update(data).await()
    }

    suspend fun updateAppointmentStatus(
        clinicId: String,
        appointmentId: String,
        status: AppointmentStatus,
        cancelledReason: String? = null,
    ) {
        val data = hashMapOf<String, Any?>(
            "status" to status.id,
            "cancelledReason" to
                if (status == AppointmentStatus.CANCELLED || status == AppointmentStatus.NO_SHOW) cancelledReason else null,
        )
        appointmentsCollection(clinicId).document(appointmentId).update(data).await()
    }

    fun observeWaitlist(clinicId: String): Flow<List<WaitlistEntry>> =
        waitlistCollection(clinicId)
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .asFlow()

    suspend fun addToWaitlist(
        clinicId: String,
        addedByUid: String,
        patientId: String,
        patientName: String,
        preferredDates: List<Timestamp>,
        notes: String?,
    ): String {
        val ref = waitlistCollection(clinicId).document()
        val data = hashMapOf<String, Any?>(
            "patientId" to patientId,
            "patientName" to patientName,
            "preferredDates" to preferredDates,
            "notes" to notes,
            "addedAt" to FieldValue.serverTimestamp(),
            "addedByUid" to addedByUid,
            "status" to WaitlistStatus.WAITING.id,
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun updateWaitlistStatus(clinicId: String, entryId: String, status: WaitlistStatus) {
        waitlistCollection(clinicId).document(entryId).update("status", status.id).await()
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
