package com.agastyaone.crmai.ui.charting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import com.agastyaone.crmai.data.charting.ToothChart
import com.agastyaone.crmai.data.charting.ToothConditionEntry
import com.agastyaone.crmai.data.charting.ToothConditionType
import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import com.agastyaone.crmai.data.charting.ToothSurface

/**
 * Interactive tooth diagram: an upper and a lower arch row, each in real FDI charting order
 * with a gap at the midline. Editable only when [canEdit] - viewers (e.g. an assistant) see
 * it fully rendered with disabled tap targets, per the Phase 3a spec.
 */
@Composable
fun OdontogramView(
    charting: Charting,
    numberingSystem: ToothNumberingSystem,
    canEdit: Boolean,
    onConditionChange: (toothNumber: String, entry: ToothConditionEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTooth by remember { mutableStateOf<String?>(null) }
    val arches = ToothChart.archesFor(charting.dentitionType)

    Column(modifier = modifier) {
        ToothConditionLegend()
        Spacer(modifier = Modifier.height(12.dp))
        ToothArchRow(arches.upper, arches.midlineIndex, charting, numberingSystem) { tooth ->
            if (canEdit) selectedTooth = tooth
        }
        Spacer(modifier = Modifier.height(20.dp))
        ToothArchRow(arches.lower, arches.midlineIndex, charting, numberingSystem) { tooth ->
            if (canEdit) selectedTooth = tooth
        }
    }

    val toothForDialog = selectedTooth
    if (toothForDialog != null) {
        ToothConditionPickerDialog(
            displayLabel = ToothChart.displayLabel(toothForDialog, numberingSystem),
            entry = charting.toothCondition(toothForDialog),
            onSave = { entry ->
                onConditionChange(toothForDialog, entry)
                selectedTooth = null
            },
            onDismiss = { selectedTooth = null },
        )
    }
}

@Composable
private fun ToothConditionLegend() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ToothConditionType.entries) { condition ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(colorForCondition(condition.id), CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(condition.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ToothArchRow(
    teeth: List<String>,
    midlineIndex: Int,
    charting: Charting,
    numberingSystem: ToothNumberingSystem,
    onTap: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(teeth) { index, tooth ->
            Row {
                if (index == midlineIndex) {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                ToothCell(
                    displayLabel = ToothChart.displayLabel(tooth, numberingSystem),
                    entry = charting.toothCondition(tooth),
                    onTap = { onTap(tooth) },
                )
            }
        }
    }
}

@Composable
private fun ToothCell(displayLabel: String, entry: ToothConditionEntry, onTap: () -> Unit) {
    Column(
        modifier = Modifier.width(52.dp).clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colorForCondition(entry.primaryCondition), RoundedCornerShape(8.dp))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.surfaces.size > 1) {
                Text("${entry.surfaces.size}", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(displayLabel, style = MaterialTheme.typography.labelSmall)
    }
}

private val WHOLE_TOOTH_CONDITIONS = listOf(
    ToothConditionType.MISSING,
    ToothConditionType.IMPLANT,
    ToothConditionType.EXTRACTION_PLANNED,
)

@Composable
private fun ToothConditionPickerDialog(
    displayLabel: String,
    entry: ToothConditionEntry,
    onSave: (ToothConditionEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var surfaces by remember(entry) { mutableStateOf(entry.surfaces) }
    var notes by remember(entry) { mutableStateOf(entry.notes ?: "") }
    var expandedSurfaceId by remember { mutableStateOf<String?>(null) }

    // Missing/implant/extraction-planned describe the whole tooth, not one surface, so they're
    // offered separately from the per-surface conditions rather than mixed into every list.
    val surfaceConditions = ToothConditionType.entries.filterNot { it in WHOLE_TOOTH_CONDITIONS }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tooth $displayLabel") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Whole tooth", style = MaterialTheme.typography.labelLarge)
                ConditionRow(
                    label = "Whole tooth",
                    currentConditionId = surfaces[ToothConditionEntry.WHOLE_TOOTH_SURFACE],
                    options = WHOLE_TOOTH_CONDITIONS,
                    isExpanded = expandedSurfaceId == ToothConditionEntry.WHOLE_TOOTH_SURFACE,
                    onToggle = {
                        expandedSurfaceId = if (expandedSurfaceId == ToothConditionEntry.WHOLE_TOOTH_SURFACE) {
                            null
                        } else {
                            ToothConditionEntry.WHOLE_TOOTH_SURFACE
                        }
                    },
                    onSelect = { conditionId ->
                        surfaces = if (conditionId == null) {
                            surfaces - ToothConditionEntry.WHOLE_TOOTH_SURFACE
                        } else {
                            surfaces + (ToothConditionEntry.WHOLE_TOOTH_SURFACE to conditionId)
                        }
                        expandedSurfaceId = null
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Surfaces", style = MaterialTheme.typography.labelLarge)
                for (surface in ToothSurface.entries) {
                    ConditionRow(
                        label = surface.label,
                        currentConditionId = surfaces[surface.id],
                        options = surfaceConditions,
                        isExpanded = expandedSurfaceId == surface.id,
                        onToggle = { expandedSurfaceId = if (expandedSurfaceId == surface.id) null else surface.id },
                        onSelect = { conditionId ->
                            surfaces = if (conditionId == null) surfaces - surface.id else surfaces + (surface.id to conditionId)
                            expandedSurfaceId = null
                        },
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ToothConditionEntry(surfaces = surfaces, notes = notes.ifBlank { null })) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConditionRow(
    label: String,
    currentConditionId: String?,
    options: List<ToothConditionType>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(colorForCondition(currentConditionId), CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label)
            }
            Text(
                currentConditionId?.let { ToothConditionType.fromId(it)?.label } ?: "Not marked",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (isExpanded) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                TextButton(onClick = { onSelect(null) }) { Text("Clear") }
                for (option in options) {
                    TextButton(onClick = { onSelect(option.id) }) { Text(option.label) }
                }
            }
        }
    }
}
