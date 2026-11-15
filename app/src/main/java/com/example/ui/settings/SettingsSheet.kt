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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.DEFAULT_CATEGORIES
import com.example.data.model.UserSettings
import com.example.notification.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    userEmail: String,
    settings: UserSettings,
    isDriveConnected: Boolean,
    onUpdateSettings: (UserSettings) -> Unit,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (oldName: String, newName: String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onConnectDrive: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var soundExpanded by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var notificationDescriptionDraft by remember(settings.notificationDescription) {
        mutableStateOf(settings.notificationDescription)
    }

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

    val formattedReminderTime = remember(settings.defaultReminderHour, settings.defaultReminderMinute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.defaultReminderHour.coerceIn(0, 23))
            set(Calendar.MINUTE, settings.defaultReminderMinute.coerceIn(0, 59))
        }
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    val cats = settings.customCategories.ifEmpty { DEFAULT_CATEGORIES }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DarkMode, contentDescription = null)
                    Text("Dark Theme", style = MaterialTheme.typography.bodyLarge)
                }
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

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Notifications Settings",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Default Reminder Time",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = formattedReminderTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { showTimePickerDialog = true },
                            modifier = Modifier.testTag("notification_time_picker_button")
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Time")
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = soundExpanded,
                        onExpandedChange = { soundExpanded = !soundExpanded }
                    ) {
                        OutlinedTextField(
                            value = currentSoundLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Notification Sound") },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            NotificationHelper.playSoundPreview(settings.defaultSoundKey)
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Preview sound"
                                        )
                                    }
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = soundExpanded)
                                }
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth()
                                .testTag("notification_sound_dropdown")
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

                    OutlinedTextField(
                        value = notificationDescriptionDraft,
                        onValueChange = { notificationDescriptionDraft = it.take(200) },
                        label = { Text("Notification Description") },
                        placeholder = { Text("Scheduled P Task reminder") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_description_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val cleaned = notificationDescriptionDraft.trim()
                                    .ifBlank { "Scheduled P Task reminder" }
                                notificationDescriptionDraft = cleaned
                                onUpdateSettings(settings.copy(notificationDescription = cleaned))
                            },
                            enabled = notificationDescriptionDraft.trim() != settings.notificationDescription,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_notification_description_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Description")
                        }
                        OutlinedButton(
                            onClick = {
                                val desc = notificationDescriptionDraft.trim()
                                    .ifBlank { settings.notificationDescription }
                                NotificationHelper.showNotification(
                                    context = context,
                                    taskId = "preview_notification",
                                    title = "Sample Task Reminder",
                                    listName = "P Task",
                                    soundKey = settings.defaultSoundKey,
                                    description = desc
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_notification_button")
                        ) {
                            Text("Test Alert")
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Categories Settings (${cats.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                OutlinedButton(
                    onClick = { showCategoriesDialog = true },
                    modifier = Modifier.testTag("manage_categories_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manage")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cats.forEach { cat ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
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

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = settings.defaultReminderHour.coerceIn(0, 23),
            initialMinute = settings.defaultReminderMinute.coerceIn(0, 59),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = { Text("Default Reminder Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSettings(
                            settings.copy(
                                defaultReminderHour = timePickerState.hour,
                                defaultReminderMinute = timePickerState.minute
                            )
                        )
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_notification_time_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategoriesDialog) {
        CategoriesManagementDialog(
            categories = cats,
            onAddCategory = onAddCategory,
            onUpdateCategory = onUpdateCategory,
            onDeleteCategory = onDeleteCategory,
            onDismiss = { showCategoriesDialog = false }
        )
    }
}

@Composable
private fun CategoriesManagementDialog(
    categories: List<String>,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (oldName: String, newName: String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategory by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var editingDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manage Categories", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it.take(60) },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_add_input")
                    )
                    Button(
                        onClick = {
                            onAddCategory(newCategory)
                            newCategory = ""
                        },
                        enabled = newCategory.isNotBlank(),
                        modifier = Modifier.testTag("category_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add category")
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = categories,
                        key = { it }
                    ) { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (editingCategory == cat) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editingDraft,
                                        onValueChange = { editingDraft = it.take(60) },
                                        singleLine = true,
                                        label = { Text("Rename category") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("category_rename_input")
                                    )
                                    IconButton(
                                        onClick = {
                                            onUpdateCategory(cat, editingDraft)
                                            editingCategory = null
                                            editingDraft = ""
                                        },
                                        enabled = editingDraft.isNotBlank(),
                                        modifier = Modifier.testTag("category_rename_save_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Save category name",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingCategory = null
                                            editingDraft = ""
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel rename")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                editingCategory = cat
                                                editingDraft = cat
                                            },
                                            modifier = Modifier.testTag("category_edit_$cat")
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit $cat"
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteCategory(cat) },
                                            enabled = categories.size > 1,
                                            modifier = Modifier.testTag("category_delete_$cat")
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete $cat",
                                                tint = if (categories.size > 1) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("categories_done_button")
            ) {
                Text("Done")
            }
        }
    )
}
