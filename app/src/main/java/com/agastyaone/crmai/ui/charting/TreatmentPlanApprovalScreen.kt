package com.agastyaone.crmai.ui.charting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.charting.ToothChart
import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import com.agastyaone.crmai.data.charting.TreatmentPlanStatus
import com.agastyaone.crmai.ui.signature.SignaturePad
import com.agastyaone.crmai.ui.signature.SignaturePadHint
import com.agastyaone.crmai.ui.signature.rememberSignaturePadState
import kotlinx.coroutines.launch

/**
 * Read-only plan summary plus the patient-approval step - reuses the Phase 2a signature
 * pad and uploader rather than a second signature component. Owner/dentist-only screen:
 * they operate the tablet the patient signs on, same as the intake flow.
 */
@Composable
fun TreatmentPlanApprovalScreen(
    clinicId: String,
    patientId: String,
    planId: String,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.treatmentPlanRepository
    val signatureUploader = ServiceLocator.signatureUploader
    val scope = rememberCoroutineScope()

    val plan by repository.observeTreatmentPlan(clinicId, planId).collectAsState(initial = null)
    val numberingSystem by ServiceLocator.tenantRepository
        .observeToothNumberingSystem(clinicId)
        .collectAsState(initial = ToothNumberingSystem.FDI)
    val signatureState = rememberSignaturePadState()
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treatment plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = plan
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val status = TreatmentPlanStatus.fromId(current.status)

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Line items", style = MaterialTheme.typography.titleLarge)
            for (item in current.parsedLineItems) {
                val toothSuffix = item.toothNumber?.let { " - Tooth ${ToothChart.displayLabel(it, numberingSystem)}" } ?: ""
                Text("${item.procedureName}$toothSuffix: ${formatCurrency(item.estimatedCost)}")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Total estimate: ${formatCurrency(current.totalEstimate)}", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${status.label}", style = MaterialTheme.typography.titleMedium)

            when (status) {
                TreatmentPlanStatus.DRAFT -> {
                    Button(onClick = {
                        scope.launch { repository.updateStatus(clinicId, planId, TreatmentPlanStatus.PROPOSED) }
                    }) { Text("Propose to patient") }
                }
                TreatmentPlanStatus.PROPOSED -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Patient signature", style = MaterialTheme.typography.titleMedium)
                    SignaturePad(
                        state = signatureState,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                    SignaturePadHint()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { signatureState.clear() }) { Text("Clear") }
                        Button(
                            enabled = !signatureState.isEmpty && !isSaving,
                            onClick = {
                                val bitmap = signatureState.toBitmap() ?: return@Button
                                isSaving = true
                                errorMessage = null
                                scope.launch {
                                    runCatching {
                                        val signatureUrl = signatureUploader.upload(
                                            clinicId,
                                            patientId,
                                            "treatmentPlan_$planId.png",
                                            bitmap,
                                        )
                                        repository.recordApproval(clinicId, planId, signatureUrl)
                                    }.onSuccess {
                                        isSaving = false
                                    }.onFailure {
                                        isSaving = false
                                        errorMessage = it.message
                                    }
                                }
                            },
                        ) { Text("Approve & sign") }
                    }
                    if (isSaving) CircularProgressIndicator()
                }
                else -> {
                    Text("Approved ${current.patientApprovedAt?.toDate()?.let { "on $it" } ?: ""}")
                }
            }

            errorMessage?.let { Text(it, color = Color.Red) }
        }
    }
}
