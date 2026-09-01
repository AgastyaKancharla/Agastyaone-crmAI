package com.agastyaone.crmai.ui.scheduling

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.agastyaone.crmai.data.patients.Patient
import com.agastyaone.crmai.data.scheduling.AppointmentSource
import com.agastyaone.crmai.ui.patients.PatientSearchPicker
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Single-tap FAB flow from the calendar: find an existing patient (reusing the Phase 2a
 * search), or send a new patient through the existing patient-creation screen first -
 * once they're created, the receptionist returns to the calendar and taps the FAB again
 * to finish booking them, now that they're searchable here too.
 */
@Composable
fun WalkInScreen(
    clinicId: String,
    uid: String,
    onGoToPatientCreation: () -> Unit,
    onBooked: () -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.scheduleRepository
    val scope = rememberCoroutineScope()

    var selectedPatient by remember { mutableStateOf<Patient?>(null) }
    var dentistUid by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().format(DATE_FORMAT)) }
    var startTimeText by remember { mutableStateOf("") }
    var endTimeText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Walk-in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PatientSearchPicker(
                clinicId = clinicId,
                selectedPatient = selectedPatient,
                onPatientSelected = { selectedPatient = it },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = onGoToPatientCreation, modifier = Modifier.fillMaxWidth()) {
                Text("Patient not found - add them first")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            OutlinedTextField(dentistUid, { dentistUid = it }, label = { Text("Dentist UID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(startTimeText, { startTimeText = it }, label = { Text("Start time (HH:mm)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(endTimeText, { endTimeText = it }, label = { Text("End time (HH:mm)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            errorMessage?.let { Text(it, color = Color.Red) }

            Button(
                enabled = !isSaving && selectedPatient != null && startTimeText.isNotBlank() && endTimeText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val patient = selectedPatient ?: return@Button
                    val parsed = runCatching {
                        val parsedDate = LocalDate.parse(date, DATE_FORMAT)
                        val start = parsedDate.combineWithTime(LocalTime.parse(startTimeText, TIME_FORMAT))
                        val end = parsedDate.combineWithTime(LocalTime.parse(endTimeText, TIME_FORMAT))
                        Pair(start, end)
                    }
                    if (parsed.isFailure) {
                        errorMessage = "Enter a valid date and time"
                        return@Button
                    }
                    val (start, end) = parsed.getOrThrow()
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            repository.createAppointment(
                                clinicId = clinicId,
                                createdByUid = uid,
                                patientId = patient.id,
                                patientName = patient.name,
                                dentistUid = dentistUid.ifBlank { null },
                                startTime = start,
                                endTime = end,
                                source = AppointmentSource.WALK_IN,
                                notes = notes.ifBlank { null },
                            )
                        }.onSuccess {
                            isSaving = false
                            onBooked()
                        }.onFailure {
                            isSaving = false
                            errorMessage = it.message
                        }
                    }
                },
            ) { Text("Book walk-in") }

            if (isSaving) CircularProgressIndicator()
        }
    }
}
