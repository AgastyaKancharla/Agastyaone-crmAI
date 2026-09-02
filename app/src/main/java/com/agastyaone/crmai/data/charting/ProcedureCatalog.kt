package com.agastyaone.crmai.data.charting

/**
 * Starter procedure names for the treatment-plan line-item picker.
 *
 * NOT authoritative for billing: [Procedure.code] is an internal catalog id, not a GST
 * HSN/SAC code, and none of these carry a default cost (pricing varies by clinic). Procedure
 * codes and HSN/SAC tax mapping must be verified by the clinic's own accountant before this
 * ever feeds a real invoice (a later phase) - see the README.
 */
object ProcedureCatalog {
    data class Procedure(val code: String, val name: String, val isToothSpecific: Boolean = true)

    val SEED_PROCEDURES: List<Procedure> = listOf(
        Procedure("CONSULT", "Consultation", isToothSpecific = false),
        Procedure("SCALING", "Scaling & Polishing", isToothSpecific = false),
        Procedure("COMPOSITE_FILLING", "Composite Filling"),
        Procedure("RCT", "Root Canal Treatment"),
        Procedure("CROWN_METAL", "Crown (Metal)"),
        Procedure("CROWN_PFM", "Crown (PFM)"),
        Procedure("CROWN_ZIRCONIA", "Crown (Zirconia)"),
        Procedure("EXTRACTION_SIMPLE", "Simple Extraction"),
        Procedure("EXTRACTION_SURGICAL", "Surgical Extraction"),
        Procedure("DENTURE_COMPLETE", "Complete Denture", isToothSpecific = false),
        Procedure("DENTURE_PARTIAL", "Partial Denture", isToothSpecific = false),
        Procedure("ORTHO_CONSULT", "Orthodontic Consultation", isToothSpecific = false),
        Procedure("IMPLANT_PLACEMENT", "Implant Placement"),
        Procedure("TEETH_WHITENING", "Teeth Whitening", isToothSpecific = false),
    )
}
