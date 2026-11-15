package com.example.ui.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import com.example.ai.ParsedTaskResult
import com.example.ui.viewmodel.NlpUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNlpSheet(
    nlpState: NlpUiState,
    targetListTitle: String,
    onParseInput: (String) -> Unit,
    onAddParsedTask: (ParsedTaskResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var micNotice by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                rawText = spoken
                onParseInput(spoken)
            }
        }
    }

    val launchSpeech = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a task (e.g. Buy milk tomorrow urgent)")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            micNotice = "Speech recognizer unavailable on this device. Type your task below instead."
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micNotice = null
            launchSpeech()
        } else {
            micNotice = "Microphone permission denied. Type your task below instead."
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick AI Capture → $targetListTitle",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                label = { Text("Speak or type naturally") },
                placeholder = { Text("e.g., Pay electric bill tomorrow high priority") },
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    launchSpeech()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.testTag("voice_mic_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice input")
                        }
                        IconButton(
                            onClick = { onParseInput(rawText) },
                            enabled = rawText.isNotBlank() && nlpState !is NlpUiState.Parsing,
                            modifier = Modifier.testTag("voice_parse_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Parse task")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_text_input")
            )

            if (!micNotice.isNullOrBlank()) {
                Text(
                    text = micNotice!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (nlpState) {
                NlpUiState.Idle -> {
                    Text(
                        text = "Gemini extracts task title, priority, category, and due date automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NlpUiState.Parsing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Understanding your task...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is NlpUiState.Error -> {
                    Text(
                        text = nlpState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is NlpUiState.Ready -> {
                    val parsed = nlpState.parsed
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = parsed.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Priority: ${parsed.priority} • Category: ${parsed.category}" +
                                    (parsed.dueOffsetDays?.let { " • Due in $it day(s)" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (parsed.notes.isNotBlank()) {
                                Text(
                                    text = parsed.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onAddParsedTask(parsed) },
                                    modifier = Modifier.testTag("add_parsed_task_button")
                                ) {
                                    Text("Add Task")
                                }
                                OutlinedButton(onClick = onDismiss) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
