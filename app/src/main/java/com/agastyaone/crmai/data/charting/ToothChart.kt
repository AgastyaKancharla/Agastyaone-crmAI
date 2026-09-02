package com.agastyaone.crmai.data.charting

data class ToothArches(val upper: List<String>, val lower: List<String>, val midlineIndex: Int)

enum class ToothNumberingSystem(val id: String) {
    FDI("FDI"),
    UNIVERSAL("Universal");

    companion object {
        fun fromId(id: String?): ToothNumberingSystem = entries.firstOrNull { it.id == id } ?: FDI
    }
}

/**
 * Fixed FDI tooth layouts and the FDI<->Universal display conversion. Chart data is always
 * stored keyed by FDI number regardless of the tenant's display preference
 * ([ToothNumberingSystem]) - Universal is purely a display-layer relabelling, so a clinic
 * switching the setting later never orphans or renumbers existing chart data.
 */
object ToothChart {
    // Upper right -> upper left, then lower left -> lower right - the standard FDI charting order.
    val ADULT_FDI: List<String> = listOf(
        "18", "17", "16", "15", "14", "13", "12", "11", "21", "22", "23", "24", "25", "26", "27", "28",
        "48", "47", "46", "45", "44", "43", "42", "41", "31", "32", "33", "34", "35", "36", "37", "38",
    )

    val PRIMARY_FDI: List<String> = listOf(
        "55", "54", "53", "52", "51", "61", "62", "63", "64", "65",
        "85", "84", "83", "82", "81", "71", "72", "73", "74", "75",
    )

    // Universal numbering (1-32) only covers the adult permanent dentition; a primary chart
    // displayed under "Universal" falls back to its FDI label since there's no single
    // standard Universal scheme for primary teeth worth hardcoding here.
    private val FDI_TO_UNIVERSAL: Map<String, String> = mapOf(
        "18" to "1", "17" to "2", "16" to "3", "15" to "4", "14" to "5", "13" to "6", "12" to "7", "11" to "8",
        "21" to "9", "22" to "10", "23" to "11", "24" to "12", "25" to "13", "26" to "14", "27" to "15", "28" to "16",
        "38" to "17", "37" to "18", "36" to "19", "35" to "20", "34" to "21", "33" to "22", "32" to "23", "31" to "24",
        "41" to "25", "42" to "26", "43" to "27", "44" to "28", "45" to "29", "46" to "30", "47" to "31", "48" to "32",
    )

    fun teethFor(dentitionType: String): List<String> =
        if (DentitionType.fromId(dentitionType) == DentitionType.PRIMARY) PRIMARY_FDI else ADULT_FDI

    /** The upper/lower arch split (with the quadrant midline index within each), shared by
     * every tooth-diagram composable - the odontogram grid and the treatment plan's
     * tooth-number picker both lay teeth out the same way. */
    fun archesFor(dentitionType: String): ToothArches {
        val teeth = teethFor(dentitionType)
        val half = teeth.size / 2
        return ToothArches(upper = teeth.subList(0, half), lower = teeth.subList(half, teeth.size), midlineIndex = half / 2)
    }

    fun displayLabel(fdiToothNumber: String, system: ToothNumberingSystem): String =
        if (system == ToothNumberingSystem.UNIVERSAL) {
            FDI_TO_UNIVERSAL[fdiToothNumber] ?: fdiToothNumber
        } else {
            fdiToothNumber
        }
}
