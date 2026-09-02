package com.agastyaone.crmai.ui.charting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.charting.ProcedureCatalog
import com.agastyaone.crmai.data.charting.ToothChart
import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import com.agastyaone.crmai.data.charting.TreatmentLineItem
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dentist-only, reachable only from the treatment-plan list (not directly by other roles).
 * Builds a whole new plan locally, line item by line item, then saves it in one write -
 * simpler and less state-sync risk than loading/merging an existing plan's remote state.
 */
@Composable
fun TreatmentPlanBuilderScreen(
    clinicId: String,
    uid: String,
    patientId: String,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.treatmentPlanRepository
    val scope = rememberCoroutineScope()
    val numberingSystem by ServiceLocator.tenantRepository
        .observeToothNumberingSystem(clinicId)
        .collectAsState(initial = ToothNumberingSystem.FDI)

    var lineItems by remember { mutableStateOf<List<TreatmentLineItem>>(emptyList()) }
    var showProcedurePicker by remember { mutableStateOf(false) }
    var pendingProcedure by remember { mutableStateOf<ProcedureCatalog.Procedure?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val totalEstimate = lineItems.sumOf { it.estimatedCost }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New treatment plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showProcedurePicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add procedure")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Total estimate: ${formatCurrency(totalEstimate)}",
                style = MaterialTheme.typography.titleLarge,
            )

            if (lineItems.isEmpty()) {
                Text(
                    "No line items yet. Tap + to add a procedure.",
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(lineItems) { index, item ->
                        LineItemRow(
                            item = item,
                            numberingSystem = numberingSystem,
                            onCostChange = { cost ->
                                lineItems = lineItems.toMutableList().also { it[index] = it[index].copy(estimatedCost = cost) }
                            },
                            onRemove = {
                                lineItems = lineItems.toMutableList().also { it.removeAt(index) }
                            },
                        )
                    }
                }
            }

            Button(
                enabled = lineItems.isNotEmpty() && !isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                onClick = {
                    isSaving = true
                    scope.launch {
                        val planId = repository.createTreatmentPlan(clinicId, uid, patientId, lineItems)
                        isSaving = false
                        onSaved(planId)
                    }
                },
            ) { Text("Save plan") }

            if (isSaving) CircularProgressIndicator()
        }
    }

    if (showProcedurePicker) {
        ProcedurePickerDialog(
            onSelect = { procedure ->
                showProcedurePicker = false
                if (procedure.isToothSpecific) {
                    pendingProcedure = procedure
                } else {
                    lineItems = lineItems + TreatmentLineItem(
                        procedureCode = procedure.code,
                        procedureName = procedure.name,
                        toothNumber = null,
                    )
                }
            },
            onDismiss = { showProcedurePicker = false },
        )
    }

    val procedureAwaitingTooth = pendingProcedure
    if (procedureAwaitingTooth != null) {
        // Treatment plans aren't tied to a specific charting/dentition, so the tooth picker
        // defaults to the adult layout - see ToothNumberPickerDialog's default parameter.
        ToothNumberPickerDialog(
            numberingSystem = numberingSystem,
            onSelect = { toothNumber ->
                lineItems = lineItems + TreatmentLineItem(
                    procedureCode = procedureAwaitingTooth.code,
                    procedureName = procedureAwaitingTooth.name,
                    toothNumber = toothNumber,
                )
                pendingProcedure = null
            },
            onDismiss = { pendingProcedure = null },
        )
    }
}

@Composable
private fun LineItemRow(
    item: TreatmentLineItem,
    numberingSystem: ToothNumberingSystem,
    onCostChange: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    var costText by remember(item.procedureCode, item.toothNumber) {
        mutableStateOf(if (item.estimatedCost == 0.0) "" else item.estimatedCost.toString())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.procedureName, style = MaterialTheme.typography.titleSmall)
                val toothNumber = item.toothNumber
                if (toothNumber != null) {
                    Text("Tooth ${ToothChart.displayLabel(toothNumber, numberingSystem)}", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedTextField(
                    value = costText,
                    onValueChange = { value ->
                        costText = value
                        onCostChange(value.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("Estimated cost") },
                    modifier = Modifier.padding(top = 4.dp),
                    singleLine = true,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove line item")
            }
        }
    }
}

@Composable
private fun ProcedurePickerDialog(onSelect: (ProcedureCatalog.Procedure) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a procedure") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (procedure in ProcedureCatalog.SEED_PROCEDURES) {
                    TextButton(onClick = { onSelect(procedure) }, modifier = Modifier.fillMaxWidth()) {
                        Text(procedure.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

fun formatCurrency(amount: Double): String = String.format(Locale.US, "₹%.2f", amount)
