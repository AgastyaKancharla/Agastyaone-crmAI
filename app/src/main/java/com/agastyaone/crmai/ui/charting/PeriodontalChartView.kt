package com.agastyaone.crmai.ui.charting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.data.charting.Charting
import com.agastyaone.crmai.data.charting.PeriodontalReading
import com.agastyaone.crmai.data.charting.ToothChart
import com.agastyaone.crmai.data.charting.ToothNumberingSystem

/**
 * A 6-point-per-tooth entry grid. Every tooth in the dentition is listed (not just ones
 * already read) so a gap in coverage is visible; a mini bar chart per tooth surfaces the
 * pocket-depth pattern at a glance instead of forcing a scan of raw numbers.
 */
@Composable
fun PeriodontalChartView(
    charting: Charting,
    numberingSystem: ToothNumberingSystem,
    canEdit: Boolean,
    onReadingChange: (toothNumber: String, reading: PeriodontalReading) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTooth by remember { mutableStateOf<String?>(null) }
    val teeth = ToothChart.teethFor(charting.dentitionType)

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(teeth) { tooth ->
            PeriodontalToothRow(
                displayLabel = ToothChart.displayLabel(tooth, numberingSystem),
                reading = charting.periodontalReading(tooth),
                onTap = { if (canEdit) selectedTooth = tooth },
            )
        }
    }

    val toothForDialog = selectedTooth
    if (toothForDialog != null) {
        PeriodontalReadingDialog(
            displayLabel = ToothChart.displayLabel(toothForDialog, numberingSystem),
            reading = charting.periodontalReading(toothForDialog),
            onSave = { reading ->
                onReadingChange(toothForDialog, reading)
                selectedTooth = null
            },
            onDismiss = { selectedTooth = null },
        )
    }
}

@Composable
private fun PeriodontalToothRow(displayLabel: String, reading: PeriodontalReading, onTap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onTap)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(displayLabel, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                for (index in 0 until PeriodontalReading.POINT_COUNT) {
                    PocketDepthBar(depth = reading.pocketDepths[index], bleeding = reading.bleeding[index])
                }
            }

            Text(
                if (reading.isRecorded) "Mobility ${reading.mobilityGrade}" else "Not recorded",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PocketDepthBar(depth: Int, bleeding: Boolean) {
    val color = when {
        depth >= 6 -> Color(0xFFE57373)
        depth >= 4 -> Color(0xFFFFB74D)
        else -> Color(0xFF81C784)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height((depth.coerceIn(0, 10) * 3 + 2).dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (bleeding) Color(0xFFD32F2F) else Color.Transparent, CircleShape),
        )
    }
}

@Composable
private fun PeriodontalReadingDialog(
    displayLabel: String,
    reading: PeriodontalReading,
    onSave: (PeriodontalReading) -> Unit,
    onDismiss: () -> Unit,
) {
    var pocketDepthText by remember(reading) { mutableStateOf(reading.pocketDepths.map { it.toString() }) }
    var bleeding by remember(reading) { mutableStateOf(reading.bleeding) }
    var mobilityGrade by remember(reading) { mutableStateOf(reading.mobilityGrade) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tooth $displayLabel - periodontal reading") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (index in 0 until PeriodontalReading.POINT_COUNT) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(PeriodontalReading.POINT_LABELS[index], modifier = Modifier.width(32.dp))
                        OutlinedTextField(
                            value = pocketDepthText[index],
                            onValueChange = { newValue ->
                                pocketDepthText = pocketDepthText.toMutableList().also { it[index] = newValue }
                            },
                            label = { Text("mm") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                        )
                        Checkbox(
                            checked = bleeding[index],
                            onCheckedChange = { checked ->
                                bleeding = bleeding.toMutableList().also { it[index] = checked }
                            },
                        )
                        Text("Bleeding", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text("Mobility grade", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (grade in 0..3) {
                        TextButton(onClick = { mobilityGrade = grade }) {
                            Text(if (grade == mobilityGrade) "[$grade]" else "$grade")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val depths = pocketDepthText.map { it.toIntOrNull()?.coerceIn(0, 15) ?: 0 }
                onSave(PeriodontalReading(pocketDepths = depths, bleeding = bleeding, mobilityGrade = mobilityGrade))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
