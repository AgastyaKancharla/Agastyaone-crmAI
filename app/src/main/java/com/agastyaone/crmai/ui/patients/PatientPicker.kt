package com.agastyaone.crmai.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.patients.Patient
import kotlinx.coroutines.launch

/**
 * Name-search patient picker, factored out of the Phase 2a data-request form so
 * Phase 2b's walk-in flow reuses the same search instead of a second implementation.
 * A `patientId` must reference a real patient (firestore.rules' patientExists()),
 * so scheduling always routes through a selection here rather than free-typed text.
 */
@Composable
fun PatientSearchPicker(
    clinicId: String,
    selectedPatient: Patient?,
    onPatientSelected: (Patient?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = ServiceLocator.patientRepository
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf(selectedPatient?.name ?: "") }
    var searchResults by remember { mutableStateOf<List<Patient>>(emptyList()) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onPatientSelected(null)
                scope.launch {
                    searchResults = if (it.isBlank()) emptyList() else repository.searchPatientsByName(clinicId, it.trim())
                }
            },
            label = { Text("Find patient by name") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (selectedPatient != null) {
            Text("Selected: ${selectedPatient.name}")
        } else {
            for (candidate in searchResults) {
                Button(
                    onClick = {
                        onPatientSelected(candidate)
                        query = candidate.name
                        searchResults = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Select ${candidate.name}") }
            }
        }
    }
}
