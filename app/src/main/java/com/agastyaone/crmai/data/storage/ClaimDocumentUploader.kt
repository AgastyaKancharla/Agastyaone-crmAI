package com.agastyaone.crmai.data.storage

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await

/**
 * Uploads a claim document (a scanned claim form, usually a PDF, or a photo of one) to
 * `tenants/{clinicId}/patients/{patientId}/insuranceClaims/{claimId}/{fileName}` in
 * Firebase Storage (see storage.rules's `isReasonablySizedDocument()`) and returns its
 * download URL for [com.agastyaone.crmai.data.insurance.InsuranceClaim.documentUrls].
 *
 * `putFile` lets the Storage SDK infer content type from the source [Uri]'s
 * ContentResolver metadata, same as [ImageUploader] - the document picker/camera intent
 * this is fed from always carries it.
 */
class ClaimDocumentUploader(private val storage: FirebaseStorage = Firebase.storage) {

    suspend fun upload(clinicId: String, patientId: String, claimId: String, fileName: String, sourceUri: Uri): String {
        val ref = storage.reference.child(
            "tenants/$clinicId/patients/$patientId/insuranceClaims/$claimId/$fileName",
        )
        ref.putFile(sourceUri).await()
        return ref.downloadUrl.await().toString()
    }
}
