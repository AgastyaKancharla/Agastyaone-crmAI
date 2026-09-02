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
 * All reads/writes here go straight through the client SDK, scoped by clinicId - the
 * Firestore rules (not this class) enforce the role matrix (owner full CRUD, assistant
 * read-all/write-periodontalChart-only, receptionist and Lab Coordinator excluded entirely).
 *
 * Single-tooth entries are written via dotted field paths ("toothConditions.11") rather than
 * rewriting the whole map - Firestore merges that into a top-level "toothConditions" field
 * change either way, which is exactly what the rules' field-level scoping checks against.
 */
class ChartingRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun chartingsCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("chartings")

    fun observeChartingsForPatient(clinicId: String, patientId: String): Flow<List<Charting>> =
        chartingsCollection(clinicId)
            .whereEqualTo("patientId", patientId)
            .orderBy("visitDate", Query.Direction.DESCENDING)
            .asFlow()

    fun observeCharting(clinicId: String, chartingId: String): Flow<Charting?> = callbackFlow {
        val registration = chartingsCollection(clinicId).document(chartingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Charting::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createCharting(
        clinicId: String,
        dentistUid: String,
        patientId: String,
        dentitionType: DentitionType,
    ): String {
        val ref = chartingsCollection(clinicId).document()
        val data = hashMapOf<String, Any?>(
            "patientId" to patientId,
            "visitDate" to FieldValue.serverTimestamp(),
            "dentistUid" to dentistUid,
            "dentitionType" to dentitionType.id,
            "toothConditions" to emptyMap<String, Any?>(),
            "periodontalChart" to emptyMap<String, Any?>(),
            "lastEditedByUid" to dentistUid,
            "lastEditedAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        return ref.id
    }

    /** Dentist-only, per firestore.rules - diagnostic markings never come from an assistant's write. */
    suspend fun updateToothCondition(
        clinicId: String,
        chartingId: String,
        editedByUid: String,
        toothNumber: String,
        entry: ToothConditionEntry,
    ) {
        chartingsCollection(clinicId).document(chartingId).update(
            mapOf(
                "toothConditions.$toothNumber" to entry.toRaw(),
                "lastEditedByUid" to editedByUid,
                "lastEditedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Owner or assistant, per firestore.rules - this is the field a hygienist is allowed to touch. */
    suspend fun updatePeriodontalReading(
        clinicId: String,
        chartingId: String,
        editedByUid: String,
        toothNumber: String,
        reading: PeriodontalReading,
    ) {
        chartingsCollection(clinicId).document(chartingId).update(
            mapOf(
                "periodontalChart.$toothNumber" to reading.toRaw(),
                "lastEditedByUid" to editedByUid,
                "lastEditedAt" to FieldValue.serverTimestamp(),
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
