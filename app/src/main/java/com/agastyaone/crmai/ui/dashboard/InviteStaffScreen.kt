package com.agastyaone.crmai.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
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
import com.agastyaone.crmai.core.Role
import com.agastyaone.crmai.core.ServiceLocator
import kotlinx.coroutines.launch

private val invitableRoles = listOf(Role.RECEPTIONIST, Role.ASSISTANT, Role.LAB_COORDINATOR)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteStaffScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val functionsRepository = ServiceLocator.cloudFunctionsRepository

    var role by remember { mutableStateOf(invitableRoles.first()) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite staff") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = roleMenuExpanded,
                onExpandedChange = { roleMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = roleLabel(role),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                    invitableRoles.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(roleLabel(candidate)) },
                            onClick = { role = candidate; roleMenuExpanded = false },
                        )
                    }
                }
            }

            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth())

            Button(
                enabled = !isBusy && name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isBusy = true
                    errorMessage = null
                    successMessage = null
                    scope.launch {
                        runCatching {
                            functionsRepository.inviteStaff(
                                role = role.claimValue,
                                name = name,
                                phone = phone.ifBlank { null },
                                email = email.ifBlank { null },
                            )
                        }.onSuccess {
                            successMessage = "Invite sent to $name."
                            name = ""; phone = ""; email = ""
                        }.onFailure { errorMessage = it.message }
                        isBusy = false
                    }
                },
            ) { Text("Send invite") }

            if (isBusy) CircularProgressIndicator()
            errorMessage?.let { Text(it, color = Color.Red) }
            successMessage?.let { Text(it, color = Color(0xFF0F6E5E)) }
        }
    }
}

private fun roleLabel(role: Role): String = when (role) {
    Role.OWNER -> "Owner"
    Role.RECEPTIONIST -> "Receptionist"
    Role.ASSISTANT -> "Assistant / Hygienist"
    Role.LAB_COORDINATOR -> "Lab Coordinator"
}
