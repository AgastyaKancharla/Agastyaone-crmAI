package com.agastyaone.crmai.ui.patients

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
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
import com.agastyaone.crmai.data.patients.DataRequest
import com.agastyaone.crmai.data.patients.DataRequestStatus
import com.agastyaone.crmai.data.patients.DataRequestType
import com.agastyaone.crmai.data.patients.Patient
import kotlinx.coroutines.launch

/**
 * Owner-only: log and track a patient's DPDP access/correction/erasure request. This
 * phase does not execute erasure automatically - it just gives a real, auditable record
 * that a request was made, what was done, and when.
 */
@Composable
fun DataRequestsScreen(clinicId: String, uid: String, onBack: () -> Unit) {
    val repository = ServiceLocator.patientRepository
    val requests by repository.observeDataRequests(clinicId).collectAsState(initial = emptyList())
    var showLogForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (showLogForm) {
            LogDataRequestForm(
                clinicId = clinicId,
                uid = uid,
                modifier = Modifier.padding(padding),
                onDone = { showLogForm = false },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Button(onClick = { showLogForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Log a new request")
                }
                if (requests.isEmpty()) {
                    Text(
                        "No data requests logged yet.",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(requests, key = { it.id }) { request ->
                            DataRequestRow(clinicId = clinicId, request = request)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataRequestRow(clinicId: String, request: DataRequest) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository
    var notes by remember(request.id) { mutableStateOf(request.notes ?: "") }

    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Patient: ${request.patientId}")
            Text("Type: ${request.requestType}")
            Text("Status: ${request.status}")
            OutlinedTextField(
                notes,
                { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (status in DataRequestStatus.entries) {
                    Button(
                        enabled = request.status != status.id,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                repository.updateDataRequestStatus(
                                    clinicId,
                                    request.id,
                                    status,
                                    notes.ifBlank { null },
                                )
                            }
                        },
                    ) { Text("Mark ${status.id}") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDataRequestForm(clinicId: String, uid: String, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository

    var patientQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Patient>>(emptyList()) }
    var selectedPatient by remember { mutableStateOf<Patient?>(null) }

    var requestType by remember { mutableStateOf(DataRequestType.ACCESS) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = patientQuery,
            onValueChange = {
                patientQuery = it
                selectedPatient = null
                scope.launch {
                    searchResults = if (it.isBlank()) emptyList() else repository.searchPatientsByName(clinicId, it.trim())
                }
            },
            label = { Text("Find patient by name") },
            modifier = Modifier.fillMaxWidth(),
        )

        val currentSelection = selectedPatient
        if (currentSelection != null) {
            Text("Selected: ${currentSelection.name}")
        } else {
            for (candidate in searchResults) {
                Button(
                    onClick = {
                        selectedPatient = candidate
                        patientQuery = candidate.name
                        searchResults = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Select ${candidate.name}") }
            }
        }

        ExposedDropdownMenuBox(expanded = typeMenuExpanded, onExpandedChange = { typeMenuExpanded = it }) {
            OutlinedTextField(
                value = requestType.id,
                onValueChange = {},
                readOnly = true,
                label = { Text("Request type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                for (candidate in DataRequestType.entries) {
                    DropdownMenuItem(
                        text = { Text(candidate.id) },
                        onClick = { requestType = candidate; typeMenuExpanded = false },
                    )
                }
            }
        }

        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

        errorMessage?.let { Text(it, color = Color.Red) }

        Button(
            enabled = !isSaving && selectedPatient != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val patient = selectedPatient ?: return@Button
                isSaving = true
                errorMessage = null
                scope.launch {
                    runCatching {
                        repository.logDataRequest(clinicId, uid, patient.id, requestType, notes.ifBlank { null })
                    }.onSuccess {
                        isSaving = false
                        onDone()
                    }.onFailure {
                        isSaving = false
                        errorMessage = it.message
                    }
                }
            },
        ) { Text("Log request") }

        if (isSaving) CircularProgressIndicator()
    }
}
