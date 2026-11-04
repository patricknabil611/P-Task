package com.example.ui.voice

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ParsedTaskResult
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.ui.viewmodel.NlpUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNlpSheet(
    nlpState: NlpUiState,
    availableLists: List<TodoList>,
    onParseInput: (String) -> Unit,
    onSaveParsedTask: (TaskItem, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by remember { mutableStateOf("") }

    // Speech to text activity result launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                textInput = spokenText
                onParseInput(spokenText)
            }
        }
    }

    val examplePrompts = listOf(
        "Clean the kitchen and organize cabinets for El sha2a",
        "Buy milk and fresh fruit tomorrow at 5pm",
        "Pack passport and foreign currency for travel list",
        "Dry cleaning and pharmacy on the go high priority"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Voice & Natural Task Creator",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Speak or type in your own words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Input Row with Mic button and Send button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Say or type what you need to do...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nlp_text_input"),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Microphone voice trigger
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell P Task what you want to do...")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Submit parse
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onParseInput(textInput)
                        }
                    },
                    enabled = textInput.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (textInput.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Submit",
                        tint = if (textInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Example Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Try saying or tapping:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                examplePrompts.forEach { prompt ->
                    Text(
                        text = "• \"$prompt\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                textInput = prompt
                                onParseInput(prompt)
                            }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // Results Card
            AnimatedVisibility(visible = nlpState is NlpUiState.Loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Gemini is understanding your task...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            AnimatedVisibility(visible = nlpState is NlpUiState.Error) {
                val error = (nlpState as NlpUiState.Error).message
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            AnimatedVisibility(visible = nlpState is NlpUiState.Success) {
                val parsed = (nlpState as NlpUiState.Success).parsed
                ParsedTaskPreviewCard(
                    parsed = parsed,
                    availableLists = availableLists,
                    onConfirm = { taskItem, listTitle ->
                        onSaveParsedTask(taskItem, listTitle)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ParsedTaskPreviewCard(
    parsed: ParsedTaskResult,
    availableLists: List<TodoList>,
    onConfirm: (TaskItem, String) -> Unit
) {
    // Match target list or fallback to first list
    val targetList = availableLists.find {
        it.title.contains(parsed.targetList, ignoreCase = true) ||
                parsed.targetList.contains(it.title, ignoreCase = true)
    } ?: availableLists.firstOrNull()

    val targetListTitle = targetList?.title ?: "Personal Todo daily"
    val targetListId = targetList?.id ?: ""

    val calculatedDueDate = if (parsed.dueDateOffsetHours > 0) {
        System.currentTimeMillis() + (parsed.dueDateOffsetHours * 3600000L)
    } else 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "AI Extracted Task",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(parsed.priority) }
                )
            }

            Text(
                text = parsed.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            if (parsed.notes.isNotBlank()) {
                Text(
                    text = "Notes: ${parsed.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "List: $targetListTitle",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.secondary
                )
                if (parsed.dueDateOffsetHours > 0) {
                    Text(
                        text = "Due: in ~${parsed.dueDateOffsetHours}h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val task = TaskItem(
                        listId = targetListId,
                        title = parsed.title,
                        notes = parsed.notes,
                        priority = parsed.priority,
                        dueDateEpoch = calculatedDueDate,
                        reminderEpoch = if (calculatedDueDate > 0) calculatedDueDate - 3600000L else 0L,
                        category = parsed.category
                    )
                    onConfirm(task, targetListTitle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_ai_task_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm & Add to $targetListTitle")
            }
        }
    }
}
