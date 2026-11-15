package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.drive.DriveColorPalette
import com.example.data.drive.SharedListState
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.ui.listdialog.iconForListKey
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    listsState: UiState<List<TodoList>>,
    sharedLists: List<SharedListState>,
    allTasks: List<TaskItem>,
    isDriveConnected: Boolean,
    isRefreshingDrive: Boolean,
    snackbarHostState: SnackbarHostState,
    onRefreshDrive: () -> Unit,
    onConnectDrive: () -> Unit,
    onOpenList: (TodoList) -> Unit,
    onOpenShare: (TodoList) -> Unit,
    onEditList: (TodoList) -> Unit,
    onDeleteList: (TodoList) -> Unit,
    onCreateListClick: () -> Unit,
    onOpenVoiceCapture: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val personalLists = (listsState as? UiState.Success)?.data.orEmpty()
    val sharedTodoLists = remember(sharedLists) {
        sharedLists.map { state -> state.file.toTodoList(isReadOnly = state.isReadOnly) }
    }
    val combinedLists = remember(personalLists, sharedTodoLists) {
        (personalLists + sharedTodoLists)
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
    }

    val filteredLists = remember(combinedLists, allTasks, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            combinedLists
        } else {
            combinedLists.filter { list ->
                list.title.contains(q, ignoreCase = true) ||
                    allTasks.any { it.listId == list.id && it.title.contains(q, ignoreCase = true) }
            }
        }
    }

    val activeTaskCount = remember(allTasks) { allTasks.count { !it.isCompleted } }
    val urgentTaskCount = remember(allTasks) { allTasks.count { !it.isCompleted && it.priority == "URGENT" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "P Task",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${combinedLists.size} lists • $activeTaskCount active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isDriveConnected) onRefreshDrive() else onConnectDrive()
                        },
                        modifier = Modifier.testTag("refresh_drive_button")
                    ) {
                        if (isRefreshingDrive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Drive lists"
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search lists"
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onOpenVoiceCapture,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("voice_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Quick AI voice capture"
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onCreateListClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New List") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_list_fab")
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshingDrive,
            onRefresh = {
                if (isDriveConnected) onRefreshDrive() else onConnectDrive()
            },
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "search_bar") {
                        AnimatedVisibility(visible = showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Search lists or tasks") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dashboard_search_input")
                            )
                        }
                    }

                    item(key = "hero_summary") {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(IndigoDeep, IndigoPrimary, CyanAccent)
                                        )
                                    )
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Today's Focus",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = "$activeTaskCount open tasks • $urgentTaskCount urgent",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Group,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "${sharedTodoLists.size} shared",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isDriveConnected) {
                        item(key = "drive_connect_banner") {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudQueue,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = "Share Lists via Google Drive",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = "Connect Drive once to see shared lists and invite by email.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = onConnectDrive,
                                        modifier = Modifier.testTag("connect_drive_banner_button")
                                    ) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }
                    }

                    when (listsState) {
                        UiState.Loading -> {
                            item(key = "loading_indicator") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        is UiState.Error -> {
                            item(key = "error_banner") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = listsState.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                        is UiState.Success -> Unit
                    }

                    if (listsState is UiState.Success && filteredLists.isEmpty()) {
                        item(key = "empty_lists_state") {
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
                                        text = if (searchQuery.isBlank()) "No lists yet" else "No matching lists",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            "Tap New List below to create your first task list."
                                        } else {
                                            "Try a different search keyword."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredLists,
                            key = { it.id }
                        ) { list ->
                            val listTasks = allTasks.filter { it.listId == list.id }
                            val openCount = listTasks.count { !it.isCompleted }
                            val doneCount = listTasks.count { it.isCompleted }
                            val sharedState = sharedLists.find { it.file.id == list.id }

                            TodoListCard(
                                list = list,
                                sharedState = sharedState,
                                openCount = openCount,
                                doneCount = doneCount,
                                onOpen = { onOpenList(list) },
                                onShare = { onOpenShare(list) },
                                onEdit = { onEditList(list) },
                                onDelete = { onDeleteList(list) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoListCard(
    list: TodoList,
    sharedState: SharedListState?,
    openCount: Int,
    doneCount: Int,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = remember(list.colorHex) { DriveColorPalette.parseColor(list.colorHex) }
    val memberCount = sharedState?.file?.members?.size ?: 0

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("list_card_${list.id}")
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForListKey(list.iconKey),
                    contentDescription = list.title,
                    tint = accent
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = list.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (list.isShared) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("shared_badge_${list.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = "Shared list",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (memberCount > 1) "Shared ($memberCount)" else "Shared",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else if (list.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked list",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = buildString {
                        append("$openCount active")
                        if (doneCount > 0) append(" • $doneCount done")
                        if (list.isReadOnly) append(" • View only")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("list_menu_${list.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "List options"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                        modifier = Modifier.testTag("menu_share_${list.id}")
                    )
                    if (!list.isReadOnly) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            modifier = Modifier.testTag("menu_edit_${list.id}")
                        )
                    }
                    if (!list.isShared) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("menu_delete_${list.id}")
                        )
                    }
                }
            }
        }
    }
}
