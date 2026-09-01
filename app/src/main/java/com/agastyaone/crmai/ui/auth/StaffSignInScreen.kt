package com.agastyaone.crmai.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agastyaone.crmai.core.ServiceLocator
import com.agastyaone.crmai.data.auth.PhoneVerificationEvent
import kotlinx.coroutines.launch

@Composable
fun StaffSignInScreen(onBack: () -> Unit) {
    var tabIndex by remember { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Sign in") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SecondaryTabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Phone") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Email") })
            }
            when (tabIndex) {
                0 -> PhoneSignInTab()
                else -> EmailSignInTab()
            }
        }
    }
}

@Composable
private fun PhoneSignInTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepository = ServiceLocator.authRepository

    var phone by remember { mutableStateOf("+91") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
            enabled = verificationId == null,
        )

        if (verificationId == null) {
            Button(
                enabled = !isBusy,
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
                                    authRepository.signInWithPhoneCredential(event.credential)
                                    authRepository.refreshSession()
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send OTP") }
        } else {
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it },
                label = { Text("6-digit OTP") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !isBusy && otp.isNotBlank(),
                onClick = {
                    isBusy = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            authRepository.signInWithOtp(verificationId!!, otp)
                            authRepository.refreshSession()
                        }.onFailure { errorMessage = it.message }
                        isBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verify & sign in") }
        }

        if (isBusy) CircularProgressIndicator()
        errorMessage?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red) }
    }
}

@Composable
private fun EmailSignInTab() {
    val scope = rememberCoroutineScope()
    val authRepository = ServiceLocator.authRepository

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !isBusy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                isBusy = true
                errorMessage = null
                scope.launch {
                    runCatching {
                        authRepository.signInWithEmail(email, password)
                        authRepository.refreshSession()
                    }.onFailure { errorMessage = it.message }
                    isBusy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in") }

        if (isBusy) CircularProgressIndicator()
        errorMessage?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red) }
    }
}
