package com.agastyaone.crmai.ui.patients

import androidx.compose.foundation.background
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.patients.ConsentType

/**
 * The EDR (Electronic Dental Record) full-record view. The allergy banner is a safety
 * feature, not decoration: it's always shown while [com.agastyaone.crmai.data.patients.Patient.hasAllergies]
 * is true, with no way to dismiss it away.
 */
@Composable
fun PatientDetailScreen(
    clinicId: String,
    patientId: String,
    role: Role,
    onBack: () -> Unit,
    onEditDemographics: () -> Unit,
    onEditClinicalDetails: () -> Unit,
    onStartIntake: () -> Unit,
    onOpenChartings: () -> Unit,
    onOpenTreatmentPlans: () -> Unit,
) {
    val repository = ServiceLocator.patientRepository
    val patient by repository.observePatient(clinicId, patientId).collectAsState(initial = null)
    val consents by repository.observeConsents(clinicId, patientId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(patient?.name ?: "Patient record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = patient
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (current.hasAllergies) {
                AllergyBanner(allergies = current.allergies)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionHeader("Demographics")
                DetailRow("Phone", current.phone)
                DetailRow("Email", current.email)
                DetailRow("Date of birth", current.dob)
                DetailRow("Gender", current.gender)
                DetailRow("Address", current.address)
                DetailRow("Blood group", current.bloodGroup)
                DetailRow("Emergency contact", current.emergencyContactName)
                DetailRow("Emergency contact phone", current.emergencyContactPhone)

                if (role == Role.OWNER || role == Role.RECEPTIONIST) {
                    OutlinedButton(onClick = onEditDemographics, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit demographics")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionHeader("Medical history")
                DetailRow("Chronic conditions", current.chronicConditions.joinToString().ifBlank { "None recorded" })
                DetailRow("Current medications", current.currentMedications.joinToString().ifBlank { "None recorded" })
                DetailRow("Notes", current.medicalHistoryNotes.orEmpty().ifBlank { "None recorded" })

                if (role == Role.OWNER || role == Role.ASSISTANT) {
                    OutlinedButton(onClick = onEditClinicalDetails, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit medical history")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionHeader("Consents on file")
                if (consents.isEmpty()) {
                    Text("No consents captured yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    for (consentType in ConsentType.entries) {
                        val consent = consents.firstOrNull { it.consentType == consentType.id }
                        DetailRow(
                            label = consentType.id,
                            value = when {
                                consent == null -> "Not captured"
                                consent.granted -> "Granted"
                                else -> "Not granted / withdrawn"
                            },
                        )
                    }
                }

                if (role == Role.OWNER || role == Role.RECEPTIONIST) {
                    Button(onClick = onStartIntake, modifier = Modifier.fillMaxWidth()) {
                        Text("Run digital intake")
                    }
                }

                // Phase 3a - odontogram/periodontal charting and treatment plans. Receptionist
                // and Lab Coordinator get neither button at all; firestore.rules would reject
                // their reads anyway, but there's no reason to show a dead end.
                if (role == Role.OWNER || role == Role.ASSISTANT) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader("Clinical charting")
                    OutlinedButton(onClick = onOpenChartings, modifier = Modifier.fillMaxWidth()) {
                        Text("Chartings")
                    }
                    OutlinedButton(onClick = onOpenTreatmentPlans, modifier = Modifier.fillMaxWidth()) {
                        Text("Treatment plans")
                    }
                }
            }
        }
    }
}

@Composable
private fun AllergyBanner(allergies: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error)
            .padding(16.dp),
    ) {
        Column {
            Text(
                "ALLERGY ALERT",
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                allergies.joinToString(),
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value?.ifBlank { "Not on file" } ?: "Not on file", style = MaterialTheme.typography.bodyMedium)
    }
}
