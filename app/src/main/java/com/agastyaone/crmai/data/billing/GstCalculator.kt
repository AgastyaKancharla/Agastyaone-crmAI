package com.agastyaone.crmai.data.billing

/**
 * Pre-computed tax amounts (not rates) for one invoice - [cgst]/[sgst] are populated
 * together and [igst] is zero, or vice versa, never a mix of both, matching Indian GST's
 * intra-state (CGST+SGST) vs inter-state (IGST) split.
 */
data class GstBreakdown(val cgst: Double, val sgst: Double, val igst: Double) {
    val totalTax: Double get() = cgst + sgst + igst
}

/**
 * The spec leaves the actual GST rate open ("each typically half the total GST rate",
 * without naming the rate) - [DEFAULT_GST_RATE_PERCENT] is this project's placeholder
 * default (18%, the standard slab most non-exempt services fall under), used to
 * pre-fill the invoice builder's editable rate field. Like [ProcedureCatalog]'s HSN/SAC
 * codes, it is NOT a verified rate: many routine dental/medical services are GST-exempt
 * under Indian law, and the correct rate (0%, 5%, 12%, 18%...) depends on the specific
 * procedure's actual HSN/SAC classification, which the clinic's accountant must confirm.
 */
const val DEFAULT_GST_RATE_PERCENT: Double = 18.0

/**
 * Same state as the clinic -> CGST + SGST, each half the total rate. Different state
 * -> IGST, the full rate, no CGST/SGST. Compared case-insensitively and trimmed since
 * both values come from free-text state fields (clinic signup, invoice billingState),
 * not a constrained state picker - see the Phase 4a spec's note on why this project
 * doesn't build a full address-collection flow for the rare inter-state case.
 */
fun calculateGst(subtotal: Double, billingState: String, clinicState: String, gstRatePercent: Double = DEFAULT_GST_RATE_PERCENT): GstBreakdown {
    val totalTax = subtotal * gstRatePercent / 100.0
    return if (billingState.trim().equals(clinicState.trim(), ignoreCase = true)) {
        val half = totalTax / 2.0
        GstBreakdown(cgst = half, sgst = half, igst = 0.0)
    } else {
        GstBreakdown(cgst = 0.0, sgst = 0.0, igst = totalTax)
    }
}
