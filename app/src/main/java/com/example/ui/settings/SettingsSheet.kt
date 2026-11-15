package com.example.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.DEFAULT_CATEGORIES
import com.example.data.model.UserSettings
import com.example.notification.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    userEmail: String,
    settings: UserSettings,
    isDriveConnected: Boolean,
    onUpdateSettings: (UserSettings) -> Unit,
    onConnectDrive: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    var newCategory by remember { mutableStateOf("") }
    var soundExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportBackup(uri)
    }

    val currentSoundLabel = NotificationHelper.AVAILABLE_SOUNDS
        .find { it.key == settings.defaultSoundKey }?.label ?: "Harmonic Chime"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "P Task v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close settings")
                }
            }

            if (userEmail.isNotBlank()) {
                Text(
                    text = "Signed in as $userEmail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Theme", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.useDarkTheme,
                    onCheckedChange = { onUpdateSettings(settings.copy(useDarkTheme = it)) },
                    modifier = Modifier.testTag("dark_theme_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Allow Biometric List Lock", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.biometricEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(biometricEnabled = it)) },
                    modifier = Modifier.testTag("biometric_toggle_switch")
                )
            }

            ExposedDropdownMenuBox(
                expanded = soundExpanded,
                onExpandedChange = { soundExpanded = !soundExpanded }
            ) {
                OutlinedTextField(
                    value = currentSoundLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Default Reminder Sound") },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { NotificationHelper.playSoundPreview(settings.defaultSoundKey) }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Preview sound")
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = soundExpanded)
                        }
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = soundExpanded,
                    onDismissRequest = { soundExpanded = false }
                ) {
                    NotificationHelper.AVAILABLE_SOUNDS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.label} — ${option.description}") },
                            onClick = {
                                onUpdateSettings(settings.copy(defaultSoundKey = option.key))
                                NotificationHelper.playSoundPreview(option.key)
                                soundExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            Text("Custom Categories", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            val cats = settings.customCategories.ifEmpty { DEFAULT_CATEGORIES }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { cat ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(cat) },
                        trailingIcon = if (cats.size > 1) {
                            {
                                IconButton(
                                    onClick = {
                                        onUpdateSettings(
                                            settings.copy(customCategories = cats.filterNot { it == cat })
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove $cat")
                                }
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text("Add category") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val trimmed = newCategory.trim()
                        if (trimmed.isNotEmpty() && !cats.contains(trimmed)) {
                            onUpdateSettings(settings.copy(customCategories = cats + trimmed))
                            newCategory = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add category")
                }
            }

            HorizontalDivider()

            if (!isDriveConnected) {
                OutlinedButton(
                    onClick = onConnectDrive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Google Drive for Sharing")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("ptask_backup.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("About")
                }
                Button(
                    onClick = onSignOut,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
