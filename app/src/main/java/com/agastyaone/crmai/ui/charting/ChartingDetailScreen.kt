package com.agastyaone.crmai.ui.charting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import kotlinx.coroutines.launch

/**
 * Hosts the odontogram and periodontal-chart tabs for one charting document. Diagnostic
 * (toothConditions) edits are dentist-only; periodontal edits are dentist or assistant -
 * both views read the same document, per firestore.rules' field-level scoping.
 */
@Composable
fun ChartingDetailScreen(
    clinicId: String,
    uid: String,
    role: Role,
    chartingId: String,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.chartingRepository
    val tenantRepository = ServiceLocator.tenantRepository
    val scope = rememberCoroutineScope()

    val charting by repository.observeCharting(clinicId, chartingId).collectAsState(initial = null)
    val numberingSystem by tenantRepository.observeToothNumberingSystem(clinicId)
        .collectAsState(initial = ToothNumberingSystem.FDI)

    var selectedTab by remember { mutableIntStateOf(0) }
    val canEditDiagnosis = role == Role.OWNER
    val canEditPeriodontal = role == Role.OWNER || role == Role.ASSISTANT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charting") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = charting
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Odontogram") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Periodontal") })
            }

            when (selectedTab) {
                0 -> OdontogramView(
                    charting = current,
                    numberingSystem = numberingSystem,
                    canEdit = canEditDiagnosis,
                    onConditionChange = { toothNumber, entry ->
                        scope.launch {
                            repository.updateToothCondition(clinicId, chartingId, uid, toothNumber, entry)
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
                else -> PeriodontalChartView(
                    charting = current,
                    numberingSystem = numberingSystem,
                    canEdit = canEditPeriodontal,
                    onReadingChange = { toothNumber, reading ->
                        scope.launch {
                            repository.updatePeriodontalReading(clinicId, chartingId, uid, toothNumber, reading)
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }
    }
}
