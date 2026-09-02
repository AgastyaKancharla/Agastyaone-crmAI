package com.agastyaone.crmai.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.billing.DEFAULT_GST_RATE_PERCENT
import com.agastyaone.crmai.data.billing.InvoiceLineItem
import com.agastyaone.crmai.data.billing.calculateGst
import com.agastyaone.crmai.data.charting.ProcedureCatalog
import com.agastyaone.crmai.data.charting.TreatmentPlan
import com.agastyaone.crmai.data.charting.TreatmentPlanStatus
import com.agastyaone.crmai.data.patients.Patient
import com.agastyaone.crmai.data.tenant.Clinic
import com.agastyaone.crmai.ui.patients.PatientSearchPicker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

/** Package-local currency formatter, mirroring ui.charting's identical same-package helper. */
fun formatCurrency(amount: Double): String = String.format(Locale.US, "₹%.2f", amount)

/**
 * Select a patient (skipped if [initialPatientId] is already known, e.g. opened from
 * PatientDetailScreen), then either pull line items from one of their accepted treatment
 * plans or add ad-hoc lines from the procedure catalog. Subtotal/GST/total recompute live
 * as lines change; billingState defaults to the clinic's own state and is editable before
 * saving, per the spec's "most invoices are intra-state, don't over-build address
 * collection" guidance.
 */
@Composable
fun InvoiceBuilderScreen(
    clinicId: String,
    uid: String,
    initialPatientId: String?,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val patientRepository = ServiceLocator.patientRepository
    val treatmentPlanRepository = ServiceLocator.treatmentPlanRepository
    val invoiceRepository = ServiceLocator.invoiceRepository
    val scope = rememberCoroutineScope()

    val clinic by ServiceLocator.tenantRepository.observeClinic(clinicId).collectAsState(initial = Clinic())

    val preselectedPatient by (
        if (initialPatientId != null) {
            patientRepository.observePatient(clinicId, initialPatientId)
        } else {
            flowOf(null)
        }
        ).collectAsState(initial = null)
    var manuallyPickedPatient by remember { mutableStateOf<Patient?>(null) }
    val patient = preselectedPatient ?: manuallyPickedPatient

    val treatmentPlans by (
        if (patient != null) {
            treatmentPlanRepository.observeTreatmentPlansForPatient(clinicId, patient.id)
        } else {
            flowOf(emptyList())
        }
        ).collectAsState(initial = emptyList())
    val acceptedPlans = treatmentPlans.filter { it.status == TreatmentPlanStatus.ACCEPTED.id }

    var lineItems by remember { mutableStateOf(emptyList<InvoiceLineItem>()) }
    var billingState by remember(clinic.state) { mutableStateOf(clinic.state) }
    var showPlanPicker by remember { mutableStateOf(false) }
    var showProcedurePicker by remember { mutableStateOf(false) }
    var draftProcedure by remember { mutableStateOf<ProcedureCatalog.Procedure?>(null) }
    var draftQuantity by remember { mutableStateOf("1") }
    var draftUnitCost by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val subtotal = lineItems.sumOf { it.lineTotal }
    val gst = calculateGst(subtotal, billingState, clinic.state)
    val total = subtotal + gst.totalTax

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New invoice") },
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
            if (initialPatientId == null) {
                PatientSearchPicker(
                    clinicId = clinicId,
                    selectedPatient = manuallyPickedPatient,
                    onPatientSelected = { manuallyPickedPatient = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Patient: ${patient?.name ?: "Loading..."}", style = MaterialTheme.typography.titleMedium)
            }

            if (patient != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Line items", style = MaterialTheme.typography.titleMedium)

                OutlinedButton(
                    onClick = { showPlanPicker = true },
                    enabled = acceptedPlans.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (acceptedPlans.isEmpty()) {
                            "No accepted treatment plans for this patient"
                        } else {
                            "Pull line items from an accepted treatment plan"
                        },
                    )
                }

                for ((index, item) in lineItems.withIndex()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("${item.procedureName} x${item.quantity}")
                            Text("HSN/SAC ${item.hsnSacCode} - ${formatCurrency(item.lineTotal)}")
                        }
                        TextButton(onClick = { lineItems = lineItems.toMutableList().apply { removeAt(index) } }) {
                            Text("Remove")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Add ad-hoc line", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { showProcedurePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(draftProcedure?.name ?: "Choose a procedure")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draftQuantity,
                        onValueChange = { draftQuantity = it },
                        label = { Text("Qty") },
                        modifier = Modifier.fillMaxWidth(0.4f),
                    )
                    OutlinedTextField(
                        value = draftUnitCost,
                        onValueChange = { draftUnitCost = it },
                        label = { Text("Unit cost (INR)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = {
                        val procedure = draftProcedure ?: return@Button
                        val quantity = draftQuantity.toIntOrNull() ?: return@Button
                        val unitCost = draftUnitCost.toDoubleOrNull() ?: return@Button
                        lineItems = lineItems + InvoiceLineItem(
                            procedureCode = procedure.code,
                            procedureName = procedure.name,
                            hsnSacCode = procedure.hsnSacCode,
                            quantity = quantity,
                            unitCost = unitCost,
                            lineTotal = quantity * unitCost,
                        )
                        draftProcedure = null
                        draftQuantity = "1"
                        draftUnitCost = ""
                    },
                    enabled = draftProcedure != null && draftQuantity.toIntOrNull() != null && draftUnitCost.toDoubleOrNull() != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add line") }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedTextField(
                    value = billingState,
                    onValueChange = { billingState = it },
                    label = { Text("Billing state") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Clinic state: ${clinic.state.ifBlank { "not set" }} - same state means CGST+SGST, different means IGST.")

                Text("Subtotal: ${formatCurrency(subtotal)}")
                if (gst.cgst > 0) Text("CGST: ${formatCurrency(gst.cgst)}")
                if (gst.sgst > 0) Text("SGST: ${formatCurrency(gst.sgst)}")
                if (gst.igst > 0) Text("IGST: ${formatCurrency(gst.igst)}")
                Text("Total: ${formatCurrency(total)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "GST computed at the default ${DEFAULT_GST_RATE_PERCENT.toInt()}% placeholder rate - " +
                        "confirm the correct rate per procedure with your accountant.",
                    style = MaterialTheme.typography.labelSmall,
                )

                errorMessage?.let { Text(it, color = Color.Red) }

                Button(
                    enabled = !isSaving && lineItems.isNotEmpty() && billingState.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val currentPatient = patient ?: return@Button
                        isSaving = true
                        errorMessage = null
                        scope.launch {
                            runCatching {
                                invoiceRepository.createInvoice(
                                    clinicId = clinicId,
                                    issuedByUid = uid,
                                    patientId = currentPatient.id,
                                    lineItems = lineItems,
                                    billingState = billingState,
                                    subtotal = subtotal,
                                    gst = gst,
                                    total = total,
                                    treatmentPlanId = null,
                                )
                            }.onSuccess {
                                isSaving = false
                                onSaved(it)
                            }.onFailure {
                                isSaving = false
                                errorMessage = it.message
                            }
                        }
                    },
                ) { Text("Create invoice") }

                if (isSaving) CircularProgressIndicator()
            }
        }
    }

    if (showPlanPicker) {
        TreatmentPlanPickerDialog(
            plans = acceptedPlans,
            onSelect = { plan ->
                lineItems = plan.parsedLineItems.map { treatmentItem ->
                    val procedure = ProcedureCatalog.SEED_PROCEDURES.firstOrNull { it.code == treatmentItem.procedureCode }
                    InvoiceLineItem(
                        procedureCode = treatmentItem.procedureCode,
                        procedureName = treatmentItem.procedureName,
                        hsnSacCode = procedure?.hsnSacCode ?: "999319",
                        quantity = 1,
                        unitCost = treatmentItem.estimatedCost,
                        lineTotal = treatmentItem.estimatedCost,
                    )
                }
                showPlanPicker = false
            },
            onDismiss = { showPlanPicker = false },
        )
    }

    if (showProcedurePicker) {
        AlertDialog(
            onDismissRequest = { showProcedurePicker = false },
            title = { Text("Choose a procedure") },
            text = {
                Column {
                    for (procedure in ProcedureCatalog.SEED_PROCEDURES) {
                        TextButton(
                            onClick = {
                                draftProcedure = procedure
                                showProcedurePicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(procedure.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProcedurePicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TreatmentPlanPickerDialog(
    plans: List<TreatmentPlan>,
    onSelect: (TreatmentPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accepted treatment plans") },
        text = {
            Column {
                for (plan in plans) {
                    TextButton(
                        onClick = { onSelect(plan) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${plan.parsedLineItems.size} item(s), ${formatCurrency(plan.totalEstimate)}",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
