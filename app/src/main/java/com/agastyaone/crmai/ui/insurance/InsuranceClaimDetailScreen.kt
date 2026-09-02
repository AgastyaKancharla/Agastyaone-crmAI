package com.agastyaone.crmai.ui.insurance

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.insurance.ClaimStatus
import com.agastyaone.crmai.data.insurance.InsuranceClaim
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Status pipeline, uploaded documents (opened externally via the standard viewer intent -
 * download URLs are plain https links, so no in-app PDF renderer is needed), and
 * resolution notes once the claim reaches a resolved status. Reuses the same
 * "amount claimed, not the full invoice total" framing as the builder screen.
 */
@Composable
fun InsuranceClaimDetailScreen(
    clinicId: String,
    claimId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = ServiceLocator.insuranceClaimRepository
    val scope = rememberCoroutineScope()

    val claim by repository.observeClaim(clinicId, claimId).collectAsState(initial = null)
    val invoice by (
        claim?.let { ServiceLocator.invoiceRepository.observeInvoice(clinicId, it.invoiceId) } ?: flowOf(null)
        ).collectAsState(initial = null)

    var showStatusDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(claim?.tpaName ?: "Insurance claim") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = claim
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("TPA: ${current.tpaName}", style = MaterialTheme.typography.bodyLarge)
            Text("Invoice: ${invoice?.invoiceNumber ?: "..."}")
            Text("Submitted: ${submittedDateText(current)}")

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Amount claimed from insurance: ${formatCurrency(current.claimAmount)}", style = MaterialTheme.typography.titleMedium)
            invoice?.let {
                Text(
                    "Patient responsible for the difference: ${formatCurrency((it.total - current.claimAmount).coerceAtLeast(0.0))}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text("Status: ${ClaimStatus.fromId(current.status).label}", style = MaterialTheme.typography.titleMedium)
            current.resolutionNotes?.let { Text("Resolution notes: $it") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Documents", style = MaterialTheme.typography.titleMedium)
            if (current.documentUrls.isEmpty()) {
                Text("No documents uploaded.")
            } else {
                for ((index, url) in current.documentUrls.withIndex()) {
                    TextButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Document ${index + 1}", modifier = Modifier.fillMaxWidth()) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            OutlinedButton(onClick = { showStatusDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Update status")
            }
        }

        if (showStatusDialog) {
            UpdateClaimStatusDialog(
                currentStatus = ClaimStatus.fromId(current.status),
                currentNotes = current.resolutionNotes.orEmpty(),
                onSave = { status, notes ->
                    scope.launch {
                        repository.updateStatus(clinicId, claimId, status, notes.ifBlank { null })
                    }
                    showStatusDialog = false
                },
                onDismiss = { showStatusDialog = false },
            )
        }
    }
}

@Composable
private fun UpdateClaimStatusDialog(
    currentStatus: ClaimStatus,
    currentNotes: String,
    onSave: (ClaimStatus, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var status by remember { mutableStateOf(currentStatus) }
    var notes by remember { mutableStateOf(currentNotes) }
    val isResolvedStatus = status == ClaimStatus.APPROVED || status == ClaimStatus.DENIED || status == ClaimStatus.PAID

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update claim status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in ClaimStatus.entries) {
                    TextButton(
                        onClick = { status = option },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text((if (status == option) "> " else "") + option.label, modifier = Modifier.fillMaxWidth()) }
                }
                if (isResolvedStatus) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Resolution notes") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(status, notes) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun submittedDateText(claim: InsuranceClaim): String {
    val timestamp = claim.submittedAt ?: return "Unknown date"
    return timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
