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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.data.charting.DentitionType
import com.agastyaone.crmai.data.charting.ToothChart
import com.agastyaone.crmai.data.charting.ToothNumberingSystem

/**
 * Same upper/lower arch layout as [OdontogramView] (via [ToothChart.archesFor]), reused here
 * as a plain tooth-number selector for the treatment plan builder - consistent numbering and
 * layout wherever staff pick a tooth, per the Phase 3a spec.
 */
@Composable
fun ToothNumberPickerDialog(
    dentitionType: String = DentitionType.ADULT.id,
    numberingSystem: ToothNumberingSystem,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val arches = ToothChart.archesFor(dentitionType)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a tooth") },
        text = {
            Column {
                PickerArchRow(arches.upper, arches.midlineIndex, numberingSystem, onSelect)
                Spacer(modifier = Modifier.height(12.dp))
                PickerArchRow(arches.lower, arches.midlineIndex, numberingSystem, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(null) }) { Text("Not tooth-specific") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PickerArchRow(
    teeth: List<String>,
    midlineIndex: Int,
    numberingSystem: ToothNumberingSystem,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(teeth) { index, tooth ->
            Row {
                if (index == midlineIndex) {
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(
                    modifier = Modifier.width(40.dp).clickable { onSelect(tooth) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFECEFF1), RoundedCornerShape(6.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(6.dp)),
                    )
                    Text(ToothChart.displayLabel(tooth, numberingSystem), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
