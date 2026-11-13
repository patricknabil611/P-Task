package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.security.configureAppCheck
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.data.model.UserSettings
import com.example.notification.NotificationHelper
import com.example.security.BiometricAuthManager
import com.example.ui.auth.AuthScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.listdetail.ListDetailScreen
import com.example.ui.listdialog.ListEditDialog
import com.example.ui.settings.SettingsSheet
import com.example.ui.taskdialog.TaskEditDialog
import com.example.ui.theme.PTaskTheme
import com.example.ui.viewmodel.TodoViewModel
import com.example.ui.viewmodel.UiState
import com.example.ui.voice.VoiceNlpSheet
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.drive.SyncStatus
import com.example.ui.sharedlist.ShareListDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration before UI or Firebase operations
        configureAppCheck(this, intent)

        // Create notification channels for task deadlines and reminders
        NotificationHelper.createNotificationChannels(this)

        enableEdgeToEdge()

        setContent {
            var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
            var justSignedIn by remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    currentUser = auth.currentUser
                }
                Firebase.auth.addAuthStateListener(listener)
                onDispose {
                    Firebase.auth.removeAuthStateListener(listener)
                }
            }

            if (currentUser == null) {
                PTaskTheme {
                    AuthScreen(
                        onAuthSuccess = {
                            justSignedIn = true
                            currentUser = Firebase.auth.currentUser
                        }
                    )
                }
            } else {
                PTaskApp(
                    activity = this,
                    userUid = currentUser?.uid ?: "default",
                    autoConnectDriveOnEntry = justSignedIn,
                    onConsumeAutoConnectDrive = { justSignedIn = false },
                    onSignOut = {
                        justSignedIn = false
                        Firebase.auth.signOut()
                        currentUser = null
                    }
                )
            }
        }
    }
}

@Composable
fun PTaskApp(
    activity: FragmentActivity,
    userUid: String = "default",
    autoConnectDriveOnEntry: Boolean = false,
    onConsumeAutoConnectDrive: () -> Unit = {},
    onSignOut: () -> Unit,
    viewModel: TodoViewModel = viewModel(key = userUid)
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val listsState by viewModel.listsState.collectAsStateWithLifecycle()
    val allTasksState by viewModel.allTasksState.collectAsStateWithLifecycle()
    val sharedListsMap by viewModel.sharedListsMap.collectAsStateWithLifecycle()
    val syncStatusMap by viewModel.syncStatusMap.collectAsStateWithLifecycle()
    val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
    val isRefreshingDrive by viewModel.isRefreshingDrive.collectAsStateWithLifecycle()
    val userAvatarColor by viewModel.userAvatarColor.collectAsStateWithLifecycle()
    val driveAuthIntent by viewModel.driveAuthIntent.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val selectedListId by viewModel.selectedListId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val isBiometricUnlocked by viewModel.isBiometricUnlocked.collectAsStateWithLifecycle()
    val nlpState by viewModel.nlpState.collectAsStateWithLifecycle()
    val snackMessage by viewModel.snackMessage.collectAsStateWithLifecycle()
    val undoAction by viewModel.undoAction.collectAsStateWithLifecycle()

    val resolvedCategories = settings?.getResolvedCategories() ?: UserSettings.DEFAULT_CATEGORIES

    // Dialog & Sheet States
    var showTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskItem?>(null) }
    var targetListIdForNewTask by remember { mutableStateOf("") }

    var showListDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<TodoList?>(null) }

    var showShareDialog by remember { mutableStateOf(false) }
    var listToShare by remember { mutableStateOf<TodoList?>(null) }

    var showVoiceNlpSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = com.example.data.drive.GoogleDriveAuthHelper.handleSignInResult(result.data)
        if (account != null) {
            viewModel.onDriveAccountConnected(account)
        } else {
            viewModel.onDriveConsentCancelled()
        }
    }

    LaunchedEffect(autoConnectDriveOnEntry, isDriveConnected) {
        if (autoConnectDriveOnEntry) {
            onConsumeAutoConnectDrive()
            if (!isDriveConnected) {
                viewModel.connectGoogleDrive()
            }
        }
    }

    LaunchedEffect(driveAuthIntent) {
        driveAuthIntent?.let { intent ->
            driveConsentLauncher.launch(intent)
            viewModel.clearDriveAuthIntent()
        }
    }

    // Snackbar listeners
    LaunchedEffect(snackMessage) {
        snackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackMessage()
        }
    }

    LaunchedEffect(undoAction) {
        undoAction?.let { undo ->
            val result = snackbarHostState.showSnackbar(
                message = undo.message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                undo.onUndo()
            }
            viewModel.clearUndoAction()
        }
    }

    PTaskTheme(themeSetting = settings?.themeMode ?: "SYSTEM") {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { _ ->
            when {
                listsState is UiState.Loading || allTasksState is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                listsState is UiState.Error -> {
                    val error = (listsState as UiState.Error).message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                else -> {
                    val lists = (listsState as? UiState.Success)?.data ?: emptyList()
                    val tasks = (allTasksState as? UiState.Success)?.data ?: emptyList()

                    val currentSharedState = selectedListId?.let { sharedListsMap[it] }
                    val currentSelectedList = if (currentSharedState != null) {
                        currentSharedState.file.toTodoList(isReadOnly = currentSharedState.isReadOnly)
                    } else {
                        lists.find { it.id == selectedListId }
                    }

                    if (currentSelectedList != null) {
                        val tasksForList = tasks.filter { it.listId == currentSelectedList.id }

                        ListDetailScreen(
                            list = currentSelectedList,
                            tasks = tasksForList,
                            isBiometricUnlocked = isBiometricUnlocked,
                            sharedFile = currentSharedState?.file,
                            syncStatus = syncStatusMap[currentSelectedList.id] ?: SyncStatus.SYNCED,
                            isReadOnly = currentSharedState?.isReadOnly ?: false,
                            isRefreshing = isRefreshingDrive,
                            currentUserEmail = viewModel.getCurrentUserEmail(),
                            onBack = { viewModel.selectList(null) },
                            onRequestUnlock = {
                                BiometricAuthManager.promptBiometric(
                                    activity = activity,
                                    title = "Unlock ${currentSelectedList.title}",
                                    subtitle = "Confirm fingerprint or PIN to access this list",
                                    onSuccess = { viewModel.setBiometricUnlocked(true) },
                                    onError = { err ->
                                        Toast.makeText(activity, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onAddTask = {
                                taskToEdit = null
                                targetListIdForNewTask = currentSelectedList.id
                                showTaskDialog = true
                            },
                            onQuickAddTask = { title ->
                                val newTask = TaskItem(
                                    listId = currentSelectedList.id,
                                    title = title,
                                    category = currentSelectedList.category
                                )
                                viewModel.addTask(newTask, currentSelectedList.title)
                            },
                            onToggleTask = { task -> viewModel.toggleTask(task) },
                            onEditTask = { task ->
                                taskToEdit = task
                                targetListIdForNewTask = task.listId
                                showTaskDialog = true
                            },
                            onDeleteTask = { task -> viewModel.deleteTask(task) },
                            onClearCompleted = {
                                viewModel.clearCompletedTasks(currentSelectedList.id)
                            },
                            onEditList = { list ->
                                listToEdit = list
                                showListDialog = true
                            },
                            onDeleteList = { list ->
                                viewModel.deleteTodoList(list.id, list.title)
                            },
                            onShareList = {
                                listToShare = currentSelectedList
                                showShareDialog = true
                            },
                            onRefreshList = { viewModel.refreshDriveSharedLists(interactive = true) }
                        )
                    } else {
                        DashboardScreen(
                            lists = lists,
                            allTasks = tasks,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            availableCategories = resolvedCategories,
                            sharedListsMap = sharedListsMap,
                            currentUserEmail = viewModel.getCurrentUserEmail(),
                            isDriveConnected = isDriveConnected,
                            isRefreshingDrive = isRefreshingDrive,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onCategoryChange = { viewModel.setCategoryFilter(it) },
                            onSelectList = { list ->
                                if (list.isLocked && !isBiometricUnlocked) {
                                    BiometricAuthManager.promptBiometric(
                                        activity = activity,
                                        title = "Unlock ${list.title}",
                                        subtitle = "Confirm fingerprint or PIN to open list",
                                        onSuccess = {
                                            viewModel.setBiometricUnlocked(true)
                                            viewModel.selectList(list.id)
                                        },
                                        onError = { err ->
                                            Toast.makeText(activity, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    viewModel.selectList(list.id)
                                }
                            },
                            onAddNewList = {
                                listToEdit = null
                                showListDialog = true
                            },
                            onEditList = { list ->
                                listToEdit = list
                                showListDialog = true
                            },
                            onDeleteList = { list ->
                                viewModel.deleteTodoList(list.id, list.title)
                            },
                            onAddNewTask = {
                                taskToEdit = null
                                targetListIdForNewTask = lists.firstOrNull()?.id ?: ""
                                showTaskDialog = true
                            },
                            onToggleTask = { task -> viewModel.toggleTask(task) },
                            onEditTask = { task ->
                                taskToEdit = task
                                targetListIdForNewTask = task.listId
                                showTaskDialog = true
                            },
                            onOpenVoiceNlp = {
                                viewModel.resetNlpState()
                                showVoiceNlpSheet = true
                            },
                            onOpenSettings = {
                                showSettingsSheet = true
                            },
                            onShareList = { list ->
                                listToShare = list
                                showShareDialog = true
                            },
                            onRefreshDrive = {
                                viewModel.refreshDriveSharedLists(interactive = true)
                            },
                            onConnectDrive = {
                                viewModel.connectGoogleDrive()
                            }
                        )
                    }

                    // Task Edit / Create Dialog
                    if (showTaskDialog) {
                        val activeListId = targetListIdForNewTask.ifBlank { currentSelectedList?.id ?: "" }
                        val activeSharedState = sharedListsMap[activeListId]
                        val activeSharedTask = activeSharedState?.file?.tasks?.find { it.id == taskToEdit?.id }
                        val allAvailableLists = remember(lists, sharedListsMap) {
                            (lists + sharedListsMap.values.map { it.file.toTodoList() }).distinctBy { it.id }
                        }

                        TaskEditDialog(
                            taskToEdit = taskToEdit,
                            currentListId = activeListId.ifBlank { lists.firstOrNull()?.id ?: "" },
                            availableLists = allAvailableLists,
                            sharedTask = activeSharedTask,
                            isReadOnly = activeSharedState?.isReadOnly ?: false,
                            onDismiss = { showTaskDialog = false },
                            onSaveTask = { task, listTitle ->
                                if (taskToEdit == null) {
                                    viewModel.addTask(task, listTitle)
                                } else {
                                    viewModel.updateTask(task, listTitle)
                                }
                                showTaskDialog = false
                            }
                        )
                    }

                    // List Edit / Create Dialog
                    if (showListDialog) {
                        ListEditDialog(
                            listToEdit = listToEdit,
                            availableCategories = resolvedCategories,
                            onDismiss = { showListDialog = false },
                            onSaveList = { list ->
                                if (listToEdit == null) {
                                    viewModel.addTodoList(list)
                                } else {
                                    viewModel.updateTodoList(list)
                                }
                                showListDialog = false
                            }
                        )
                    }

                    // Share List Dialog (1-step sharing flow)
                    if (showShareDialog && listToShare != null) {
                        val targetShared = sharedListsMap[listToShare!!.id]
                        ShareListDialog(
                            list = listToShare!!,
                            sharedFile = targetShared?.file,
                            currentUserEmail = viewModel.getCurrentUserEmail(),
                            onOneStepShare = { email, role, onResult ->
                                viewModel.shareListAndInviteOneStep(listToShare!!, email, role, onResult)
                            },
                            onUpdateRole = { memberEmail, newRole ->
                                viewModel.updateMemberRole(listToShare!!.id, memberEmail, newRole)
                            },
                            onRemoveMember = { memberEmail ->
                                viewModel.removeMemberFromSharedList(listToShare!!.id, memberEmail)
                            },
                            onLeaveList = {
                                viewModel.leaveSharedList(listToShare!!.id)
                            },
                            onDeleteSharedList = {
                                viewModel.deleteSharedList(listToShare!!.id)
                            },
                            onDismiss = {
                                showShareDialog = false
                                listToShare = null
                            }
                        )
                    }

                    // Voice NLP Sheet
                    if (showVoiceNlpSheet) {
                        VoiceNlpSheet(
                            nlpState = nlpState,
                            availableLists = lists,
                            onParseInput = { input ->
                                viewModel.parseNlpInput(input, lists)
                            },
                            onSaveParsedTask = { task, listTitle ->
                                viewModel.addTask(task, listTitle)
                                showVoiceNlpSheet = false
                            },
                            onDismiss = { showVoiceNlpSheet = false }
                        )
                    }

                    // Settings Sheet
                    if (showSettingsSheet) {
                        SettingsSheet(
                            currentSettings = settings,
                            userAvatarColor = userAvatarColor,
                            isDriveConnected = isDriveConnected,
                            onSaveSettings = { themeMode, biometric, sound ->
                                viewModel.updateSettings(themeMode, biometric, sound)
                            },
                            onSelectUserColor = { hex ->
                                viewModel.setUserAvatarColor(hex)
                            },
                            onConnectDrive = {
                                viewModel.connectGoogleDrive()
                            },
                            onAddCategory = { name, callback ->
                                viewModel.addCategory(name, callback)
                            },
                            onRenameCategory = { oldName, newName, callback ->
                                viewModel.renameCategory(oldName, newName, callback)
                            },
                            onDeleteCategory = { cat, rep, callback ->
                                viewModel.deleteCategory(cat, rep, callback)
                            },
                            onMoveCategory = { cat, up ->
                                viewModel.moveCategory(cat, up)
                            },
                            onResetCategories = {
                                viewModel.resetCategoriesToDefaults()
                            },
                            onExportBackup = { onReady ->
                                viewModel.exportBackupJson(onReady)
                            },
                            onImportBackup = { json, callback ->
                                viewModel.importBackupJson(json, callback)
                            },
                            onSignOut = {
                                showSettingsSheet = false
                                viewModel.onUserSignOut {
                                    onSignOut()
                                }
                            },
                            onDismiss = { showSettingsSheet = false }
                        )
                    }
                }
            }
        }
    }
}

// Backward-compatible alias
@Composable
fun OmniTaskApp(
    activity: FragmentActivity,
    onSignOut: () -> Unit,
    viewModel: TodoViewModel = viewModel()
) = PTaskApp(activity, "default", false, {}, onSignOut, viewModel)
