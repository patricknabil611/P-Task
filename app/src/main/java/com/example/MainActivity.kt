package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.notification.NotificationHelper
import com.example.security.BiometricAuthManager
import com.example.security.configureAppCheck
import com.example.ui.about.AboutDialog
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.performGoogleSignOut
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.listdetail.ListDetailScreen
import com.example.ui.listdialog.ListEditDialog
import com.example.ui.settings.SettingsSheet
import com.example.ui.sharedlist.ShareListDialog
import com.example.ui.taskdialog.TaskEditDialog
import com.example.ui.theme.PTaskTheme
import com.example.ui.viewmodel.TodoViewModel
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UndoAction
import com.example.ui.voice.VoiceNlpSheet
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        NotificationHelper.createNotificationChannels(this)
        enableEdgeToEdge()

        setContent {
            PTaskAppRoot(activity = this)
        }
    }
}

@Composable
private fun PTaskAppRoot(activity: FragmentActivity) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val user = currentUser
    if (user == null) {
        PTaskTheme {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
    } else {
        key(user.uid) {
            val viewModel: TodoViewModel = viewModel(
                key = user.uid,
                factory = TodoViewModel.Factory(activity.application)
            )
            AuthenticatedAppContent(
                activity = activity,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun AuthenticatedAppContent(
    activity: FragmentActivity,
    viewModel: TodoViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val listsState by viewModel.listsState.collectAsStateWithLifecycle()
    val sharedLists by viewModel.sharedLists.collectAsStateWithLifecycle()
    val allTasks by viewModel.allCombinedTasks.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
    val isRefreshingDrive by viewModel.isRefreshingDrive.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val driveConsentIntent by viewModel.driveConsentIntent.collectAsStateWithLifecycle()
    val nlpState by viewModel.nlpState.collectAsStateWithLifecycle()
    val lastUndo by viewModel.lastUndo.collectAsStateWithLifecycle()

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val navController = rememberNavController()

    var showCreateListDialog by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<TodoList?>(null) }
    var sharingList by remember { mutableStateOf<TodoList?>(null) }
    var addingTaskForList by remember { mutableStateOf<TodoList?>(null) }
    var editingTaskPair by remember { mutableStateOf<Pair<TodoList, TaskItem>?>(null) }
    var voiceTargetList by remember { mutableStateOf<TodoList?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDriveSignInResult(result.data)
        } else {
            viewModel.onDriveConsentCancelled()
        }
    }

    LaunchedEffect(driveConsentIntent) {
        val intent = driveConsentIntent
        if (intent != null) {
            viewModel.clearDriveConsentIntent()
            driveConsentLauncher.launch(intent)
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val personalLists = (listsState as? UiState.Success)?.data.orEmpty()
    val allResolvedLists = remember(personalLists, sharedLists) {
        (personalLists + sharedLists.map { it.file.toTodoList(it.isReadOnly) })
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
    }

    LaunchedEffect(lastUndo) {
        val action = lastUndo
        if (action is UndoAction.DeletedTask) {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted \"${action.task.title}\"",
                actionLabel = "Undo"
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                val listTitle = allResolvedLists.find { it.id == action.task.listId }?.title ?: "List"
                viewModel.undoLastDelete(listTitle)
            } else {
                viewModel.clearUndo()
            }
        }
    }

    fun openListWithGuard(list: TodoList) {
        if (list.isLocked && !list.isShared && settings.biometricEnabled &&
            BiometricAuthManager.isBiometricAvailable(activity)
        ) {
            BiometricAuthManager.authenticate(
                activity = activity,
                title = "Unlock ${list.title}",
                onSuccess = { navController.navigate("list/${list.id}") },
                onError = { err ->
                    scope.launch { snackbarHostState.showSnackbar(err) }
                }
            )
        } else {
            navController.navigate("list/${list.id}")
        }
    }

    PTaskTheme(darkTheme = settings.useDarkTheme) {
        NavHost(
            navController = navController,
            startDestination = "dashboard"
        ) {
            composable("dashboard") {
                DashboardScreen(
                    listsState = listsState,
                    sharedLists = sharedLists,
                    allTasks = allTasks,
                    isDriveConnected = isDriveConnected,
                    isRefreshingDrive = isRefreshingDrive,
                    snackbarHostState = snackbarHostState,
                    onRefreshDrive = { viewModel.refreshSharedLists() },
                    onConnectDrive = { viewModel.requestDriveConnect() },
                    onOpenList = ::openListWithGuard,
                    onOpenShare = { sharingList = it },
                    onEditList = { editingList = it },
                    onDeleteList = { viewModel.deleteList(it.id) },
                    onCreateListClick = { showCreateListDialog = true },
                    onOpenVoiceCapture = {
                        val target = allResolvedLists.firstOrNull { !it.isReadOnly }
                        if (target != null) {
                            viewModel.resetNlpState()
                            voiceTargetList = target
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Create a list first to add tasks.")
                            }
                        }
                    },
                    onOpenSettings = { showSettingsSheet = true }
                )
            }

            composable(
                route = "list/{listId}",
                arguments = listOf(navArgument("listId") { type = NavType.StringType })
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getString("listId").orEmpty()
                val resolvedList = allResolvedLists.find { it.id == listId }

                if (resolvedList == null) {
                    LaunchedEffect(listId) {
                        navController.popBackStack()
                    }
                } else {
                    val sharedState = sharedLists.find { it.file.id == resolvedList.id }
                    val listTasks = allTasks.filter { it.listId == resolvedList.id }

                    ListDetailScreen(
                        list = resolvedList,
                        sharedState = sharedState,
                        tasks = listTasks,
                        categories = settings.customCategories,
                        isRefreshingDrive = isRefreshingDrive,
                        snackbarHostState = snackbarHostState,
                        onBack = { navController.popBackStack() },
                        onRefreshDrive = { viewModel.refreshSharedLists() },
                        onOpenShare = { sharingList = resolvedList },
                        onEditList = { editingList = resolvedList },
                        onAddTask = { addingTaskForList = resolvedList },
                        onOpenVoiceCapture = {
                            viewModel.resetNlpState()
                            voiceTargetList = resolvedList
                        },
                        onEditTask = { task -> editingTaskPair = resolvedList to task },
                        onToggleTask = { task -> viewModel.toggleTaskCompleted(task, resolvedList.title) },
                        onDeleteTask = { task -> viewModel.deleteTask(task) }
                    )
                }
            }
        }

        if (showCreateListDialog) {
            ListEditDialog(
                onSave = { title, iconKey, colorHex, isLocked ->
                    viewModel.createList(title, iconKey, colorHex, isLocked)
                    showCreateListDialog = false
                },
                onDismiss = { showCreateListDialog = false }
            )
        }

        editingList?.let { target ->
            ListEditDialog(
                existingList = target,
                onSave = { title, iconKey, colorHex, isLocked ->
                    viewModel.updateList(
                        target.copy(
                            title = title,
                            iconKey = iconKey,
                            colorHex = colorHex,
                            isLocked = isLocked
                        )
                    )
                    editingList = null
                },
                onDismiss = { editingList = null }
            )
        }

        sharingList?.let { target ->
            val currentResolved = allResolvedLists.find { it.id == target.id }
                ?: allResolvedLists.find { it.title == target.title && it.isShared }
                ?: target
            val currentSharedState = sharedLists.find { it.file.id == currentResolved.id }

            ShareListDialog(
                list = currentResolved,
                sharedState = currentSharedState,
                isDriveConnected = isDriveConnected,
                onConnectDrive = { viewModel.requestDriveConnect() },
                onShareOneStep = { email, role ->
                    viewModel.shareListAndInviteOneStep(currentResolved, email, role)
                },
                onDismiss = { sharingList = null }
            )
        }

        addingTaskForList?.let { targetList ->
            TaskEditDialog(
                listId = targetList.id,
                categories = settings.customCategories,
                defaultSoundKey = settings.defaultSoundKey,
                onSave = { newTask ->
                    viewModel.saveTask(newTask, targetList.title, isNew = true)
                    addingTaskForList = null
                },
                onDismiss = { addingTaskForList = null }
            )
        }

        editingTaskPair?.let { (targetList, existingTask) ->
            TaskEditDialog(
                listId = targetList.id,
                existingTask = existingTask,
                categories = settings.customCategories,
                defaultSoundKey = settings.defaultSoundKey,
                onSave = { updated ->
                    viewModel.saveTask(updated, targetList.title, isNew = false)
                    editingTaskPair = null
                },
                onDismiss = { editingTaskPair = null }
            )
        }

        voiceTargetList?.let { targetList ->
            VoiceNlpSheet(
                nlpState = nlpState,
                targetListTitle = targetList.title,
                onParseInput = { text -> viewModel.parseVoiceOrTextWithAi(text) },
                onAddParsedTask = { parsed ->
                    viewModel.commitParsedTask(targetList.id, targetList.title, parsed)
                    voiceTargetList = null
                },
                onDismiss = {
                    viewModel.resetNlpState()
                    voiceTargetList = null
                }
            )
        }

        if (showSettingsSheet) {
            SettingsSheet(
                userEmail = viewModel.getCurrentUserEmail(),
                settings = settings,
                isDriveConnected = isDriveConnected,
                onUpdateSettings = { viewModel.updateSettings(it) },
                onConnectDrive = { viewModel.requestDriveConnect() },
                onExportBackup = { uri -> viewModel.exportBackup(uri) },
                onImportBackup = { uri -> viewModel.importBackup(uri) },
                onOpenAbout = {
                    showSettingsSheet = false
                    showAboutDialog = true
                },
                onSignOut = {
                    showSettingsSheet = false
                    performGoogleSignOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { viewModel.onUserSignOut() }
                    )
                },
                onDismiss = { showSettingsSheet = false }
            )
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }
}
