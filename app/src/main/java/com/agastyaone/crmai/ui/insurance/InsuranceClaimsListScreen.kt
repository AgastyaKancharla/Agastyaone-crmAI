package com.agastyaone.crmai.ui.insurance

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.agastyaone.crmai.data.insurance.ClaimStatus
import com.agastyaone.crmai.data.insurance.InsuranceClaim
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Clinic-wide, filterable by status - the spec allows either kanban or a filtered list;
 * a filtered list matches [com.agastyaone.crmai.ui.billing.InvoiceListScreen]'s existing
 * pattern rather than introducing a new layout style for one screen.
 */
@Composable
fun InsuranceClaimsListScreen(
    clinicId: String,
    onOpenClaim: (String) -> Unit,
    onBack: () -> Unit,
) {
    val claims by ServiceLocator.insuranceClaimRepository
        .observeClaimsForClinic(clinicId)
        .collectAsState(initial = emptyList())

    var statusFilter by remember { mutableStateOf<ClaimStatus?>(null) }
    val filtered = statusFilter?.let { status -> claims.filter { it.status == status.id } } ?: claims

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insurance claims") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All") })
                for (status in ClaimStatus.entries) {
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = { statusFilter = status },
                        label = { Text(status.label) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No insurance claims yet. File one from an invoice.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered) { claim ->
                        InsuranceClaimRow(claim = claim, onClick = { onOpenClaim(claim.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InsuranceClaimRow(claim: InsuranceClaim, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(claim.tpaName, style = MaterialTheme.typography.bodyLarge)
                Text(submittedDateText(claim), style = MaterialTheme.typography.labelSmall)
            }
            Column {
                Text(formatCurrency(claim.claimAmount), style = MaterialTheme.typography.bodyLarge)
                Text(ClaimStatus.fromId(claim.status).label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun submittedDateText(claim: InsuranceClaim): String {
    val timestamp = claim.submittedAt ?: return "Unknown date"
    return timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
