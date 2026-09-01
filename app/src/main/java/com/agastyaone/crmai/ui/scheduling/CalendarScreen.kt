package com.agastyaone.crmai.ui.scheduling

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.scheduling.Appointment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class CalendarViewMode { DAY, WEEK }

/** Clinic opening hours for the day timeline - matches a typical dental clinic's day. */
private val TIMELINE_HOURS = 8..19
private val DAY_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

/**
 * Day view (scrollable hourly timeline) by default, week view as a 7-day overview that
 * jumps back into day view on tap. Assistant gets read-only access here (no FAB, no
 * waitlist entry point, no long-press reschedule) per the Phase 2b role table.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    clinicId: String,
    role: Role,
    onBack: () -> Unit,
    onOpenAppointment: (String) -> Unit,
    onAddWalkIn: () -> Unit,
    onOpenWaitlist: () -> Unit,
) {
    val repository = ServiceLocator.scheduleRepository
    val canEdit = role == Role.OWNER || role == Role.RECEPTIONIST

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.DAY) }
    var rescheduleTarget by remember { mutableStateOf<Appointment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canEdit) {
                        TextButton(onClick = onOpenWaitlist) { Text("Waitlist") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = onAddWalkIn) {
                    Icon(Icons.Filled.Add, contentDescription = "Walk-in quick add")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                TextButton(onClick = { viewMode = CalendarViewMode.DAY }) { Text("Day") }
                TextButton(onClick = { viewMode = CalendarViewMode.WEEK }) { Text("Week") }
            }

            if (viewMode == CalendarViewMode.DAY) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { selectedDate = selectedDate.minusDays(1) }) { Text("< Prev") }
                    Text(selectedDate.format(DAY_HEADER_FORMAT), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { selectedDate = selectedDate.plusDays(1) }) { Text("Next >") }
                }

                val appointments by remember(selectedDate) {
                    repository.observeAppointmentsInRange(
                        clinicId,
                        selectedDate.startOfDayTimestamp(),
                        selectedDate.endOfDayTimestamp(),
                    )
                }.collectAsState(initial = emptyList())

                DayTimeline(
                    appointments = appointments,
                    canEdit = canEdit,
                    onOpenAppointment = onOpenAppointment,
                    onLongPressAppointment = { rescheduleTarget = it },
                )
            } else {
                WeekOverview(
                    clinicId = clinicId,
                    anchorDate = selectedDate,
                    onSelectDay = { day ->
                        selectedDate = day
                        viewMode = CalendarViewMode.DAY
                    },
                )
            }
        }
    }

    rescheduleTarget?.let { appointment ->
        RescheduleDialog(
            clinicId = clinicId,
            appointment = appointment,
            onDismiss = { rescheduleTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayTimeline(
    appointments: List<Appointment>,
    canEdit: Boolean,
    onOpenAppointment: (String) -> Unit,
    onLongPressAppointment: (Appointment) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(TIMELINE_HOURS.toList()) { hour ->
            val hourAppointments = appointments.filter { it.startTime?.toLocalDateTime()?.hour == hour }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    text = String.format(Locale.US, "%02d:00", hour),
                    modifier = Modifier.width(56.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hourAppointments.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    } else {
                        for (appointment in hourAppointments) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onOpenAppointment(appointment.id) },
                                        onLongClick = { if (canEdit) onLongPressAppointment(appointment) },
                                    ),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = colorForStatus(appointment.status),
                                ),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "${appointment.startTime?.formattedTime()} - ${appointment.endTime?.formattedTime()}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(appointment.patientName)
                                    Text(labelForStatus(appointment.status), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekOverview(
    clinicId: String,
    anchorDate: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    val repository = ServiceLocator.scheduleRepository
    val weekStart = anchorDate.with(DayOfWeek.MONDAY)
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }

    val weekAppointments by remember(weekStart) {
        repository.observeAppointmentsInRange(
            clinicId,
            weekStart.startOfDayTimestamp(),
            weekStart.plusDays(7).startOfDayTimestamp(),
        )
    }.collectAsState(initial = emptyList())

    LazyRow(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { day ->
            val count = weekAppointments.count { it.startTime?.toLocalDateTime()?.toLocalDate() == day }
            Card(
                modifier = Modifier.width(120.dp).clickable { onSelectDay(day) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US), fontWeight = FontWeight.Bold)
                    Text(day.format(DateTimeFormatter.ofPattern("d MMM")))
                    Text("$count appointment${if (count == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
