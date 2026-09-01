package com.agastyaone.crmai.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.patients.Patient
import com.agastyaone.crmai.data.patients.PatientDemographics
import kotlinx.coroutines.launch

/**
 * Owner/receptionist-only screen for the non-clinical fields. Reachability (owner and
 * receptionist can open this; assistant cannot) is enforced by which dashboard tiles/
 * nav routes even lead here - the actual write is enforced server-side either way by
 * firestore.rules' demographicFields()/clinicalFields() split.
 */
@Composable
fun PatientFormScreen(
    clinicId: String,
    uid: String,
    existingPatient: Patient?,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = ServiceLocator.patientRepository
    val isEditing = existingPatient != null

    var name by remember { mutableStateOf(existingPatient?.name ?: "") }
    var dob by remember { mutableStateOf(existingPatient?.dob ?: "") }
    var gender by remember { mutableStateOf(existingPatient?.gender ?: "") }
    var phone by remember { mutableStateOf(existingPatient?.phone ?: "") }
    var email by remember { mutableStateOf(existingPatient?.email ?: "") }
    var address by remember { mutableStateOf(existingPatient?.address ?: "") }
    var emergencyContactName by remember { mutableStateOf(existingPatient?.emergencyContactName ?: "") }
    var emergencyContactPhone by remember { mutableStateOf(existingPatient?.emergencyContactPhone ?: "") }
    var bloodGroup by remember { mutableStateOf(existingPatient?.bloodGroup ?: "") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit patient" else "Add patient") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dob, { dob = it }, label = { Text("Date of birth (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(gender, { gender = it }, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                emergencyContactName,
                { emergencyContactName = it },
                label = { Text("Emergency contact name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                emergencyContactPhone,
                { emergencyContactPhone = it },
                label = { Text("Emergency contact phone") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(bloodGroup, { bloodGroup = it }, label = { Text("Blood group (optional)") }, modifier = Modifier.fillMaxWidth())

            Button(
                enabled = !isSaving && name.isNotBlank() && phone.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isSaving = true
                    errorMessage = null
                    val demographics = PatientDemographics(
                        name = name.trim(),
                        dob = dob.ifBlank { null },
                        gender = gender.ifBlank { null },
                        phone = phone.trim(),
                        email = email.ifBlank { null },
                        address = address.ifBlank { null },
                        emergencyContactName = emergencyContactName.ifBlank { null },
                        emergencyContactPhone = emergencyContactPhone.ifBlank { null },
                        bloodGroup = bloodGroup.ifBlank { null },
                    )
                    scope.launch {
                        runCatching {
                            if (isEditing) {
                                repository.updateDemographics(clinicId, existingPatient!!.id, uid, demographics)
                                existingPatient.id
                            } else {
                                repository.createPatient(clinicId, uid, demographics)
                            }
                        }.onSuccess { patientId ->
                            isSaving = false
                            onSaved(patientId)
                        }.onFailure {
                            isSaving = false
                            errorMessage = it.message
                        }
                    }
                },
            ) { Text(if (isEditing) "Save changes" else "Create patient") }

            if (isSaving) CircularProgressIndicator()
            errorMessage?.let { Text(it, color = Color.Red) }
        }
    }
}
