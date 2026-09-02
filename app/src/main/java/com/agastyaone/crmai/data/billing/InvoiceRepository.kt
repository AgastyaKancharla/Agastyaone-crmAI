package com.agastyaone.crmai.data.billing

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class InvoiceRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun tenantDoc(clinicId: String) = db.collection("tenants").document(clinicId)

    private fun invoicesCollection(clinicId: String) = tenantDoc(clinicId).collection("invoices")

    fun observeInvoicesForPatient(clinicId: String, patientId: String): Flow<List<Invoice>> =
        invoicesCollection(clinicId)
            .whereEqualTo("patientId", patientId)
            .orderBy("issuedAt", Query.Direction.DESCENDING)
            .asFlow()

    fun observeAllInvoices(clinicId: String): Flow<List<Invoice>> =
        invoicesCollection(clinicId)
            .orderBy("issuedAt", Query.Direction.DESCENDING)
            .asFlow()

    fun observeInvoice(clinicId: String, invoiceId: String): Flow<Invoice?> = callbackFlow {
        val registration = invoicesCollection(clinicId).document(invoiceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Invoice::class.java))
            }
        awaitClose { registration.remove() }
    }

    /**
     * Invoice numbers are sequential per clinic (INV-0001, INV-0002, ...). The counter
     * lives on the tenant doc (`invoiceCounter`) and is bumped inside the same
     * transaction that creates the invoice document, so two staff members creating
     * invoices at the same moment can't collide on a number - Firestore retries the
     * transaction under contention rather than letting both reads see the same stale
     * counter value. firestore.rules gives the receptionist a narrow allowance to touch
     * only that one field on the otherwise owner-only tenant doc (see touchesOnly()).
     */
    suspend fun createInvoice(
        clinicId: String,
        issuedByUid: String,
        patientId: String,
        lineItems: List<InvoiceLineItem>,
        billingState: String,
        subtotal: Double,
        gst: GstBreakdown,
        total: Double,
        treatmentPlanId: String?,
    ): String {
        val tenantRef = tenantDoc(clinicId)
        val invoiceRef = invoicesCollection(clinicId).document()
        db.runTransaction { transaction ->
            val tenantSnapshot = transaction.get(tenantRef)
            val nextNumber = (tenantSnapshot.getLong("invoiceCounter") ?: 0L) + 1
            val invoiceNumber = "INV-%04d".format(nextNumber)
            val data = hashMapOf<String, Any?>(
                "patientId" to patientId,
                "invoiceNumber" to invoiceNumber,
                "issuedAt" to Timestamp.now(),
                "issuedByUid" to issuedByUid,
                "lineItems" to lineItems.map { it.toRaw() },
                "billingState" to billingState,
                "subtotal" to subtotal,
                "cgst" to gst.cgst,
                "sgst" to gst.sgst,
                "igst" to gst.igst,
                "total" to total,
                "paymentStatus" to PaymentStatus.UNPAID.id,
                "amountPaid" to 0.0,
                "razorpayPaymentLinkId" to null,
                "razorpayStatus" to null,
                "treatmentPlanId" to treatmentPlanId,
            )
            transaction.set(invoiceRef, data)
            transaction.update(tenantRef, "invoiceCounter", nextNumber)
        }.await()
        return invoiceRef.id
    }

    suspend fun recordPayment(clinicId: String, invoiceId: String, paymentStatus: PaymentStatus, amountPaid: Double) {
        invoicesCollection(clinicId).document(invoiceId).update(
            mapOf(
                "paymentStatus" to paymentStatus.id,
                "amountPaid" to amountPaid,
            ),
        ).await()
    }

    suspend fun voidInvoice(clinicId: String, invoiceId: String) {
        invoicesCollection(clinicId).document(invoiceId).delete().await()
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
