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
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.charting.TreatmentPlan
import com.agastyaone.crmai.data.charting.TreatmentPlanStatus

/** Treatment plans for one patient, newest first. Only the owner/dentist can start a new one. */
@Composable
fun TreatmentPlanListScreen(
    clinicId: String,
    role: Role,
    patientId: String,
    onOpenPlan: (String) -> Unit,
    onAddPlan: () -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.treatmentPlanRepository
    val plans by repository.observeTreatmentPlansForPatient(clinicId, patientId).collectAsState(initial = emptyList())
    val canCreate = role == Role.OWNER

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treatment plans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(onClick = onAddPlan) {
                    Icon(Icons.Filled.Add, contentDescription = "New treatment plan")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (plans.isEmpty()) {
                Text(
                    "No treatment plans yet.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(plans, key = { it.id }) { plan -> TreatmentPlanRow(plan, onOpenPlan) }
                }
            }
        }
    }
}

@Composable
private fun TreatmentPlanRow(plan: TreatmentPlan, onOpen: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(plan.id) }) {
        ListItem(
            headlineContent = { Text(formatCurrency(plan.totalEstimate)) },
            supportingContent = { Text("${plan.lineItems.size} item(s) - ${TreatmentPlanStatus.fromId(plan.status).label}") },
        )
    }
}
