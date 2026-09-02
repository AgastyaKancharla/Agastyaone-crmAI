package com.agastyaone.crmai.ui.charting

import androidx.compose.ui.graphics.Color
import com.agastyaone.crmai.data.charting.ToothConditionType

/** Colour-coded legend for tooth conditions, shown always-visible per the Phase 3a spec. */
fun colorForCondition(conditionId: String?): Color = when (ToothConditionType.fromId(conditionId ?: "")) {
    ToothConditionType.CARIES -> Color(0xFFE57373)
    ToothConditionType.FILLED -> Color(0xFF81C784)
    ToothConditionType.MISSING -> Color(0xFFBDBDBD)
    ToothConditionType.CROWN -> Color(0xFFFFD54F)
    ToothConditionType.ROOT_CANAL_TREATED -> Color(0xFFBA68C8)
    ToothConditionType.IMPLANT -> Color(0xFF4FC3F7)
    ToothConditionType.EXTRACTION_PLANNED -> Color(0xFFFF8A65)
    ToothConditionType.FRACTURED -> Color(0xFF90A4AE)
    ToothConditionType.IMPACTED -> Color(0xFFA1887F)
    null -> Color(0xFFECEFF1)
}
