package com.agastyaone.crmai.data.charting

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class DentitionType(val id: String) {
    ADULT("adult"),
    PRIMARY("primary");

    companion object {
        fun fromId(id: String): DentitionType = entries.firstOrNull { it.id == id } ?: ADULT
    }
}

enum class ToothSurface(val id: String, val label: String) {
    MESIAL("mesial", "Mesial"),
    DISTAL("distal", "Distal"),
    OCCLUSAL("occlusal", "Occlusal"),
    BUCCAL("buccal", "Buccal"),
    LINGUAL("lingual", "Lingual"),
}

enum class ToothConditionType(val id: String, val label: String) {
    CARIES("caries", "Caries"),
    FILLED("filled", "Filled"),
    MISSING("missing", "Missing"),
    CROWN("crown", "Crown"),
    ROOT_CANAL_TREATED("rootCanalTreated", "Root canal treated"),
    IMPLANT("implant", "Implant"),
    EXTRACTION_PLANNED("extractionPlanned", "Extraction planned"),
    FRACTURED("fractured", "Fractured"),
    IMPACTED("impacted", "Impacted");

    companion object {
        fun fromId(id: String): ToothConditionType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One tooth's diagnostic markings. Most conditions are per-surface (a filling only affects
 * the occlusal surface, say); a handful describe the whole tooth (missing, implant, extraction
 * planned) and are stored under [WHOLE_TOOTH_SURFACE] instead of a real surface id.
 */
data class ToothConditionEntry(
    val surfaces: Map<String, String> = emptyMap(),
    val notes: String? = null,
) {
    companion object {
        const val WHOLE_TOOTH_SURFACE = "whole"

        fun fromRaw(raw: Map<*, *>?): ToothConditionEntry {
            if (raw == null) return ToothConditionEntry()
            val rawSurfaces = raw["surfaces"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val surfaces = rawSurfaces.entries.associate { (key, value) -> key.toString() to value.toString() }
            return ToothConditionEntry(surfaces = surfaces, notes = raw["notes"] as? String)
        }
    }

    val isEmpty: Boolean get() = surfaces.isEmpty()

    /** The condition shown as this tooth's overall colour in the odontogram grid - the
     * whole-tooth marking if there is one, otherwise whichever surface was set first. */
    val primaryCondition: String? get() = surfaces[WHOLE_TOOTH_SURFACE] ?: surfaces.values.firstOrNull()

    fun toRaw(): Map<String, Any?> = mapOf("surfaces" to surfaces, "notes" to notes)
}

/**
 * A tooth's 6-point periodontal reading. Points are ordered mesiobuccal, buccal, distobuccal,
 * mesiolingual, lingual, distolingual - matching [POINT_LABELS] and the Phase 3a spec's schema.
 */
data class PeriodontalReading(
    val pocketDepths: List<Int> = List(POINT_COUNT) { 0 },
    val bleeding: List<Boolean> = List(POINT_COUNT) { false },
    val mobilityGrade: Int = 0,
) {
    companion object {
        const val POINT_COUNT = 6
        val POINT_LABELS = listOf("MB", "B", "DB", "ML", "L", "DL")

        fun fromRaw(raw: Map<*, *>?): PeriodontalReading {
            if (raw == null) return PeriodontalReading()
            val pocketDepths = (raw["pocketDepths"] as? List<*>)
                ?.map { (it as? Number)?.toInt() ?: 0 }
                ?.takeIf { it.size == POINT_COUNT }
                ?: List(POINT_COUNT) { 0 }
            val bleeding = (raw["bleeding"] as? List<*>)
                ?.map { it as? Boolean ?: false }
                ?.takeIf { it.size == POINT_COUNT }
                ?: List(POINT_COUNT) { false }
            val mobilityGrade = (raw["mobilityGrade"] as? Number)?.toInt() ?: 0
            return PeriodontalReading(pocketDepths, bleeding, mobilityGrade)
        }
    }

    val isRecorded: Boolean get() = pocketDepths.any { it > 0 } || bleeding.any { it } || mobilityGrade > 0

    fun toRaw(): Map<String, Any?> = mapOf(
        "pocketDepths" to pocketDepths,
        "bleeding" to bleeding,
        "mobilityGrade" to mobilityGrade,
    )
}

/**
 * `tenants/{clinicId}/chartings/{chartingId}`. [toothConditions] and [periodontalChart] are
 * kept as raw maps (Firestore's native nested-map representation) rather than typed nested
 * objects - the app writes/reads individual tooth entries via [ToothConditionEntry]/
 * [PeriodontalReading]'s `toRaw()`/`fromRaw()` rather than relying on Firestore's POJO mapper
 * to reconstruct nested custom types inside a Map value.
 */
data class Charting(
    @DocumentId val id: String = "",
    val patientId: String = "",
    val visitDate: Timestamp? = null,
    val dentistUid: String? = null,
    val dentitionType: String = DentitionType.ADULT.id,
    val toothConditions: Map<String, Any?> = emptyMap(),
    val periodontalChart: Map<String, Any?> = emptyMap(),
    val lastEditedByUid: String? = null,
    val lastEditedAt: Timestamp? = null,
) {
    fun toothCondition(toothNumber: String): ToothConditionEntry =
        ToothConditionEntry.fromRaw(toothConditions[toothNumber] as? Map<*, *>)

    fun periodontalReading(toothNumber: String): PeriodontalReading =
        PeriodontalReading.fromRaw(periodontalChart[toothNumber] as? Map<*, *>)
}
