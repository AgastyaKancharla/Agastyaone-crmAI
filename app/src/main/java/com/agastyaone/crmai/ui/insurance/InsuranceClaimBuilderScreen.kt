package com.agastyaone.crmai.ui.insurance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.insurance.TpaCatalog
import kotlinx.coroutines.launch
import java.util.Locale

fun formatCurrency(amount: Double): String = String.format(Locale.US, "₹%.2f", amount)

/**
 * Opened from a specific invoice (Phase 4c spec section 3). [claimAmount] is explicitly
 * framed as "amount claimed from insurance" rather than a stand-in for the full invoice
 * total - partial coverage is the norm, so the remaining patient-owed balance is shown
 * live as the amount is typed.
 *
 * The claimId is reserved up front (see [com.agastyaone.crmai.data.insurance.InsuranceClaimRepository.newClaimId])
 * because Storage document paths are keyed by it (storage.rules) - documents upload
 * against that reserved id as they're picked, and the Firestore doc is only written once
 * every upload has succeeded.
 */
@Composable
fun InsuranceClaimBuilderScreen(
    clinicId: String,
    uid: String,
    invoiceId: String,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val claimRepository = ServiceLocator.insuranceClaimRepository
    val documentUploader = ServiceLocator.claimDocumentUploader
    val scope = rememberCoroutineScope()

    val invoice by ServiceLocator.invoiceRepository.observeInvoice(clinicId, invoiceId).collectAsState(initial = null)
    val claimId = remember(clinicId) { claimRepository.newClaimId(clinicId) }

    var tpaName by remember { mutableStateOf("") }
    var claimAmountText by remember { mutableStateOf("") }
    var documentUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) documentUris = documentUris + uri
    }

    val currentInvoice = invoice
    val claimAmount = claimAmountText.toDoubleOrNull() ?: 0.0
    val patientOwed = (currentInvoice?.total ?: 0.0) - claimAmount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File insurance claim") },
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
            Text(
                "Invoice ${currentInvoice?.invoiceNumber ?: "..."} - Total ${formatCurrency(currentInvoice?.total ?: 0.0)}",
                style = MaterialTheme.typography.bodyLarge,
            )

            OutlinedTextField(
                value = tpaName,
                onValueChange = { tpaName = it },
                label = { Text("TPA / insurer name") },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TpaCatalog.SEED_TPAS) { suggestion ->
                    AssistChip(onClick = { tpaName = suggestion }, label = { Text(suggestion) })
                }
            }

            OutlinedTextField(
                value = claimAmountText,
                onValueChange = { claimAmountText = it },
                label = { Text("Amount claimed from insurance (INR)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Patient remains responsible for the difference: ${formatCurrency(patientOwed.coerceAtLeast(0.0))}",
                style = MaterialTheme.typography.labelSmall,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Supporting documents", style = MaterialTheme.typography.titleMedium)
            for ((index, uri) in documentUris.withIndex()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(fileNameForUri(context, uri, index), modifier = Modifier.padding(vertical = 8.dp))
                    IconButton(onClick = { documentUris = documentUris.filterIndexed { i, _ -> i != index } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove document")
                    }
                }
            }
            OutlinedButton(
                onClick = { documentPicker.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add document (claim form, PDF or photo)") }

            errorMessage?.let { Text(it, color = Color.Red) }

            Button(
                enabled = currentInvoice != null && tpaName.isNotBlank() && claimAmount > 0 && !isSaving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val patientId = currentInvoice?.patientId ?: return@Button
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            val uploadedUrls = documentUris.mapIndexed { index, uri ->
                                documentUploader.upload(
                                    clinicId = clinicId,
                                    patientId = patientId,
                                    claimId = claimId,
                                    fileName = fileNameForUri(context, uri, index),
                                    sourceUri = uri,
                                )
                            }
                            claimRepository.createClaim(
                                clinicId = clinicId,
                                claimId = claimId,
                                submittedByUid = uid,
                                patientId = patientId,
                                invoiceId = invoiceId,
                                tpaName = tpaName.trim(),
                                claimAmount = claimAmount,
                                documentUrls = uploadedUrls,
                            )
                        }.onSuccess {
                            isSaving = false
                            onSaved(claimId)
                        }.onFailure {
                            isSaving = false
                            errorMessage = it.message
                        }
                    }
                },
            ) { Text("Submit claim") }

            if (isSaving) CircularProgressIndicator()
        }
    }
}

/** A stable, extension-carrying Storage object name derived from the picked document's MIME type. */
private fun fileNameForUri(context: android.content.Context, uri: Uri, index: Int): String {
    val mimeType = context.contentResolver.getType(uri)
    val extension = when {
        mimeType == "application/pdf" -> "pdf"
        mimeType?.startsWith("image/") == true -> mimeType.removePrefix("image/")
        else -> "bin"
    }
    return "${System.currentTimeMillis()}_$index.$extension"
}
