package com.example.ui.taskdialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DEFAULT_CATEGORIES
import com.example.data.model.TaskItem
import com.example.notification.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditDialog(
    listId: String,
    existingTask: TaskItem? = null,
    categories: List<String>,
    defaultSoundKey: String,
    onSave: (TaskItem) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existingTask?.title.orEmpty()) }
    var notes by remember { mutableStateOf(existingTask?.notes.orEmpty()) }
    var priority by remember { mutableStateOf(existingTask?.priority ?: "MEDIUM") }
    var category by remember {
        mutableStateOf(existingTask?.category ?: categories.firstOrNull() ?: "General")
    }
    var dueDateEpoch by remember { mutableLongStateOf(existingTask?.dueDateEpoch ?: 0L) }
    var reminderEpoch by remember { mutableLongStateOf(existingTask?.reminderEpoch ?: 0L) }
    var soundKey by remember { mutableStateOf(existingTask?.reminderSoundKey ?: defaultSoundKey) }

    val availableCats = (categories.ifEmpty { DEFAULT_CATEGORIES } + category).distinct()
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTask == null) "New Task" else "Edit Task") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_notes_input")
                )

                Text("Priority", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("URGENT", "HIGH", "MEDIUM", "LOW").forEach { level ->
                        FilterChip(
                            selected = priority == level,
                            onClick = { priority = level },
                            label = { Text(level) }
                        )
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableCats.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (dueDateEpoch > 0L) {
                            "Due: ${dateFmt.format(Date(dueDateEpoch))}"
                        } else {
                            "Due Date: None"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dueDateEpoch == 0L,
                        onClick = { dueDateEpoch = 0L },
                        label = { Text("None") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { dueDateEpoch = offsetDaysEndOfDay(0) },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { dueDateEpoch = offsetDaysEndOfDay(1) },
                        label = { Text("Tomorrow") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { dueDateEpoch = offsetDaysEndOfDay(7) },
                        label = { Text("+1 Week") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (reminderEpoch > 0L) {
                            "Reminder: ${dateFmt.format(Date(reminderEpoch))}"
                        } else {
                            "Reminder: Off"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reminderEpoch == 0L,
                        onClick = { reminderEpoch = 0L },
                        label = { Text("Off") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { reminderEpoch = System.currentTimeMillis() + 3_600_000L },
                        label = { Text("In 1 hr") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { reminderEpoch = System.currentTimeMillis() + 10_800_000L },
                        label = { Text("In 3 hrs") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { reminderEpoch = offsetDaysMorning(1) },
                        label = { Text("Tomorrow 9 AM") }
                    )
                }

                if (reminderEpoch > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Alert Sound", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { NotificationHelper.playSoundPreview(soundKey) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Preview sound")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotificationHelper.AVAILABLE_SOUNDS.forEach { option ->
                            FilterChip(
                                selected = soundKey == option.key,
                                onClick = {
                                    soundKey = option.key
                                    NotificationHelper.playSoundPreview(option.key)
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val saved = (existingTask ?: TaskItem(listId = listId)).copy(
                            listId = listId,
                            title = title.trim(),
                            notes = notes.trim(),
                            priority = priority,
                            category = category,
                            dueDateEpoch = dueDateEpoch,
                            reminderEpoch = reminderEpoch,
                            reminderSoundKey = soundKey
                        )
                        onSave(saved)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_task_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun offsetDaysEndOfDay(days: Int): Long {
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, 20)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis
}

private fun offsetDaysMorning(days: Int): Long {
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis
}
