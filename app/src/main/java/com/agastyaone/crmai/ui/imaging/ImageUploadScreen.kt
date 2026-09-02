package com.agastyaone.crmai.ui.imaging

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.imaging.ImagingType
import com.agastyaone.crmai.data.charting.ToothChart
import com.agastyaone.crmai.data.charting.ToothNumberingSystem
import com.agastyaone.crmai.ui.charting.ToothNumberPickerDialog
import com.agastyaone.crmai.ui.scheduling.DATE_FORMAT
import com.agastyaone.crmai.ui.scheduling.startOfDayTimestamp
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Capture from camera or pick from gallery, then tag: tooth (reusing Phase 3a's
 * tooth-picker dialog for consistency), type, and an optional note. Reachable by
 * owner/assistant only - nav-layer gating, same as the rest of the clinical module.
 */
@Composable
fun ImageUploadScreen(
    clinicId: String,
    uid: String,
    patientId: String,
    onUploaded: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = ServiceLocator.imagingRepository
    val uploader = ServiceLocator.imageUploader
    val scope = rememberCoroutineScope()

    val numberingSystem by ServiceLocator.tenantRepository
        .observeToothNumberingSystem(clinicId)
        .collectAsState(initial = ToothNumberingSystem.FDI)

    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var toothNumber by remember { mutableStateOf<String?>(null) }
    var showToothPicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(ImagingType.RVG) }
    var notes by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().format(DATE_FORMAT)) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) selectedUri = pendingCaptureUri
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selectedUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add image") },
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
            val uri = selectedUri
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Selected image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(240.dp).background(Color.Black),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(240.dp).background(Color(0xFFECEFF1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No image selected")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val captureUri = createCaptureUri(context)
                    pendingCaptureUri = captureUri
                    cameraLauncher.launch(captureUri)
                }) { Text("Camera") }
                OutlinedButton(onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
            }

            OutlinedButton(onClick = { showToothPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    toothNumber?.let { "Tooth: ${ToothChart.displayLabel(it, numberingSystem)}" }
                        ?: "Tooth (optional) - tap to select",
                )
            }

            OutlinedButton(onClick = { showTypePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Type: ${type.label}")
            }

            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            errorMessage?.let { Text(it, color = Color.Red) }

            Button(
                enabled = selectedUri != null && !isSaving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val uriToUpload = selectedUri ?: return@Button
                    val parsedDate = runCatching { LocalDate.parse(dateText, DATE_FORMAT) }.getOrNull()
                    if (parsedDate == null) {
                        errorMessage = "Enter a valid date"
                        return@Button
                    }
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            val fileName = "${System.currentTimeMillis()}.jpg"
                            val downloadUrl = uploader.upload(clinicId, patientId, fileName, uriToUpload)
                            repository.createImagingRecord(
                                clinicId = clinicId,
                                uploadedByUid = uid,
                                patientId = patientId,
                                toothNumber = toothNumber,
                                type = type,
                                storageUrl = downloadUrl,
                                capturedAt = parsedDate.startOfDayTimestamp(),
                                notes = notes.ifBlank { null },
                            )
                        }.onSuccess {
                            isSaving = false
                            onUploaded()
                        }.onFailure {
                            isSaving = false
                            errorMessage = it.message
                        }
                    }
                },
            ) { Text("Upload") }

            if (isSaving) CircularProgressIndicator()
        }
    }

    if (showToothPicker) {
        ToothNumberPickerDialog(
            numberingSystem = numberingSystem,
            onSelect = {
                toothNumber = it
                showToothPicker = false
            },
            onDismiss = { showToothPicker = false },
        )
    }

    if (showTypePicker) {
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = { Text("Image type") },
            text = {
                Column {
                    for (option in ImagingType.entries) {
                        TextButton(
                            onClick = {
                                type = option
                                showTypePicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option.label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTypePicker = false }) { Text("Cancel") }
            },
        )
    }
}
