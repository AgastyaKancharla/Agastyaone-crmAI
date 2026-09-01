package com.agastyaone.crmai.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.agastyaone.crmai.data.auth.SessionState
import kotlinx.coroutines.launch

/**
 * Deliberately separate from [OwnerSignupScreen] / [StaffSignInScreen] in both the nav
 * graph and the code path: platform admin is never a role a clinic signup or invite can
 * grant, so this is the only place that even attempts it, and it double-checks the
 * resulting claim before letting the user in.
 */
@Composable
fun PlatformAdminSignInScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val authRepository = ServiceLocator.authRepository

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("AgastyaOne team sign-in") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Internal access for the AgastyaOne platform team only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())

            Button(
                enabled = !isBusy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isBusy = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            authRepository.signInWithEmail(email, password)
                            val state = authRepository.refreshSession()
                            if (state !is SessionState.PlatformAdmin) {
                                authRepository.signOut()
                                error("This account is not a platform admin.")
                            }
                        }.onFailure { errorMessage = it.message }
                        isBusy = false
                    }
                },
            ) { Text("Sign in") }

            if (isBusy) CircularProgressIndicator()
            errorMessage?.let { Text(it, color = Color.Red) }
        }
    }
}
