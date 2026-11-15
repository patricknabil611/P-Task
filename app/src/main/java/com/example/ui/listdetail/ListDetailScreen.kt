package com.example.ui.listdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.drive.DriveColorPalette
import com.example.data.drive.SharedListState
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseUrgent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TaskStatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Done")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    list: TodoList,
    sharedState: SharedListState?,
    tasks: List<TaskItem>,
    categories: List<String>,
    isRefreshingDrive: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefreshDrive: () -> Unit,
    onOpenShare: () -> Unit,
    onEditList: () -> Unit,
    onAddTask: () -> Unit,
    onOpenVoiceCapture: () -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    BackHandler(onBack = onBack)

    var statusFilter by remember { mutableStateOf(TaskStatusFilter.ALL) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    val filteredTasks = remember(tasks, statusFilter, selectedCategory) {
        tasks.distinctBy { it.id }
            .filter { task ->
                val statusOk = when (statusFilter) {
                    TaskStatusFilter.ALL -> true
                    TaskStatusFilter.ACTIVE -> !task.isCompleted
                    TaskStatusFilter.COMPLETED -> task.isCompleted
                }
                val categoryOk = selectedCategory == null || task.category == selectedCategory
                statusOk && categoryOk
            }
            .sortedWith(
                compareBy<TaskItem> { it.isCompleted }
                    .thenByDescending {
                        when (it.priority) {
                            "URGENT" -> 4
                            "HIGH" -> 3
                            "MEDIUM" -> 2
                            else -> 1
                        }
                    }
                    .thenBy { if (it.dueDateEpoch > 0L) it.dueDateEpoch else Long.MAX_VALUE }
            )
    }

    val usedCategories = remember(tasks, categories) {
        (categories + tasks.map { it.category }).filter { it.isNotBlank() }.distinct()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to lists"
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = list.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (list.isShared) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Shared list",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (list.isShared) {
                        IconButton(
                            onClick = onRefreshDrive,
                            modifier = Modifier.testTag("detail_refresh_button")
                        ) {
                            if (isRefreshingDrive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync shared list")
                            }
                        }
                    }
                    IconButton(
                        onClick = onOpenShare,
                        modifier = Modifier.testTag("detail_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share list")
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("detail_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "List actions")
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
                                    onOpenShare()
                                },
                                modifier = Modifier.testTag("detail_menu_share")
                            )
                            if (!list.isReadOnly) {
                                DropdownMenuItem(
                                    text = { Text("Edit List") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEditList()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (!list.isReadOnly) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = onOpenVoiceCapture,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("detail_voice_fab")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Quick AI capture")
                    }
                    FloatingActionButton(
                        onClick = onAddTask,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_task_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add task")
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshingDrive,
            onRefresh = { if (list.isShared) onRefreshDrive() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (list.isReadOnly) {
                        item(key = "readonly_banner") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Text(
                                        text = "You have view-only access to this shared list.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    val members = sharedState?.file?.members.orEmpty()
                    if (members.isNotEmpty()) {
                        item(key = "collaborators_strip") {
                            val context = LocalContext.current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                members.forEach { member ->
                                    val color = DriveColorPalette.getColorForMember(context, member.email)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
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
                                            Text(
                                                text = member.email,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "filter_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaskStatusFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = statusFilter == filter,
                                    onClick = { statusFilter = filter },
                                    label = { Text(filter.label) }
                                )
                            }
                            usedCategories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = {
                                        selectedCategory = if (selectedCategory == cat) null else cat
                                    },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }

                    if (filteredTasks.isEmpty()) {
                        item(key = "empty_tasks") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No tasks here",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (list.isReadOnly) {
                                            "No tasks match the current filter."
                                        } else {
                                            "Tap + to add a task or use AI voice capture."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredTasks,
                            key = { it.id }
                        ) { task ->
                            TaskCard(
                                task = task,
                                isSharedList = list.isShared,
                                isReadOnly = list.isReadOnly,
                                onToggle = { onToggleTask(task) },
                                onEdit = { onEditTask(task) },
                                onDelete = { onDeleteTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    isSharedList: Boolean,
    isReadOnly: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val priorityColor = when (task.priority) {
        "URGENT" -> RoseUrgent
        "HIGH" -> AmberWarning
        "MEDIUM" -> IndigoPrimary
        else -> CyanAccent
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
            .clickable(enabled = !isReadOnly, onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { if (!isReadOnly) onToggle() },
                enabled = !isReadOnly,
                modifier = Modifier.testTag("task_checkbox_${task.id}")
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = priorityColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = task.priority,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (task.dueDateEpoch > 0L) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = dateFmt.format(Date(task.dueDateEpoch)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (task.reminderEpoch > System.currentTimeMillis() && !task.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Reminder set",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isSharedList && task.updatedByEmail.isNotBlank()) {
                        val memberColor = DriveColorPalette.getColorForMember(context, task.updatedByEmail)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = memberColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = task.updatedByEmail.substringBefore("@"),
                                style = MaterialTheme.typography.labelSmall,
                                color = memberColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (!isReadOnly) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_task_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
