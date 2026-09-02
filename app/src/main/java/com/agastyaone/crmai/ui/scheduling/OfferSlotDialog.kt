package com.agastyaone.crmai.ui.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.agastyaone.crmai.data.scheduling.WaitlistEntry
import com.agastyaone.crmai.data.scheduling.WaitlistStatus
import kotlinx.coroutines.launch

/**
 * Cancel-triggered prompt: "offer this slot to the next waitlisted patient?" This phase
 * only marks the entry `offered` and surfaces the patient's phone for staff to call/
 * message manually - no automated messaging until Phase 4's WhatsApp automation.
 */
@Composable
fun OfferSlotDialog(clinicId: String, onDismiss: () -> Unit) {
    val scheduleRepository = ServiceLocator.scheduleRepository
    val waitlist by scheduleRepository.observeWaitlist(clinicId).collectAsState(initial = emptyList())
    val waitingEntries = waitlist.filter { it.status == WaitlistStatus.WAITING.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offer this slot?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (waitingEntries.isEmpty()) {
                    Text("No one is waiting right now.")
                } else {
                    for (entry in waitingEntries) {
                        OfferSlotRow(clinicId = clinicId, entry = entry)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun OfferSlotRow(clinicId: String, entry: WaitlistEntry) {
    val scheduleRepository = ServiceLocator.scheduleRepository
    val patientRepository = ServiceLocator.patientRepository
    val scope = rememberCoroutineScope()
    var offered by remember(entry.id) { mutableStateOf(entry.status == WaitlistStatus.OFFERED.id) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.patientName)
            if (offered) {
                val patient by patientRepository.observePatient(clinicId, entry.patientId).collectAsState(initial = null)
                Text("Contact: ${patient?.phone ?: "Loading..."}")
            } else {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            scheduleRepository.updateWaitlistStatus(clinicId, entry.id, WaitlistStatus.OFFERED)
                        }.onSuccess { offered = true }
                    }
                }) { Text("Offer this slot") }
            }
        }
    }
}
