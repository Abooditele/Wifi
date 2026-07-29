package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileSettingsDialog(
    currentName: String,
    currentStatus: String,
    myDeviceId: String,
    isDarkTheme: Boolean,
    fontSizeScale: Float,
    appLockPin: String?,
    onToggleDarkTheme: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onSetAppLockPin: (String?) -> Unit,
    onBackupData: () -> Unit,
    onSaveProfile: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var statusMessage by remember { mutableStateOf(currentStatus) }
    var pinInput by remember { mutableStateOf(appLockPin ?: "") }
    var lockEnabled by remember { mutableStateOf(appLockPin != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("LAN Profile & Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Device Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = statusMessage,
                    onValueChange = { statusMessage = it },
                    label = { Text("Status Message") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Font size slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatSize, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Font size", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${(fontSizeScale * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = fontSizeScale,
                    onValueChange = { onFontSizeChange(it) },
                    valueRange = 0.8f..1.6f,
                    steps = 7
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dark theme switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dark Theme", fontSize = 14.sp)
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = { onToggleDarkTheme() }, modifier = Modifier.testTag("dark_mode_switch"))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App lock
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (lockEnabled) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, tint = if (lockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("App Lock (PIN)", fontSize = 14.sp)
                            }
                            Switch(
                                checked = lockEnabled,
                                onCheckedChange = { enabled ->
                                    lockEnabled = enabled
                                    if (!enabled) {
                                        pinInput = ""
                                        onSetAppLockPin(null)
                                    }
                                }
                            )
                        }
                        if (lockEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(6) },
                                label = { Text("4-6 digit PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onSetAppLockPin(if (pinInput.length >= 4) pinInput else null) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save PIN") }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Network info + backup
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ID: $myDeviceId", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.height(18.dp), tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AES-GCM 256-Bit Encrypted Sockets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onBackupData, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup chats & media")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveProfile(name, statusMessage)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_profile_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
