package com.agastyaone.crmai.ui.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.patients.Patient
import com.agastyaone.crmai.data.scheduling.WaitlistEntry
import com.agastyaone.crmai.data.scheduling.WaitlistStatus
import com.agastyaone.crmai.ui.patients.PatientSearchPicker
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Owner/receptionist-only, matching the waitlist collection's firestore.rules access. */
@Composable
fun WaitlistScreen(clinicId: String, uid: String, onBack: () -> Unit) {
    val repository = ServiceLocator.scheduleRepository
    val entries by repository.observeWaitlist(clinicId).collectAsState(initial = emptyList())
    var showAddForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Waitlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (showAddForm) {
            AddWaitlistEntryForm(
                clinicId = clinicId,
                uid = uid,
                modifier = Modifier.padding(padding),
                onDone = { showAddForm = false },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Button(onClick = { showAddForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add to waitlist")
                }
                if (entries.isEmpty()) {
                    Text("No one is on the waitlist.", modifier = Modifier.fillMaxWidth().padding(24.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            WaitlistRow(clinicId = clinicId, entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitlistRow(clinicId: String, entry: WaitlistEntry) {
    val repository = ServiceLocator.scheduleRepository
    val patientRepository = ServiceLocator.patientRepository
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.patientName)
            Text("Preferred dates: ${entry.preferredDates.joinToString { it.formattedDate() }.ifBlank { "None given" }}")
            Text("Status: ${entry.status}")
            if (!entry.notes.isNullOrBlank()) Text("Notes: ${entry.notes}")

            if (entry.status == WaitlistStatus.WAITING.id) {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            repository.updateWaitlistStatus(clinicId, entry.id, WaitlistStatus.OFFERED)
                        }
                    }
                }) { Text("Offer this slot") }
            } else if (entry.status == WaitlistStatus.OFFERED.id) {
                val patient by patientRepository.observePatient(clinicId, entry.patientId).collectAsState(initial = null)
                Text("Contact: ${patient?.phone ?: "Loading..."}")
            }
        }
    }
}

@Composable
private fun AddWaitlistEntryForm(clinicId: String, uid: String, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.scheduleRepository

    var selectedPatient by remember { mutableStateOf<Patient?>(null) }
    var preferredDatesText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PatientSearchPicker(
            clinicId = clinicId,
            selectedPatient = selectedPatient,
            onPatientSelected = { selectedPatient = it },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            preferredDatesText,
            { preferredDatesText = it },
            label = { Text("Preferred dates, comma-separated (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

        errorMessage?.let { Text(it, color = Color.Red) }

        Button(
            enabled = !isSaving && selectedPatient != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val patient = selectedPatient ?: return@Button
                val preferredDates = runCatching {
                    preferredDatesText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { LocalDate.parse(it, DATE_FORMAT).startOfDayTimestamp() }
                }
                if (preferredDates.isFailure) {
                    errorMessage = "Enter dates as YYYY-MM-DD, comma-separated"
                    return@Button
                }
                isSaving = true
                errorMessage = null
                scope.launch {
                    runCatching {
                        repository.addToWaitlist(
                            clinicId,
                            uid,
                            patient.id,
                            patient.name,
                            preferredDates.getOrThrow(),
                            notes.ifBlank { null },
                        )
                    }.onSuccess {
                        isSaving = false
                        onDone()
                    }.onFailure {
                        isSaving = false
                        errorMessage = it.message
                    }
                }
            },
        ) { Text("Add to waitlist") }

        if (isSaving) CircularProgressIndicator()
    }
}
