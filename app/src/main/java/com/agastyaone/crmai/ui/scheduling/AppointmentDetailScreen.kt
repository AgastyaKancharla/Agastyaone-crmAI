package com.agastyaone.crmai.ui.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.scheduling.AppointmentStatus
import kotlinx.coroutines.launch

/** The linear pipeline a working appointment moves through before it's marked done. */
private val FORWARD_PIPELINE = listOf(
    AppointmentStatus.SCHEDULED,
    AppointmentStatus.CONFIRMED,
    AppointmentStatus.CHECKED_IN,
    AppointmentStatus.COMPLETED,
)

/**
 * View/edit/cancel/reschedule, gated by role: owner and receptionist get the full set
 * of controls, assistant gets a read-only view (their write attempts would be rejected
 * by firestore.rules anyway, but the UI shouldn't offer a control that can't work).
 */
@Composable
fun AppointmentDetailScreen(
    clinicId: String,
    appointmentId: String,
    role: Role,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.scheduleRepository
    val patientRepository = ServiceLocator.patientRepository
    val scope = rememberCoroutineScope()
    val canEdit = role == Role.OWNER || role == Role.RECEPTIONIST

    val appointment by repository.observeAppointment(clinicId, appointmentId).collectAsState(initial = null)

    var showReschedule by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    var showCancelForm by remember { mutableStateOf(false) }
    var showOfferPrompt by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appointment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = appointment
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val patient by patientRepository.observePatient(clinicId, current.patientId).collectAsState(initial = null)
        val currentStatus = AppointmentStatus.fromId(current.status)
        val nextStatus = FORWARD_PIPELINE.getOrNull(FORWARD_PIPELINE.indexOf(currentStatus) + 1)
        val isTerminal = isTerminalStatus(current.status)

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Patient: ${current.patientName}", style = MaterialTheme.typography.titleMedium)
            Text("Phone: ${patient?.phone ?: "Loading..."}")
            Text("Dentist: ${current.dentistUid ?: "Unassigned"}")
            Text("Time: ${current.startTime?.formattedDate()} ${current.startTime?.formattedTime()} - ${current.endTime?.formattedTime()}")
            Text("Status: ${labelForStatus(current.status)}", style = MaterialTheme.typography.titleMedium)
            if (!current.notes.isNullOrBlank()) Text("Notes: ${current.notes}")
            if (!current.cancelledReason.isNullOrBlank()) Text("Cancellation reason: ${current.cancelledReason}")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (canEdit && !isTerminal) {
                if (nextStatus != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    repository.updateAppointmentStatus(clinicId, appointmentId, nextStatus)
                                }.onFailure { errorMessage = it.message }
                            }
                        },
                    ) { Text("Mark ${labelForStatus(nextStatus.id)}") }
                }

                OutlinedButton(onClick = { showReschedule = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit time / dentist")
                }

                if (!showCancelForm) {
                    OutlinedButton(onClick = { showCancelForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel or mark no-show")
                    }
                } else {
                    OutlinedTextField(
                        cancelReason,
                        { cancelReason = it },
                        label = { Text("Reason (required)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = cancelReason.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    repository.updateAppointmentStatus(
                                        clinicId,
                                        appointmentId,
                                        AppointmentStatus.CANCELLED,
                                        cancelReason.trim(),
                                    )
                                }.onSuccess {
                                    showCancelForm = false
                                    showOfferPrompt = true
                                }.onFailure { errorMessage = it.message }
                            }
                        },
                    ) { Text("Cancel appointment") }
                    OutlinedButton(
                        enabled = cancelReason.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    repository.updateAppointmentStatus(
                                        clinicId,
                                        appointmentId,
                                        AppointmentStatus.NO_SHOW,
                                        cancelReason.trim(),
                                    )
                                }.onSuccess {
                                    showCancelForm = false
                                }.onFailure { errorMessage = it.message }
                            }
                        },
                    ) { Text("Mark as no-show") }
                }
            }

            errorMessage?.let { Text(it, color = Color.Red) }
        }

        if (showReschedule) {
            RescheduleDialog(clinicId = clinicId, appointment = current, onDismiss = { showReschedule = false })
        }
        if (showOfferPrompt) {
            OfferSlotDialog(clinicId = clinicId, onDismiss = { showOfferPrompt = false })
        }
    }
}
