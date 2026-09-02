package com.agastyaone.crmai.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.billing.Invoice
import com.agastyaone.crmai.data.billing.PaymentStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One screen serves both list views the spec asks for: pass a [patientId] for the
 * per-patient view (opened from PatientDetailScreen), or null for the clinic-wide view
 * (opened from the Owner/Receptionist dashboard) - same query shape either way, just
 * scoped or not.
 */
@Composable
fun InvoiceListScreen(
    clinicId: String,
    patientId: String?,
    onOpenInvoice: (String) -> Unit,
    onNewInvoice: () -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.invoiceRepository
    val invoices by (
        if (patientId != null) {
            repository.observeInvoicesForPatient(clinicId, patientId)
        } else {
            repository.observeAllInvoices(clinicId)
        }
        ).collectAsState(initial = emptyList())

    var statusFilter by remember { mutableStateOf<PaymentStatus?>(null) }
    val filtered = statusFilter?.let { status -> invoices.filter { it.paymentStatus == status.id } } ?: invoices

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewInvoice) {
                Icon(Icons.Filled.Add, contentDescription = "New invoice")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All") })
                for (status in PaymentStatus.entries) {
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = { statusFilter = status },
                        label = { Text(status.label) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No invoices yet. Tap + to create one.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered) { invoice ->
                        InvoiceRow(invoice = invoice, onClick = { onOpenInvoice(invoice.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(invoice: Invoice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(invoice.invoiceNumber, style = MaterialTheme.typography.bodyLarge)
                Text(issuedDateText(invoice), style = MaterialTheme.typography.labelSmall)
            }
            Column {
                Text(formatCurrency(invoice.total), style = MaterialTheme.typography.bodyLarge)
                Text(PaymentStatus.fromId(invoice.paymentStatus).label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun issuedDateText(invoice: Invoice): String {
    val timestamp = invoice.issuedAt ?: return "Unknown date"
    return timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
