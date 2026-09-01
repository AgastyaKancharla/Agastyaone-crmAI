package com.agastyaone.crmai.data.storage

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Uploads a captured [com.agastyaone.crmai.ui.signature.SignaturePadState] bitmap to
 * `tenants/{clinicId}/patients/{patientId}/signatures/{fileName}` in Firebase Storage
 * (see storage.rules) and returns its download URL for the consent/intake-form doc.
 */
class SignatureUploader(private val storage: FirebaseStorage = Firebase.storage) {

    suspend fun upload(clinicId: String, patientId: String, fileName: String, bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        val ref = storage.reference.child("tenants/$clinicId/patients/$patientId/signatures/$fileName")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }
}
