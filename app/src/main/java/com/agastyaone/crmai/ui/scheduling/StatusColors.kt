package com.agastyaone.crmai.ui.scheduling

import androidx.compose.ui.graphics.Color
import com.agastyaone.crmai.data.scheduling.AppointmentStatus

/** Calendar/detail colour-coding by appointment status, per the Phase 2b spec. */
fun colorForStatus(status: String): Color = when (AppointmentStatus.fromId(status)) {
    AppointmentStatus.SCHEDULED -> Color(0xFF90CAF9)
    AppointmentStatus.CONFIRMED -> Color(0xFF81C784)
    AppointmentStatus.CHECKED_IN -> Color(0xFFFFB74D)
    AppointmentStatus.COMPLETED -> Color(0xFFB0BEC5)
    AppointmentStatus.CANCELLED -> Color(0xFFE57373)
    AppointmentStatus.NO_SHOW -> Color(0xFFCE93D8)
}

fun labelForStatus(status: String): String = when (AppointmentStatus.fromId(status)) {
    AppointmentStatus.SCHEDULED -> "Scheduled"
    AppointmentStatus.CONFIRMED -> "Confirmed"
    AppointmentStatus.CHECKED_IN -> "Checked in"
    AppointmentStatus.COMPLETED -> "Completed"
    AppointmentStatus.CANCELLED -> "Cancelled"
    AppointmentStatus.NO_SHOW -> "No-show"
}
