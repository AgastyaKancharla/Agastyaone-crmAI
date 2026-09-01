package com.agastyaone.crmai.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.patients.Patient
import com.agastyaone.crmai.data.patients.PatientClinicalDetails
import kotlinx.coroutines.launch

/** Fetches the patient by ID so this is reachable directly from a nav route. */
@Composable
fun PatientClinicalEditScreen(
    clinicId: String,
    uid: String,
    patientId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.patientRepository
    val patient by repository.observePatient(clinicId, patientId).collectAsState(initial = null)
    val current = patient

    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        PatientClinicalEditContent(clinicId = clinicId, uid = uid, patient = current, onSaved = onSaved, onBack = onBack)
    }
}

/**
 * Assistant/owner-only screen for the clinical fields (allergies, chronic conditions,
 * current medications, medical history notes). Demographics are deliberately absent -
 * this screen writes only clinicalFields(), matching what firestore.rules lets an
 * assistant touch.
 */
@Composable
private fun PatientClinicalEditContent(
    clinicId: String,
    uid: String,
    patient: Patient,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository

    var allergies by remember { mutableStateOf(patient.allergies.joinToString(", ")) }
    var chronicConditions by remember { mutableStateOf(patient.chronicConditions.joinToString(", ")) }
    var currentMedications by remember { mutableStateOf(patient.currentMedications.joinToString(", ")) }
    var medicalHistoryNotes by remember { mutableStateOf(patient.medicalHistoryNotes ?: "") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit medical history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                allergies,
                { allergies = it },
                label = { Text("Allergies (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                chronicConditions,
                { chronicConditions = it },
                label = { Text("Chronic conditions (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                currentMedications,
                { currentMedications = it },
                label = { Text("Current medications (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                medicalHistoryNotes,
                { medicalHistoryNotes = it },
                label = { Text("Medical history notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )

            Button(
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isSaving = true
                    errorMessage = null
                    val clinical = PatientClinicalDetails(
                        allergies = allergies.splitToTrimmedList(),
                        chronicConditions = chronicConditions.splitToTrimmedList(),
                        currentMedications = currentMedications.splitToTrimmedList(),
                        medicalHistoryNotes = medicalHistoryNotes.ifBlank { null },
                    )
                    scope.launch {
                        runCatching {
                            repository.updateClinicalDetails(clinicId, patient.id, uid, clinical)
                        }.onSuccess {
                            isSaving = false
                            onSaved()
                        }.onFailure {
                            isSaving = false
                            errorMessage = it.message
                        }
                    }
                },
            ) { Text("Save changes") }

            if (isSaving) CircularProgressIndicator()
            errorMessage?.let { Text(it, color = Color.Red) }
        }
    }
}

private fun String.splitToTrimmedList(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }
