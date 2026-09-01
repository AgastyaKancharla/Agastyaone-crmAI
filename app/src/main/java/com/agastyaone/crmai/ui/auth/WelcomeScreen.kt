package com.agastyaone.crmai.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(
    onOwnerSignup: () -> Unit,
    onStaffSignIn: () -> Unit,
    onPlatformAdmin: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "AgastyaOne CRM", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Dental clinic management", style = MaterialTheme.typography.bodyMedium)

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onOwnerSignup, modifier = Modifier.fillMaxWidth()) {
                    Text("Set up my clinic (Owner / Dentist)")
                }
                OutlinedButton(onClick = onStaffSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in")
                }
            }

            TextButton(
                onClick = onPlatformAdmin,
                modifier = Modifier.padding(top = 48.dp).align(Alignment.CenterHorizontally),
            ) {
                Text("AgastyaOne team sign-in", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
