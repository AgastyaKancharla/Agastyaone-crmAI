package com.agastyaone.crmai.ui.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.scheduling.Appointment
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Long-press-to-reschedule surface, satisfying the Phase 2b spec's "long-press or drag
 * to reschedule" - long-press was chosen over a drag gesture since a plain text-field
 * dialog is far more reliable to get right without a local Android compiler in the loop
 * (see the ExposedDropdownMenu mistake earlier in this project), and the spec offers
 * either as an acceptable interaction.
 */
@Composable
fun RescheduleDialog(clinicId: String, appointment: Appointment, onDismiss: () -> Unit) {
    val repository = ServiceLocator.scheduleRepository
    val scope = rememberCoroutineScope()

    val originalStart = appointment.startTime?.toLocalDateTime()
    val originalEnd = appointment.endTime?.toLocalDateTime()

    var date by remember { mutableStateOf(originalStart?.toLocalDate()?.format(DATE_FORMAT) ?: "") }
    var startTimeText by remember { mutableStateOf(originalStart?.toLocalTime()?.format(TIME_FORMAT) ?: "") }
    var endTimeText by remember { mutableStateOf(originalEnd?.toLocalTime()?.format(TIME_FORMAT) ?: "") }
    var dentistUid by remember { mutableStateOf(appointment.dentistUid ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule appointment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(startTimeText, { startTimeText = it }, label = { Text("Start time (HH:mm)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endTimeText, { endTimeText = it }, label = { Text("End time (HH:mm)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dentistUid, { dentistUid = it }, label = { Text("Dentist UID") }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = Color.Red) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val parsedDate = LocalDate.parse(date, DATE_FORMAT)
                    val start = parsedDate.combineWithTime(LocalTime.parse(startTimeText, TIME_FORMAT))
                    val end = parsedDate.combineWithTime(LocalTime.parse(endTimeText, TIME_FORMAT))
                    Pair(start, end)
                }.onSuccess { (start, end) ->
                    scope.launch {
                        runCatching {
                            repository.rescheduleAppointment(
                                clinicId,
                                appointment.id,
                                start,
                                end,
                                dentistUid.ifBlank { null },
                            )
                        }.onSuccess {
                            onDismiss()
                        }.onFailure {
                            errorMessage = it.message
                        }
                    }
                }.onFailure {
                    errorMessage = "Enter a valid date and time"
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
