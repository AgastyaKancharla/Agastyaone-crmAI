package com.agastyaone.crmai.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.patients.ConsentType
import com.agastyaone.crmai.data.patients.IntakeFormType
import com.agastyaone.crmai.data.patients.PatientDemographics
import com.agastyaone.crmai.ui.signature.SignaturePad
import com.agastyaone.crmai.ui.signature.rememberSignaturePadState
import kotlinx.coroutines.launch

private enum class IntakeStep {
    DEMOGRAPHICS, MEDICAL_HISTORY, TREATMENT_CONSENT, TPA_CONSENT, WHATSAPP_CONSENT, REVIEW_CONSENT, DONE
}

/**
 * Tablet-optimized, full-screen, multi-step digital intake. Each consent type gets its
 * own distinct screen with its own explicit language - deliberately not a single
 * blanket "I agree" checkbox, since that would defeat the point of DPDP consent capture.
 *
 * Medical history here is the patient/guardian's own self-reported account, recorded as
 * a signed intake-form snapshot (owner/receptionist can capture it) - it does not write
 * to the patient record's own clinical fields, which stay assistant/owner-only per the
 * role matrix even when a receptionist is the one running this flow.
 */
@Composable
fun IntakeFlowScreen(
    clinicId: String,
    uid: String,
    patientId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    var step by remember { mutableStateOf(IntakeStep.DEMOGRAPHICS) }
    val stepIndex = IntakeStep.entries.indexOf(step)
    val progressFraction = (stepIndex + 1f) / IntakeStep.entries.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient intake") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel intake")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())

            when (step) {
                IntakeStep.DEMOGRAPHICS -> DemographicsStep(
                    clinicId = clinicId,
                    uid = uid,
                    patientId = patientId,
                    onNext = { step = IntakeStep.MEDICAL_HISTORY },
                )
                IntakeStep.MEDICAL_HISTORY -> MedicalHistoryStep(
                    clinicId = clinicId,
                    uid = uid,
                    patientId = patientId,
                    onBack = { step = IntakeStep.DEMOGRAPHICS },
                    onNext = { step = IntakeStep.TREATMENT_CONSENT },
                )
                IntakeStep.TREATMENT_CONSENT -> SignatureConsentStep(
                    clinicId = clinicId,
                    uid = uid,
                    patientId = patientId,
                    consentType = ConsentType.TREATMENT_RECORDS,
                    title = "Consent to treatment records",
                    explanation = "I consent to AgastyaOne recording and retaining my dental " +
                        "treatment records, including clinical notes, X-rays, and treatment " +
                        "plans, for the purpose of providing my dental care.",
                    intakeFormType = IntakeFormType.CONSENT_TREATMENT,
                    onBack = { step = IntakeStep.MEDICAL_HISTORY },
                    onNext = { step = IntakeStep.TPA_CONSENT },
                )
                IntakeStep.TPA_CONSENT -> SignatureConsentStep(
                    clinicId = clinicId,
                    uid = uid,
                    patientId = patientId,
                    consentType = ConsentType.DATA_SHARING_TPA,
                    title = "Consent to share data with insurance/TPA",
                    explanation = "I consent to AgastyaOne sharing the treatment records " +
                        "necessary to process claims with my insurance provider or Third " +
                        "Party Administrator (TPA), only for claim-processing purposes.",
                    intakeFormType = null,
                    onBack = { step = IntakeStep.TREATMENT_CONSENT },
                    onNext = { step = IntakeStep.WHATSAPP_CONSENT },
                )
                IntakeStep.WHATSAPP_CONSENT -> ToggleConsentStep(
                    clinicId = clinicId,
                    uid = uid,
                    patientId = patientId,
                    consentType = ConsentType.WHATSAPP_MARKETING,
                    title = "WhatsApp updates & offers",
                    explanation = "With this on, AgastyaOne may message you on WhatsApp about " +
                        "appointment reminders, offers, and clinic updates. You can withdraw " +
                        "this any time - it won't affect your treatment.",
                    onBack = { step = IntakeStep.TPA_CONSENT },
                    onNext = { step = IntakeStep.REVIEW_CONSENT },
                )
                IntakeStep.REVIEW_CONSENT -> ToggleConsentStep(
                    clinicId = clinicId,
                    uid = uid,
                    patientId = patientId,
                    consentType = ConsentType.REVIEW_REQUESTS,
                    title = "Review requests",
                    explanation = "With this on, AgastyaOne may ask you for a review of your " +
                        "visit after an appointment. You can withdraw this any time - it " +
                        "won't affect your treatment.",
                    onBack = { step = IntakeStep.WHATSAPP_CONSENT },
                    onNext = { step = IntakeStep.DONE },
                )
                IntakeStep.DONE -> DoneStep(onFinish = onComplete)
            }
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun StepNavRow(
    onBack: (() -> Unit)?,
    nextLabel: String,
    nextEnabled: Boolean,
    isBusy: Boolean,
    onNext: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
        }
        Button(onClick = onNext, enabled = nextEnabled && !isBusy, modifier = Modifier.weight(1f)) {
            Text(nextLabel)
        }
    }
    if (isBusy) CircularProgressIndicator()
}

@Composable
private fun DemographicsStep(
    clinicId: String,
    uid: String,
    patientId: String,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository
    val patient by repository.observePatient(clinicId, patientId).collectAsState(initial = null)

    var name by remember(patient) { mutableStateOf(patient?.name ?: "") }
    var phone by remember(patient) { mutableStateOf(patient?.phone ?: "") }
    var address by remember(patient) { mutableStateOf(patient?.address ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    StepScaffold(title = "Confirm your details") {
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        errorMessage?.let { Text(it, color = Color.Red) }
        StepNavRow(onBack = null, nextLabel = "Next: Medical history", nextEnabled = name.isNotBlank(), isBusy = isSaving) {
            isSaving = true
            errorMessage = null
            scope.launch {
                runCatching {
                    val current = patient
                    repository.updateDemographics(
                        clinicId,
                        patientId,
                        uid,
                        PatientDemographics(
                            name = name.trim(),
                            dob = current?.dob,
                            gender = current?.gender,
                            phone = phone.ifBlank { null },
                            email = current?.email,
                            address = address.ifBlank { null },
                            emergencyContactName = current?.emergencyContactName,
                            emergencyContactPhone = current?.emergencyContactPhone,
                            bloodGroup = current?.bloodGroup,
                        ),
                    )
                }.onSuccess {
                    isSaving = false
                    onNext()
                }.onFailure {
                    isSaving = false
                    errorMessage = it.message
                }
            }
        }
    }
}

@Composable
private fun MedicalHistoryStep(
    clinicId: String,
    uid: String,
    patientId: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository

    var signedByName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    StepScaffold(title = "Medical history") {
        Text(
            "Please tell us about any conditions, medications, or allergies your dentist " +
                "should know about. Your dentist/assistant will review this with you.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            signedByName,
            { signedByName = it },
            label = { Text("Filled in by (name)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            notes,
            { notes = it },
            label = { Text("Medical history") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
        )
        errorMessage?.let { Text(it, color = Color.Red) }
        StepNavRow(
            onBack = onBack,
            nextLabel = "Next: Treatment consent",
            nextEnabled = signedByName.isNotBlank(),
            isBusy = isSaving,
        ) {
            isSaving = true
            errorMessage = null
            scope.launch {
                runCatching {
                    repository.recordIntakeForm(
                        clinicId = clinicId,
                        patientId = patientId,
                        recordedByUid = uid,
                        formType = IntakeFormType.MEDICAL_HISTORY,
                        signedByName = signedByName.trim(),
                        signatureImageUrl = null,
                        notes = notes.ifBlank { null },
                    )
                }.onSuccess {
                    isSaving = false
                    onNext()
                }.onFailure {
                    isSaving = false
                    errorMessage = it.message
                }
            }
        }
    }
}

@Composable
private fun SignatureConsentStep(
    clinicId: String,
    uid: String,
    patientId: String,
    consentType: ConsentType,
    title: String,
    explanation: String,
    intakeFormType: IntakeFormType?,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository
    val uploader = ServiceLocator.signatureUploader
    val signatureState = rememberSignaturePadState()

    var signedByName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    StepScaffold(title = title) {
        Text(explanation, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            signedByName,
            { signedByName = it },
            label = { Text("Signed by (name)") },
            modifier = Modifier.fillMaxWidth(),
        )
        SignaturePad(state = signatureState, modifier = Modifier.fillMaxWidth().height(200.dp))
        OutlinedButton(onClick = { signatureState.clear() }) { Text("Clear signature") }
        errorMessage?.let { Text(it, color = Color.Red) }
        StepNavRow(
            onBack = onBack,
            nextLabel = "I consent - continue",
            nextEnabled = signedByName.isNotBlank() && !signatureState.isEmpty,
            isBusy = isSaving,
        ) {
            isSaving = true
            errorMessage = null
            scope.launch {
                runCatching {
                    val bitmap = signatureState.toBitmap()
                        ?: error("Please sign before continuing.")
                    val fileName = "${consentType.id}_${System.currentTimeMillis()}.png"
                    val signatureUrl = uploader.upload(clinicId, patientId, fileName, bitmap)
                    repository.recordConsent(
                        clinicId = clinicId,
                        patientId = patientId,
                        recordedByUid = uid,
                        consentType = consentType,
                        granted = true,
                        signatureUrl = signatureUrl,
                    )
                    if (intakeFormType != null) {
                        repository.recordIntakeForm(
                            clinicId = clinicId,
                            patientId = patientId,
                            recordedByUid = uid,
                            formType = intakeFormType,
                            signedByName = signedByName.trim(),
                            signatureImageUrl = signatureUrl,
                        )
                    }
                }.onSuccess {
                    isSaving = false
                    onNext()
                }.onFailure {
                    isSaving = false
                    errorMessage = it.message
                }
            }
        }
    }
}

@Composable
private fun ToggleConsentStep(
    clinicId: String,
    uid: String,
    patientId: String,
    consentType: ConsentType,
    title: String,
    explanation: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository

    var granted by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    StepScaffold(title = title) {
        Text(explanation, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = granted, onCheckedChange = { granted = it })
            Text(if (granted) "Yes, I consent" else "No, not right now", modifier = Modifier.padding(start = 8.dp))
        }
        errorMessage?.let { Text(it, color = Color.Red) }
        StepNavRow(onBack = onBack, nextLabel = "Continue", nextEnabled = true, isBusy = isSaving) {
            isSaving = true
            errorMessage = null
            scope.launch {
                runCatching {
                    repository.recordConsent(
                        clinicId = clinicId,
                        patientId = patientId,
                        recordedByUid = uid,
                        consentType = consentType,
                        granted = granted,
                        signatureUrl = null,
                    )
                }.onSuccess {
                    isSaving = false
                    onNext()
                }.onFailure {
                    isSaving = false
                    errorMessage = it.message
                }
            }
        }
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Intake complete", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Thank you. Your details, medical history, and consents have been recorded.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
