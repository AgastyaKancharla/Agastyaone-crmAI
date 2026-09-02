package com.agastyaone.crmai.data.storage

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await

/**
 * Uploads a captured/picked X-ray or intraoral photo to
 * `tenants/{clinicId}/patients/{patientId}/imaging/{fileName}` in Firebase Storage (see
 * storage.rules) and returns its download URL for the imaging metadata doc - the same
 * path convention [SignatureUploader] uses for signatures, per the Phase 3b spec.
 *
 * `putFile` (rather than reading the whole image into a Bitmap first, like
 * [SignatureUploader] does for a small hand-drawn signature) streams the file directly
 * and lets the Storage SDK infer content type from the source [Uri]'s ContentResolver
 * metadata, which camera-capture and gallery-picker URIs always carry.
 */
class ImageUploader(private val storage: FirebaseStorage = Firebase.storage) {

    suspend fun upload(clinicId: String, patientId: String, fileName: String, sourceUri: Uri): String {
        val ref = storage.reference.child("tenants/$clinicId/patients/$patientId/imaging/$fileName")
        ref.putFile(sourceUri).await()
        return ref.downloadUrl.await().toString()
    }
}
