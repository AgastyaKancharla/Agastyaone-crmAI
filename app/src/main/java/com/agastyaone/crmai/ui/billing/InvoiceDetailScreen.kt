package com.agastyaone.crmai.ui.billing

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.billing.Invoice
import com.agastyaone.crmai.data.billing.InvoicePdfGenerator
import com.agastyaone.crmai.data.billing.PaymentStatus
import com.agastyaone.crmai.data.tenant.Clinic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders the invoice, offers "Share PDF" (generates a real PDF, then the standard
 * Android share sheet - see [shareInvoicePdf]) and "Record payment" (manual
 * paid/partial/unpaid update, needed regardless of Phase 4b's future Razorpay
 * integration since plenty of patients pay cash/card in person). Voiding is here too
 * since firestore.rules already grants owner/receptionist delete and the role-permissions
 * section explicitly lists "void invoices" as part of this module.
 */
@Composable
fun InvoiceDetailScreen(
    clinicId: String,
    invoiceId: String,
    onVoided: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = ServiceLocator.invoiceRepository
    val scope = rememberCoroutineScope()

    val invoice by repository.observeInvoice(clinicId, invoiceId).collectAsState(initial = null)
    val patient by (
        invoice?.let { ServiceLocator.patientRepository.observePatient(clinicId, it.patientId) } ?: flowOf(null)
        ).collectAsState(initial = null)
    val clinic by ServiceLocator.tenantRepository.observeClinic(clinicId).collectAsState(initial = Clinic())

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(invoice?.invoiceNumber ?: "Invoice") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = invoice
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
            Text("Patient: ${patient?.name ?: "..."}", style = MaterialTheme.typography.bodyLarge)
            Text("Issued: ${issuedDateText(current)}")
            Text("Billing state: ${current.billingState}")

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Line items", style = MaterialTheme.typography.titleMedium)
            for (item in current.parsedLineItems) {
                Text("${item.procedureName} x${item.quantity} (HSN/SAC ${item.hsnSacCode}) - ${formatCurrency(item.lineTotal)}")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Subtotal: ${formatCurrency(current.subtotal)}")
            if (current.cgst > 0) Text("CGST: ${formatCurrency(current.cgst)}")
            if (current.sgst > 0) Text("SGST: ${formatCurrency(current.sgst)}")
            if (current.igst > 0) Text("IGST: ${formatCurrency(current.igst)}")
            Text("Total: ${formatCurrency(current.total)}", style = MaterialTheme.typography.titleMedium)
            Text("Amount paid: ${formatCurrency(current.amountPaid)}")
            Text("Status: ${PaymentStatus.fromId(current.paymentStatus).label}")

            Text(
                "HSN/SAC codes are placeholder values, not verified for GST filing - confirm with your accountant.",
                style = MaterialTheme.typography.labelSmall,
            )

            errorMessage?.let { Text(it) }

            Button(
                enabled = !isSharing,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val currentPatient = patient ?: return@Button
                    isSharing = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            // PDF rendering + file write is real I/O/CPU work, not
                            // something to run on the composition/main thread.
                            withContext(Dispatchers.IO) {
                                InvoicePdfGenerator.generate(context, clinic, currentPatient, current)
                            }
                        }.onSuccess { file ->
                            isSharing = false
                            shareInvoicePdf(context, file)
                        }.onFailure {
                            isSharing = false
                            errorMessage = it.message
                        }
                    }
                },
            ) { Text(if (isSharing) "Preparing PDF..." else "Share PDF") }

            OutlinedButton(onClick = { showPaymentDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Record payment")
            }

            OutlinedButton(onClick = { showVoidConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Void invoice")
            }
        }

        if (showPaymentDialog) {
            RecordPaymentDialog(
                invoice = current,
                onRecord = { status, amountPaid ->
                    scope.launch { repository.recordPayment(clinicId, invoiceId, status, amountPaid) }
                    showPaymentDialog = false
                },
                onDismiss = { showPaymentDialog = false },
            )
        }

        if (showVoidConfirm) {
            AlertDialog(
                onDismissRequest = { showVoidConfirm = false },
                title = { Text("Void this invoice?") },
                text = { Text("This deletes the invoice record. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showVoidConfirm = false
                        scope.launch {
                            repository.voidInvoice(clinicId, invoiceId)
                            onVoided()
                        }
                    }) { Text("Void") }
                },
                dismissButton = {
                    TextButton(onClick = { showVoidConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun RecordPaymentDialog(
    invoice: Invoice,
    onRecord: (PaymentStatus, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var status by remember { mutableStateOf(PaymentStatus.fromId(invoice.paymentStatus)) }
    var amountPaidText by remember { mutableStateOf(invoice.amountPaid.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in PaymentStatus.entries) {
                    TextButton(
                        onClick = { status = option },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text((if (status == option) "> " else "") + option.label, modifier = Modifier.fillMaxWidth()) }
                }
                OutlinedTextField(
                    value = amountPaidText,
                    onValueChange = { amountPaidText = it },
                    label = { Text("Amount paid (INR)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountPaid = amountPaidText.toDoubleOrNull() ?: invoice.amountPaid
                onRecord(status, amountPaid)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun issuedDateText(invoice: Invoice): String {
    val timestamp = invoice.issuedAt ?: return "Unknown date"
    return timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
