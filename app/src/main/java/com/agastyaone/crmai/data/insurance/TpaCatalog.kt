package com.agastyaone.crmai.data.insurance

/**
 * Autocomplete suggestions for the claim-creation TPA field (Phase 4c spec: "free text
 * with autocomplete suggestions from a short seeded list of common Indian TPAs - not a
 * hard enum, clinics will encounter TPAs not on any list you seed"). This list is a
 * convenience, never a validation constraint - [com.agastyaone.crmai.data.insurance.InsuranceClaim.tpaName]
 * accepts any non-blank string.
 */
object TpaCatalog {
    val SEED_TPAS: List<String> = listOf(
        "Medi Assist",
        "Paramount Health Services",
        "Star Health",
        "MDIndia Health Insurance",
        "Vidal Health Insurance",
        "Health India Insurance TPA",
        "Family Health Plan (FHPL)",
        "Raksha Health Insurance TPA",
        "Genins India",
        "Heritage Health Insurance TPA",
    )
}
