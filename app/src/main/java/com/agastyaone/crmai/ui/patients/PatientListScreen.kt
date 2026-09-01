package com.agastyaone.crmai.ui.patients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.patients.Patient

/** Fast name/phone search plus a recent-patients quick list, per the Phase 2a spec. */
@Composable
fun PatientListScreen(
    clinicId: String,
    onOpenPatient: (String) -> Unit,
    onAddPatient: () -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.patientRepository

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Patient>?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    val recentPatients by repository.observeRecentPatients(clinicId).collectAsState(initial = emptyList())

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchResults = null
            return@LaunchedEffect
        }
        isSearching = true
        searchResults = if (trimmed.first().isDigit() || trimmed.first() == '+') {
            repository.searchPatientsByPhone(clinicId, trimmed)
        } else {
            repository.searchPatientsByName(clinicId, trimmed)
        }
        isSearching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patients") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPatient) {
                Icon(Icons.Filled.Add, contentDescription = "Add patient")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or phone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            val listToShow = searchResults ?: recentPatients
            val header = if (searchResults != null) "Results" else "Recent patients"

            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(header, style = MaterialTheme.typography.labelLarge)
            }

            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (listToShow.isEmpty()) {
                Text(
                    "No patients yet.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(listToShow, key = { it.id }) { patient ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onOpenPatient(patient.id) },
                        ) {
                            ListItem(
                                headlineContent = { Text(patient.name) },
                                supportingContent = { Text(patient.phone ?: "No phone on file") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
