package com.example.ui.listdetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.notification.NotificationHelper
import com.example.ui.dashboard.resolveListIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.widget.Toast
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import com.example.data.drive.DriveColorPalette
import com.example.data.drive.SyncStatus
import com.example.data.model.SharedTaskItem
import com.example.data.model.SharedTodoListFile

private fun formatRelativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "recently"
    val diff = System.currentTimeMillis() - epochMillis
    return when {
        diff < 60_000L -> "just now"
        diff < 3600_000L -> "${diff / 60_000L}m ago"
        diff < 86400_000L -> "${diff / 3600_000L}h ago"
        diff < 7 * 86400_000L -> "${diff / 86400_000L}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    list: TodoList,
    tasks: List<TaskItem>,
    isBiometricUnlocked: Boolean,
    sharedFile: SharedTodoListFile? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    isReadOnly: Boolean = false,
    isRefreshing: Boolean = false,
    currentUserEmail: String = "",
    onBack: () -> Unit,
    onRequestUnlock: () -> Unit,
    onAddTask: () -> Unit,
    onQuickAddTask: (String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onClearCompleted: () -> Unit = {},
    onEditList: (TodoList) -> Unit,
    onDeleteList: (TodoList) -> Unit,
    onShareList: () -> Unit = {},
    onRefreshList: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var quickTaskText by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, PENDING, COMPLETED
    var updaterFilter by remember { mutableStateOf<String?>(null) } // null = All updaters, "ME" = Me, or email
    var showCompletedSection by remember { mutableStateOf(true) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }

    val accentColor = remember(list.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(list.colorHex))
        } catch (_: Exception) {
            Color(0xFF6366F1)
        }
    }

    // Filter tasks by status and updater
    val filteredTasks = remember(tasks, updaterFilter, sharedFile) {
        if (updaterFilter == null || sharedFile == null) {
            tasks
        } else if (updaterFilter == "ME") {
            tasks.filter { task ->
                val st = sharedFile.tasks.find { it.id == task.id }
                st != null && st.updatedBy.equals(currentUserEmail, ignoreCase = true)
            }
        } else {
            tasks.filter { task ->
                val st = sharedFile.tasks.find { it.id == task.id }
                st != null && st.updatedBy.equals(updaterFilter, ignoreCase = true)
            }
        }
    }

    val totalCount = filteredTasks.size
    val pendingTasks = remember(filteredTasks) { filteredTasks.filter { !it.isCompleted }.distinctBy { it.id } }
    val completedTasks = remember(filteredTasks) { filteredTasks.filter { it.isCompleted }.sortedByDescending { it.completedAt }.distinctBy { it.id } }
    val completedCount = completedTasks.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = resolveListIcon(list.icon),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = list.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sharedFile != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Shared List",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Sync status indicator
                            if (sharedFile != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    when (syncStatus) {
                                        SyncStatus.SYNCED -> {
                                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Synced", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontSize = 10.sp)
                                        }
                                        SyncStatus.SYNCING -> {
                                            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Syncing…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                        }
                                        SyncStatus.OFFLINE -> {
                                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Offline – changes will sync later", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B), fontSize = 10.sp)
                                        }
                                        SyncStatus.ACCESS_REVOKED -> {
                                            Text("You no longer have access to this list", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                        }
                                        SyncStatus.DELETED_BY_OWNER -> {
                                            Text("This list was deleted by the owner", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                        }
                                        else -> Unit
                                    }
                                    if (isReadOnly) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text("View only", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShareList, modifier = Modifier.testTag("detail_share_button")) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    if (sharedFile != null) {
                        IconButton(onClick = onRefreshList) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh list")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onShareList()
                                }
                            )
                            if (!isReadOnly) {
                                DropdownMenuItem(
                                    text = { Text("Edit List Details") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEditList(list)
                                    }
                                )
                                if (completedTasks.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Clear Completed Tasks") },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            showClearCompletedDialog = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete List", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDeleteList(list)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if ((!list.isLocked || isBiometricUnlocked) && !isReadOnly) {
                FloatingActionButton(
                    onClick = onAddTask,
                    containerColor = accentColor,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("detail_add_task_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { padding ->
        // Biometric Lock Gate
        if (list.isLocked && !isBiometricUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "List is Locked",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "This list contains protected tasks. Verify your biometrics or device PIN to view.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onRequestUnlock,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("unlock_biometric_button")
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock List")
                        }
                    }
                }
            }
        } else {
            // Unlocked Content View
            PullToRefreshBox(
                isRefreshing = isRefreshing && sharedFile != null,
                onRefresh = { if (sharedFile != null) onRefreshList() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                // Header Banner Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = accentColor.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(list.category) }
                                )
                                Text(
                                    text = "$completedCount / $totalCount completed",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = accentColor
                                )
                            }

                            if (list.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = list.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = accentColor,
                                trackColor = accentColor.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Quick Add Input
                item {
                    if (!isReadOnly) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickTaskText,
                                onValueChange = { quickTaskText = it },
                                placeholder = { Text("Quick add task to this list...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_task_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (quickTaskText.isNotBlank()) {
                                        onQuickAddTask(quickTaskText.trim())
                                        quickTaskText = ""
                                    }
                                },
                                enabled = quickTaskText.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("Add")
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "View only mode – you have viewer permissions on this shared list",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Filter Tabs: All, Pending, Completed
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL" to "All ($totalCount)", "PENDING" to "Pending (${pendingTasks.size})", "COMPLETED" to "Done ($completedCount)").forEach { (mode, label) ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = { filterMode = mode },
                                label = { Text(label) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Updater Filters for shared list: "Updated by me", "Updated by [member]"
                if (sharedFile != null && sharedFile.members.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filter:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilterChip(
                                selected = updaterFilter == null,
                                onClick = { updaterFilter = null },
                                label = { Text("All updaters", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = updaterFilter == "ME",
                                onClick = { updaterFilter = if (updaterFilter == "ME") null else "ME" },
                                label = { Text("Updated by me", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            sharedFile.members.forEach { m ->
                                if (!m.email.equals(currentUserEmail, ignoreCase = true)) {
                                    FilterChip(
                                        selected = updaterFilter == m.email,
                                        onClick = { updaterFilter = if (updaterFilter == m.email) null else m.email },
                                        label = { Text(m.name.ifBlank { m.email.substringBefore("@") }, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Empty state
                if (tasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No tasks in this list yet!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (!isReadOnly) "Use the input above or tap + to create your first task" else "No tasks added by list members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Incomplete Tasks List
                if (filterMode != "COMPLETED") {
                    items(pendingTasks, key = { it.id }) { task ->
                        val sharedTask = sharedFile?.tasks?.find { it.id == task.id }
                        DetailTaskCard(
                            task = task,
                            sharedTask = sharedTask,
                            sharedMembers = sharedFile?.members ?: emptyList(),
                            isReadOnly = isReadOnly,
                            onToggle = {
                                if (isReadOnly) {
                                    Toast.makeText(context, "Cannot toggle tasks in read-only mode", Toast.LENGTH_SHORT).show()
                                } else {
                                    onToggleTask(task)
                                }
                            },
                            onEdit = { onEditTask(task) },
                            onDelete = {
                                if (!isReadOnly) onDeleteTask(task)
                            }
                        )
                    }
                }

                // Collapsible Completed Tasks Section (Sorted newest completed first)
                if (filterMode != "PENDING" && completedTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showCompletedSection = !showCompletedSection }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Completed (${completedTasks.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (showCompletedSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showCompletedSection) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (showCompletedSection) {
                        items(completedTasks, key = { it.id }) { task ->
                            val sharedTask = sharedFile?.tasks?.find { it.id == task.id }
                            DetailTaskCard(
                                task = task,
                                sharedTask = sharedTask,
                                sharedMembers = sharedFile?.members ?: emptyList(),
                                isReadOnly = isReadOnly,
                                onToggle = {
                                    if (isReadOnly) {
                                        Toast.makeText(context, "Cannot toggle tasks in read-only mode", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onToggleTask(task)
                                    }
                                },
                                onEdit = { onEditTask(task) },
                                onDelete = {
                                    if (!isReadOnly) onDeleteTask(task)
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
            }
        }
    }

    // Clear completed confirmation dialog
    if (showClearCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showClearCompletedDialog = false },
            title = { Text("Clear Completed Tasks") },
            text = { Text("Are you sure you want to remove all ${completedTasks.size} completed tasks from \"${list.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCompletedDialog = false
                        onClearCompleted()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailTaskCard(
    task: TaskItem,
    sharedTask: SharedTaskItem? = null,
    sharedMembers: List<com.example.data.model.ListMember> = emptyList(),
    isReadOnly: Boolean = false,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val priorityColor = when (task.priority) {
        "URGENT" -> Color(0xFFEF4444)
        "HIGH" -> Color(0xFFF59E0B)
        "LOW" -> Color(0xFF10B981)
        else -> Color(0xFF6366F1)
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.45f else 1.0f,
        label = "detail_task_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    enabled = !isReadOnly,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_checkbox_${task.id}")
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(animatedAlpha)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface
                    )

                    if (task.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Priority badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(priorityColor.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.priority,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = priorityColor,
                                fontSize = 10.sp
                            )
                        }

                        // Due Date
                        if (task.dueDateEpoch > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = dateFormat.format(Date(task.dueDateEpoch)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Reminder
                        if (task.reminderEpoch > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    NotificationHelper.playSoundPreview(task.reminderSound)
                                }
                            ) {
                                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = timeFormat.format(Date(task.reminderEpoch)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.VolumeUp, contentDescription = "Play sound", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }

                // 24dp Avatar circle for who-updated-it (Shared lists only)
                if (sharedTask != null && (sharedTask.updatedByName.isNotBlank() || sharedTask.updatedBy.isNotBlank())) {
                    val name = sharedTask.updatedByName.ifBlank { sharedTask.updatedBy }
                    val initial = name.firstOrNull()?.uppercaseChar() ?: 'U'
                    val memberColorHex = sharedMembers.find { it.email.equals(sharedTask.updatedBy, ignoreCase = true) }?.color
                        ?: DriveColorPalette.getDeterministicColor(sharedTask.updatedBy)
                    val avatarBg = DriveColorPalette.parseColor(memberColorHex)

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(avatarBg)
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "Updated by $name · ${formatRelativeTime(sharedTask.updatedAt)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                if (!isReadOnly) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Task",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
