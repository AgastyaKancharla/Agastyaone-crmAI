package com.agastyaone.crmai.ui.imaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import coil.compose.AsyncImage
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.charting.ToothChart
import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import com.agastyaone.crmai.data.imaging.ImagingRecord
import com.agastyaone.crmai.data.imaging.ImagingType
import com.agastyaone.crmai.ui.scheduling.formattedDate
import kotlinx.coroutines.launch

private const val MAX_COMPARE_SELECTION = 2

/**
 * Per-patient imaging, grouped by date (most recent first, matching the
 * capturedAt-descending query) with a toggle to group by tooth instead. Tap up to two
 * thumbnails to enable before/after comparison; delete is owner-only (Phase 3b: an
 * assistant may tag but not delete a clinical image).
 */
@Composable
fun ImageGalleryScreen(
    clinicId: String,
    role: Role,
    patientId: String,
    onAddImage: () -> Unit,
    onCompare: (ImagingRecord, ImagingRecord) -> Unit,
    onBack: () -> Unit,
) {
    val repository = ServiceLocator.imagingRepository
    val scope = rememberCoroutineScope()
    val images by repository.observeImagingForPatient(clinicId, patientId).collectAsState(initial = emptyList())
    val numberingSystem by ServiceLocator.tenantRepository
        .observeToothNumberingSystem(clinicId)
        .collectAsState(initial = ToothNumberingSystem.FDI)

    var groupByTooth by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<ImagingRecord?>(null) }

    val canDelete = role == Role.OWNER

    // images is already ordered capturedAt-descending by the repository query, and
    // groupBy preserves each key's first-seen order - so both groupings stay
    // "most recent first" (date grouping directly; tooth grouping incidentally, which
    // is fine since the spec only orders the date view).
    val groups: List<Pair<String, List<ImagingRecord>>> = if (groupByTooth) {
        images.groupBy { it.toothNumber?.let { tooth -> ToothChart.displayLabel(tooth, numberingSystem) } ?: "Untagged" }
            .toList()
    } else {
        images.groupBy { it.capturedAt?.formattedDate() ?: "Unknown date" }.toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Imaging") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddImage) {
                Icon(Icons.Filled.Add, contentDescription = "Add image")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = !groupByTooth, onClick = { groupByTooth = false }, label = { Text("By date") })
                FilterChip(selected = groupByTooth, onClick = { groupByTooth = true }, label = { Text("By tooth") })
            }

            if (selectedIds.size == MAX_COMPARE_SELECTION) {
                val selected = images.filter { it.id in selectedIds }.sortedBy { it.capturedAt?.seconds ?: 0 }
                if (selected.size == MAX_COMPARE_SELECTION) {
                    TextButton(
                        onClick = { onCompare(selected[0], selected[1]) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Compare selected (before/after)") }
                }
            }

            if (images.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("No images yet. Tap + to add an X-ray or photo.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(groups) { (heading, groupImages) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(heading, style = MaterialTheme.typography.titleMedium)
                            groupImages.forEach { image ->
                                ImagingThumbnailRow(
                                    image = image,
                                    numberingSystem = numberingSystem,
                                    selected = image.id in selectedIds,
                                    canDelete = canDelete,
                                    onToggleSelect = {
                                        selectedIds = when {
                                            image.id in selectedIds -> selectedIds - image.id
                                            selectedIds.size < MAX_COMPARE_SELECTION -> selectedIds + image.id
                                            else -> selectedIds
                                        }
                                    },
                                    onDelete = { pendingDelete = image },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete image?") },
            text = { Text("This removes the image and its tags. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    selectedIds = selectedIds - image.id
                    scope.launch { repository.deleteImagingRecord(clinicId, image.id) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ImagingThumbnailRow(
    image: ImagingRecord,
    numberingSystem: ToothNumberingSystem,
    selected: Boolean,
    canDelete: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (selected) Color(0xFF1565C0) else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(8.dp)),
        onClick = onToggleSelect,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = image.storageUrl,
                contentDescription = "Imaging thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(0.28f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ImagingType.fromId(image.type).label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    image.toothNumber?.let { "Tooth ${ToothChart.displayLabel(it, numberingSystem)}" } ?: "No tooth tagged",
                )
                image.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                if (selected) Text("Selected for comparison", color = Color(0xFF1565C0))
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete image")
                }
            }
        }
    }
}
