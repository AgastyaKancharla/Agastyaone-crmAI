package com.agastyaone.crmai.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.auth.PhoneVerificationEvent
import kotlinx.coroutines.launch

/**
 * Collects clinic details up-front and only creates the Firebase Auth account + calls
 * `createClinicAndOwner` on submit, so the whole tenant/staff/claims transaction happens
 * in one shot instead of leaving a half-signed-up account with no clinic behind.
 */
@Composable
fun OwnerSignupScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authRepository = ServiceLocator.authRepository
    val functionsRepository = ServiceLocator.cloudFunctionsRepository

    var clinicName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }

    var tabIndex by remember { mutableIntStateOf(0) }
    var phone by remember { mutableStateOf("+91") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val clinicDetailsValid = clinicName.isNotBlank() && address.isNotBlank() &&
        city.isNotBlank() && state.isNotBlank() && ownerName.isNotBlank()

    suspend fun completeSignup() {
        functionsRepository.createClinicAndOwner(
            clinicName = clinicName,
            address = address,
            city = city,
            state = state,
            gstin = gstin.ifBlank { null },
            ownerName = ownerName,
            ownerPhone = if (tabIndex == 0) phone else null,
            ownerEmail = if (tabIndex == 1) email else null,
        )
        authRepository.refreshSession()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Set up your clinic") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Clinic details", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(clinicName, { clinicName = it }, label = { Text("Clinic name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state, { state = it }, label = { Text("State") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(gstin, { gstin = it }, label = { Text("GSTIN (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ownerName, { ownerName = it }, label = { Text("Your name (Owner/Dentist)") }, modifier = Modifier.fillMaxWidth())

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Create your login", style = MaterialTheme.typography.titleMedium)

            SecondaryTabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Phone") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Email") })
            }

            if (tabIndex == 0) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = verificationId == null,
                )
                if (verificationId == null) {
                    Button(
                        enabled = !isBusy && clinicDetailsValid,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@Button
                            isBusy = true
                            errorMessage = null
                            scope.launch {
                                authRepository.startPhoneVerification(phone, activity).collect { event ->
                                    when (event) {
                                        is PhoneVerificationEvent.CodeSent -> {
                                            verificationId = event.verificationId
                                            isBusy = false
                                        }
                                        is PhoneVerificationEvent.AutoVerified -> {
                                            runCatching {
                                                authRepository.signInWithPhoneCredential(event.credential)
                                                completeSignup()
                                            }.onFailure { errorMessage = it.message }
                                            isBusy = false
                                        }
                                        is PhoneVerificationEvent.Failed -> {
                                            errorMessage = event.message
                                            isBusy = false
                                        }
                                    }
                                }
                            }
                        },
                    ) { Text("Send OTP") }
                } else {
                    OutlinedTextField(otp, { otp = it }, label = { Text("6-digit OTP") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        enabled = !isBusy && otp.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            isBusy = true
                            errorMessage = null
                            scope.launch {
                                runCatching {
                                    authRepository.signInWithOtp(verificationId!!, otp)
                                    completeSignup()
                                }.onFailure { errorMessage = it.message }
                                isBusy = false
                            }
                        },
                    ) { Text("Verify & create clinic") }
                }
            } else {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = !isBusy && clinicDetailsValid && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        isBusy = true
                        errorMessage = null
                        scope.launch {
                            runCatching {
                                authRepository.signUpWithEmail(email, password)
                                completeSignup()
                            }.onFailure { errorMessage = it.message }
                            isBusy = false
                        }
                    },
                ) { Text("Create clinic account") }
            }

            if (isBusy) CircularProgressIndicator()
            errorMessage?.let { Text(it, color = Color.Red) }
        }
    }
}
