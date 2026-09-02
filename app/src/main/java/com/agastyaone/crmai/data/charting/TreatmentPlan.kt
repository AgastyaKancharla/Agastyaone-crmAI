package com.agastyaone.crmai.data.charting

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class TreatmentPlanStatus(val id: String, val label: String) {
    DRAFT("draft", "Draft"),
    PROPOSED("proposed", "Proposed"),
    ACCEPTED("accepted", "Accepted"),
    IN_PROGRESS("inProgress", "In progress"),
    COMPLETED("completed", "Completed");

    companion object {
        fun fromId(id: String): TreatmentPlanStatus = entries.firstOrNull { it.id == id } ?: DRAFT
    }
}

enum class LineItemStatus(val id: String) {
    PENDING("pending"),
    DONE("done");

    companion object {
        fun fromId(id: String): LineItemStatus = entries.firstOrNull { it.id == id } ?: PENDING
    }
}

/**
 * One procedure on a treatment plan. Deliberately structured close to an eventual invoice
 * line (a procedure, an optional tooth, a cost, a status) so a later billing phase can turn
 * an accepted plan into invoice lines directly - but no invoice is built in this phase, and
 * [procedureCode] here is an internal catalog id, not a verified HSN/SAC or billing code
 * (see [ProcedureCatalog]).
 */
data class TreatmentLineItem(
    val procedureCode: String = "",
    val procedureName: String = "",
    val toothNumber: String? = null,
    val estimatedCost: Double = 0.0,
    val status: String = LineItemStatus.PENDING.id,
) {
    companion object {
        fun fromRaw(raw: Map<*, *>): TreatmentLineItem = TreatmentLineItem(
            procedureCode = raw["procedureCode"] as? String ?: "",
            procedureName = raw["procedureName"] as? String ?: "",
            toothNumber = raw["toothNumber"] as? String,
            estimatedCost = (raw["estimatedCost"] as? Number)?.toDouble() ?: 0.0,
            status = raw["status"] as? String ?: LineItemStatus.PENDING.id,
        )
    }

    fun toRaw(): Map<String, Any?> = mapOf(
        "procedureCode" to procedureCode,
        "procedureName" to procedureName,
        "toothNumber" to toothNumber,
        "estimatedCost" to estimatedCost,
        "status" to status,
    )
}

/**
 * `tenants/{clinicId}/treatmentPlans/{planId}`. [lineItems] is kept as raw maps for the same
 * reason as [Charting]'s nested fields - see [TreatmentLineItem.fromRaw]/[TreatmentLineItem.toRaw].
 */
data class TreatmentPlan(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val createdByUid: String? = null,
    val createdAt: Timestamp? = null,
    val status: String = TreatmentPlanStatus.DRAFT.id,
    val lineItems: List<Map<String, Any?>> = emptyList(),
    val totalEstimate: Double = 0.0,
    val patientApprovalSignatureUrl: String? = null,
    val patientApprovedAt: Timestamp? = null,
) {
    val parsedLineItems: List<TreatmentLineItem> get() = lineItems.mapNotNull { it as? Map<*, *> }.map { TreatmentLineItem.fromRaw(it) }
}
