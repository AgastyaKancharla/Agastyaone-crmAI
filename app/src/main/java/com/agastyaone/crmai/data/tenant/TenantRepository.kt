package com.agastyaone.crmai.data.tenant

import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Clinic-level settings stored directly on the `tenants/{clinicId}` document, which the
 * owner can already write per firestore.rules - no rule change needed for a new field there.
 */
class TenantRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun tenantDoc(clinicId: String) = db.collection("tenants").document(clinicId)

    /** Display-layer only - see [com.agastyaone.crmai.data.charting.ToothChart] for why. */
    fun observeToothNumberingSystem(clinicId: String): Flow<ToothNumberingSystem> = callbackFlow {
        val registration = tenantDoc(clinicId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(ToothNumberingSystem.fromId(snapshot?.getString("toothNumberingSystem")))
        }
        awaitClose { registration.remove() }
    }

    suspend fun setToothNumberingSystem(clinicId: String, system: ToothNumberingSystem) {
        tenantDoc(clinicId).set(mapOf("toothNumberingSystem" to system.id), SetOptions.merge()).await()
    }
}
