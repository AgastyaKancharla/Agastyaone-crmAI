package com.agastyaone.crmai.ui.charting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.charting.Charting
import com.agastyaone.crmai.data.charting.DentitionType
import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import kotlinx.coroutines.launch

/** Chartings for one patient, newest first. Only the owner/dentist can start a new one. */
@Composable
fun ChartingListScreen(
    clinicId: String,
    uid: String,
    role: Role,
    patientId: String,
    onOpenCharting: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.chartingRepository
    val tenantRepository = ServiceLocator.tenantRepository
    val scope = rememberCoroutineScope()
    val chartings by repository.observeChartingsForPatient(clinicId, patientId).collectAsState(initial = emptyList())
    val numberingSystem by tenantRepository.observeToothNumberingSystem(clinicId)
        .collectAsState(initial = ToothNumberingSystem.FDI)
    val canCreate = role == Role.OWNER
    var showDentitionPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chartings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Clinic-wide display preference (Phase 3a spec) - Universal vs FDI tooth
                    // numbering. Owner-only to change; storage stays FDI either way, see
                    // ToothChart's doc comment.
                    if (role == Role.OWNER) {
                        TextButton(onClick = {
                            val next = if (numberingSystem == ToothNumberingSystem.FDI) {
                                ToothNumberingSystem.UNIVERSAL
                            } else {
                                ToothNumberingSystem.FDI
                            }
                            scope.launch { tenantRepository.setToothNumberingSystem(clinicId, next) }
                        }) { Text("Numbering: ${numberingSystem.id}") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(onClick = { showDentitionPicker = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New charting")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (chartings.isEmpty()) {
                Text(
                    "No chartings recorded yet.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chartings, key = { it.id }) { charting -> ChartingRow(charting, onOpenCharting) }
                }
            }
        }
    }

    if (showDentitionPicker) {
        AlertDialog(
            onDismissRequest = { showDentitionPicker = false },
            title = { Text("Dentition") },
            text = { Text("Is this patient's chart for their adult or primary (baby) teeth?") },
            confirmButton = {
                TextButton(onClick = {
                    showDentitionPicker = false
                    scope.launch {
                        val chartingId = repository.createCharting(clinicId, uid, patientId, DentitionType.ADULT)
                        onOpenCharting(chartingId)
                    }
                }) { Text("Adult") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDentitionPicker = false
                    scope.launch {
                        val chartingId = repository.createCharting(clinicId, uid, patientId, DentitionType.PRIMARY)
                        onOpenCharting(chartingId)
                    }
                }) { Text("Primary") }
            },
        )
    }
}

@Composable
private fun ChartingRow(charting: Charting, onOpen: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(charting.id) }) {
        ListItem(
            headlineContent = { Text("Visit ${charting.visitDate?.toDate()?.toString() ?: "date pending"}") },
            supportingContent = { Text("${charting.toothConditions.size} tooth marking(s) recorded") },
        )
    }
}
