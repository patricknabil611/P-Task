package com.example.ui.sharedlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.drive.DriveColorPalette
import com.example.data.drive.SharedListState
import com.example.data.model.TodoList
import com.example.ui.theme.EmeraldSuccess
import kotlinx.coroutines.launch

@Composable
fun ShareListDialog(
    list: TodoList,
    sharedState: SharedListState?,
    isDriveConnected: Boolean,
    onConnectDrive: () -> Unit,
    onShareOneStep: suspend (email: String, role: String) -> Result<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("writer") }
    var isSharing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share \"${list.title}\"",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isDriveConnected) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connect Google Drive once to share lists via email.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(
                                onClick = onConnectDrive,
                                modifier = Modifier.testTag("connect_drive_in_share_button")
                            ) {
                                Text("Connect Google Drive")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        feedbackMessage = null
                    },
                    label = { Text("Recipient email") },
                    placeholder = { Text("colleague@gmail.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_email_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedRole == "writer",
                        onClick = { selectedRole = "writer" },
                        label = { Text("Can edit") },
                        modifier = Modifier.testTag("role_writer_chip")
                    )
                    FilterChip(
                        selected = selectedRole == "reader",
                        onClick = { selectedRole = "reader" },
                        label = { Text("Can view") },
                        modifier = Modifier.testTag("role_reader_chip")
                    )
                }

                if (!feedbackMessage.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                EmeraldSuccess.copy(alpha = 0.14f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isError) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = feedbackMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                val members = sharedState?.file?.members.orEmpty()
                if (members.isNotEmpty()) {
                    Text(
                        text = "Collaborators (${members.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    members.forEach { member ->
                        val color = DriveColorPalette.getColorForMember(context, member.email)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.email.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = member.email,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = when (member.role) {
                                    "owner" -> "Owner"
                                    "reader" -> "View"
                                    else -> "Edit"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        isSharing = true
                        feedbackMessage = null
                        val res = onShareOneStep(email, selectedRole)
                        isSharing = false
                        res.onSuccess { msg ->
                            isError = false
                            feedbackMessage = msg
                            email = ""
                        }.onFailure { err ->
                            isError = true
                            feedbackMessage = err.localizedMessage ?: "Failed to share list."
                        }
                    }
                },
                enabled = !isSharing && email.isNotBlank(),
                modifier = Modifier.testTag("share_confirm_button")
            ) {
                if (isSharing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Share")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("share_close_button")
            ) {
                Text("Close")
            }
        }
    )
}
