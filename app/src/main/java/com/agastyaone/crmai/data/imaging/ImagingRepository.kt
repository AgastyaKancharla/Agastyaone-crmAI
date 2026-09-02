package com.agastyaone.crmai.data.imaging

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore reads/writes for imaging metadata, scoped by clinicId - firestore.rules
 * (owner/assistant create+read+update, owner-only delete) and storage.rules (mirroring
 * the same split for the actual file) enforce the role matrix, not this class.
 */
class ImagingRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun imagingCollection(clinicId: String) =
        db.collection("tenants").document(clinicId).collection("imaging")

    fun observeImagingForPatient(clinicId: String, patientId: String): Flow<List<ImagingRecord>> =
        imagingCollection(clinicId)
            .whereEqualTo("patientId", patientId)
            .orderBy("capturedAt", Query.Direction.DESCENDING)
            .asFlow()

    suspend fun createImagingRecord(
        clinicId: String,
        uploadedByUid: String,
        patientId: String,
        toothNumber: String?,
        type: ImagingType,
        storageUrl: String,
        capturedAt: Timestamp,
        notes: String?,
    ): String {
        val ref = imagingCollection(clinicId).document()
        val data = hashMapOf<String, Any?>(
            "patientId" to patientId,
            "toothNumber" to toothNumber,
            "type" to type.id,
            "storageUrl" to storageUrl,
            "capturedAt" to capturedAt,
            "uploadedByUid" to uploadedByUid,
            "notes" to notes,
        )
        ref.set(data).await()
        return ref.id
    }

    /** Owner or assistant, per firestore.rules - tagging (tooth/type/notes) is not delete. */
    suspend fun updateTags(
        clinicId: String,
        imageId: String,
        toothNumber: String?,
        type: ImagingType,
        notes: String?,
    ) {
        imagingCollection(clinicId).document(imageId).update(
            mapOf(
                "toothNumber" to toothNumber,
                "type" to type.id,
                "notes" to notes,
            ),
        ).await()
    }

    /** Owner-only, per firestore.rules - see the rules file for why deletion stays that narrow. */
    suspend fun deleteImagingRecord(clinicId: String, imageId: String) {
        imagingCollection(clinicId).document(imageId).delete().await()
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
