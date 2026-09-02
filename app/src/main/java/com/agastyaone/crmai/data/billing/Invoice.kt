package com.agastyaone.crmai.data.billing

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class PaymentStatus(val id: String, val label: String) {
    UNPAID("unpaid", "Unpaid"),
    PARTIAL("partial", "Partially paid"),
    PAID("paid", "Paid");

    companion object {
        fun fromId(id: String): PaymentStatus = entries.firstOrNull { it.id == id } ?: UNPAID
    }
}

/**
 * One line on an invoice. [hsnSacCode] comes straight from [com.agastyaone.crmai.data.charting.ProcedureCatalog.Procedure.hsnSacCode]
 * at the time the line was added - see that file's doc comment for why it's a
 * placeholder, not a verified GST code.
 */
data class InvoiceLineItem(
    val procedureCode: String = "",
    val procedureName: String = "",
    val hsnSacCode: String = "",
    val quantity: Int = 1,
    val unitCost: Double = 0.0,
    val lineTotal: Double = 0.0,
) {
    companion object {
        fun fromRaw(raw: Map<*, *>): InvoiceLineItem = InvoiceLineItem(
            procedureCode = raw["procedureCode"] as? String ?: "",
            procedureName = raw["procedureName"] as? String ?: "",
            hsnSacCode = raw["hsnSacCode"] as? String ?: "",
            quantity = (raw["quantity"] as? Number)?.toInt() ?: 1,
            unitCost = (raw["unitCost"] as? Number)?.toDouble() ?: 0.0,
            lineTotal = (raw["lineTotal"] as? Number)?.toDouble() ?: 0.0,
        )
    }

    fun toRaw(): Map<String, Any?> = mapOf(
        "procedureCode" to procedureCode,
        "procedureName" to procedureName,
        "hsnSacCode" to hsnSacCode,
        "quantity" to quantity,
        "unitCost" to unitCost,
        "lineTotal" to lineTotal,
    )
}

/**
 * `tenants/{clinicId}/invoices/{invoiceId}`. [lineItems] stays raw maps for the same
 * reason as [com.agastyaone.crmai.data.charting.TreatmentPlan]'s nested fields - see
 * [InvoiceLineItem.fromRaw]/[toRaw]. [cgst]/[sgst]/[igst] are pre-computed amounts, not
 * rates - see [com.agastyaone.crmai.data.billing.calculateGst] for how they're derived
 * from [billingState] vs the clinic's own state.
 */
data class Invoice(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val invoiceNumber: String = "",
    val issuedAt: Timestamp? = null,
    val issuedByUid: String? = null,
    val lineItems: List<Map<String, Any?>> = emptyList(),
    val billingState: String = "",
    val subtotal: Double = 0.0,
    val cgst: Double = 0.0,
    val sgst: Double = 0.0,
    val igst: Double = 0.0,
    val total: Double = 0.0,
    val paymentStatus: String = PaymentStatus.UNPAID.id,
    val amountPaid: Double = 0.0,
    val razorpayPaymentLinkId: String? = null,
    val razorpayStatus: String? = null,
    val treatmentPlanId: String? = null,
) {
    val parsedLineItems: List<InvoiceLineItem>
        get() = lineItems.mapNotNull { it as? Map<*, *> }.map { InvoiceLineItem.fromRaw(it) }
}
