package com.agastyaone.crmai.ui.scheduling

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone: ZoneId = ZoneId.systemDefault()
val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDate.startOfDayTimestamp(): Timestamp = Timestamp(this.atStartOfDay(zone).toInstant().epochSecond, 0)

fun LocalDate.endOfDayTimestamp(): Timestamp = this.plusDays(1).startOfDayTimestamp()

fun Timestamp.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this.toDate().toInstant(), zone)

fun LocalDate.combineWithTime(time: LocalTime): Timestamp {
    val instant = this.atTime(time).atZone(zone).toInstant()
    return Timestamp(instant.epochSecond, instant.nano)
}

fun Timestamp.formattedTime(): String = this.toLocalDateTime().toLocalTime().format(TIME_FORMAT)

fun Timestamp.formattedDate(): String = this.toLocalDateTime().toLocalDate().format(DATE_FORMAT)
