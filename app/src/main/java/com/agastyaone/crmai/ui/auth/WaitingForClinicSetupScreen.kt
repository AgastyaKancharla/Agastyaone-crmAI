package com.agastyaone.crmai.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch

/**
 * Shown for a signed-in user whose ID token has no `role`/`clinicId` claim yet.
 * That's either someone who was invited but hasn't accepted, or a stray account
 * (e.g. someone who signed up without going through owner-signup or an invite).
 */
@Composable
fun WaitingForClinicSetupScreen(onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val functionsRepository = ServiceLocator.cloudFunctionsRepository
    val authRepository = ServiceLocator.authRepository

    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Waiting for clinic setup", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your account isn't linked to a clinic yet. If a clinic owner invited " +
                    "you, tap below to activate that invite. Otherwise, ask the clinic " +
                    "owner to invite the phone number or email you signed in with.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Button(
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isBusy = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            functionsRepository.acceptInvite()
                            authRepository.refreshSession()
                        }.onFailure { errorMessage = it.message }
                        isBusy = false
                    }
                },
            ) { Text("I have a staff invite") }

            if (isBusy) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            errorMessage?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 16.dp)) }

            TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 24.dp)) {
                Text("Sign out")
            }
        }
    }
}
