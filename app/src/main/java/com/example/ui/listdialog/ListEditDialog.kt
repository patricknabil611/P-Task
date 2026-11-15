package com.example.ui.listdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.data.model.TodoList

val LIST_ICON_OPTIONS: List<Pair<String, ImageVector>> = listOf(
    "list" to Icons.AutoMirrored.Filled.FormatListBulleted,
    "work" to Icons.Default.Work,
    "person" to Icons.Default.Person,
    "home" to Icons.Default.Home,
    "cart" to Icons.Default.ShoppingCart,
    "fitness" to Icons.AutoMirrored.Filled.DirectionsRun,
    "star" to Icons.Default.Star
)

val LIST_COLOR_OPTIONS = listOf(
    "#4F46E5",
    "#0EA5E9",
    "#10B981",
    "#F59E0B",
    "#EF4444",
    "#8B5CF6",
    "#EC4899"
)

fun iconForListKey(key: String): ImageVector {
    return LIST_ICON_OPTIONS.find { it.first == key }?.second
        ?: Icons.AutoMirrored.Filled.FormatListBulleted
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListEditDialog(
    existingList: TodoList? = null,
    onSave: (title: String, iconKey: String, colorHex: String, isLocked: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existingList?.title.orEmpty()) }
    var iconKey by remember { mutableStateOf(existingList?.iconKey ?: "list") }
    var colorHex by remember { mutableStateOf(existingList?.colorHex ?: "#4F46E5") }
    var isLocked by remember { mutableStateOf(existingList?.isLocked ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingList == null) "New List" else "Edit List") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("list_title_input")
                )

                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LIST_ICON_OPTIONS.forEach { (key, vector) ->
                        val selected = key == iconKey
                        IconButton(
                            onClick = { iconKey = key },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = key,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LIST_COLOR_OPTIONS.forEach { hex ->
                        val c = Color(hex.toColorInt())
                        val selected = hex.equals(colorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }

                if (existingList?.isShared != true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Text("Biometric Lock", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = isLocked,
                            onCheckedChange = { isLocked = it },
                            modifier = Modifier.testTag("list_lock_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), iconKey, colorHex, isLocked)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_list_button")
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
