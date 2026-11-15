package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiTaskParser
import com.example.ai.ParsedTaskResult
import com.example.backup.BackupRestoreManager
import com.example.data.drive.DriveAuthResult
import com.example.data.drive.GoogleDriveAuthHelper
import com.example.data.drive.GoogleDriveService
import com.example.data.drive.SharedListState
import com.example.data.drive.SharedListSyncManager
import com.example.data.model.DEFAULT_CATEGORIES
import com.example.data.model.SharedTaskItem
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.data.model.UserSettings
import com.example.data.repository.TodoRepository
import com.example.notification.NotificationHelper
import com.example.widget.OmniTaskWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface NlpUiState {
    data object Idle : NlpUiState
    data object Parsing : NlpUiState
    data class Ready(val parsed: ParsedTaskResult) : NlpUiState
    data class Error(val message: String) : NlpUiState
}

sealed interface UndoAction {
    data class DeletedTask(
        val task: TaskItem,
        val eventId: Long = System.nanoTime()
    ) : UndoAction
}

class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = TodoRepository.create(appContext)
    private val driveAuthHelper = GoogleDriveAuthHelper(appContext)
    private val driveService = GoogleDriveService()
    private val syncManager = SharedListSyncManager(appContext, driveService, driveAuthHelper)
    private val geminiTaskParser = GeminiTaskParser()
    private val settingsPrefs = appContext.getSharedPreferences("ptask_user_settings", android.content.Context.MODE_PRIVATE)

    val listsState: StateFlow<UiState<List<TodoList>>> = repository.observeLists()
        .map<List<TodoList>, UiState<List<TodoList>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).localizedMessage ?: "Failed to load lists"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tasksState: StateFlow<UiState<List<TaskItem>>> = repository.observeTasks()
        .map<List<TaskItem>, UiState<List<TaskItem>>> { tasks ->
            OmniTaskWidgetProvider.updateAllWidgets(appContext, tasks)
            UiState.Success(tasks)
        }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).localizedMessage ?: "Failed to load tasks"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _settingsState = MutableStateFlow(loadLocalSettings())
    val settingsState: StateFlow<UserSettings> = _settingsState.asStateFlow()

    val sharedLists: StateFlow<List<SharedListState>> = syncManager.sharedLists

    val allCombinedTasks: StateFlow<List<TaskItem>> = combine(tasksState, sharedLists) { localState, shared ->
        val local = (localState as? UiState.Success)?.data.orEmpty()
        val remote = shared.flatMap { it.file.toTaskItems() }
        (local + remote).distinctBy { "${it.listId}_${it.id}" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isDriveConnected = MutableStateFlow(driveAuthHelper.isConnected())
    val isDriveConnected: StateFlow<Boolean> = _isDriveConnected.asStateFlow()

    private val _isRefreshingDrive = MutableStateFlow(false)
    val isRefreshingDrive: StateFlow<Boolean> = _isRefreshingDrive.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _driveConsentIntent = MutableStateFlow<Intent?>(null)
    val driveConsentIntent: StateFlow<Intent?> = _driveConsentIntent.asStateFlow()

    private val _nlpState = MutableStateFlow<NlpUiState>(NlpUiState.Idle)
    val nlpState: StateFlow<NlpUiState> = _nlpState.asStateFlow()

    private val _lastUndo = MutableStateFlow<UndoAction?>(null)
    val lastUndo: StateFlow<UndoAction?> = _lastUndo.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultListIfEmpty()
        }
        viewModelScope.launch {
            repository.observeSettings()
                .catch { /* keep local settings if offline/error */ }
                .collect { remote ->
                    if (remote != null) {
                        _settingsState.value = remote
                        saveLocalSettings(remote)
                    }
                }
        }
        if (_isDriveConnected.value) {
            refreshSharedLists()
        }
    }

    private fun settingsKeyPrefix(): String {
        val uid = Firebase.auth.currentUser?.uid ?: "guest"
        return "${uid}_"
    }

    private fun loadLocalSettings(): UserSettings {
        val p = settingsPrefs
        val prefix = settingsKeyPrefix()
        val catsRaw = p.getString("${prefix}categories", null)
        val cats = catsRaw?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_CATEGORIES
        val defaultDark = p.getBoolean("last_dark_theme", false)
        return UserSettings(
            userId = Firebase.auth.currentUser?.uid.orEmpty(),
            defaultSoundKey = p.getString("${prefix}sound_key", "chime") ?: "chime",
            defaultReminderHour = p.getInt("${prefix}rem_hour", 9).coerceIn(0, 23),
            defaultReminderMinute = p.getInt("${prefix}rem_min", 0).coerceIn(0, 59),
            notificationDescription = p.getString("${prefix}notif_desc", "Scheduled P Task reminder")
                ?: "Scheduled P Task reminder",
            useDarkTheme = p.getBoolean("${prefix}dark_theme", defaultDark),
            biometricEnabled = p.getBoolean("${prefix}biometric", true),
            customCategories = cats
        )
    }

    private fun saveLocalSettings(settings: UserSettings) {
        val prefix = settingsKeyPrefix()
        settingsPrefs.edit()
            .putString("${prefix}sound_key", settings.defaultSoundKey)
            .putInt("${prefix}rem_hour", settings.defaultReminderHour.coerceIn(0, 23))
            .putInt("${prefix}rem_min", settings.defaultReminderMinute.coerceIn(0, 59))
            .putString("${prefix}notif_desc", settings.notificationDescription)
            .putBoolean("${prefix}dark_theme", settings.useDarkTheme)
            .putBoolean("last_dark_theme", settings.useDarkTheme)
            .putBoolean("${prefix}biometric", settings.biometricEnabled)
            .putString("${prefix}categories", settings.customCategories.joinToString("\n"))
            .apply()
        NotificationHelper.saveNotificationDescription(appContext, settings.notificationDescription)
    }

    fun getCurrentUserEmail(): String {
        return driveAuthHelper.getConnectedAccount()?.email?.takeIf { it.isNotBlank() }
            ?: Firebase.auth.currentUser?.email.orEmpty()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearDriveConsentIntent() {
        _driveConsentIntent.value = null
    }

    fun requestDriveConnect() {
        viewModelScope.launch {
            when (val res = driveAuthHelper.getAccessToken()) {
                is DriveAuthResult.Success -> {
                    _isDriveConnected.value = true
                    refreshSharedLists()
                }
                is DriveAuthResult.NeedsConsent -> {
                    _driveConsentIntent.value = res.consentIntent
                }
                is DriveAuthResult.Error -> {
                    _statusMessage.value = res.message
                }
            }
        }
    }

    fun onDriveSignInResult(data: Intent?) {
        val res = driveAuthHelper.handleSignInResultIntent(data)
        res.onSuccess {
            _isDriveConnected.value = driveAuthHelper.isConnected()
            if (_isDriveConnected.value) {
                refreshSharedLists()
            } else {
                requestDriveConnect()
            }
        }.onFailure { err ->
            _isDriveConnected.value = driveAuthHelper.isConnected()
            _statusMessage.value = err.localizedMessage ?: "Google Drive sign-in was cancelled."
        }
    }

    fun onDriveConsentCancelled() {
        _driveConsentIntent.value = null
        _isDriveConnected.value = driveAuthHelper.isConnected()
    }

    fun refreshSharedLists() {
        viewModelScope.launch {
            _isRefreshingDrive.value = true
            val res = syncManager.refreshFromDrive()
            res.onFailure { err ->
                _statusMessage.value = err.localizedMessage
            }
            _isRefreshingDrive.value = false
        }
    }

    fun onUserSignOut() {
        syncManager.clearAllCaches()
        driveAuthHelper.signOut()
        _isDriveConnected.value = false
    }

    fun createList(title: String, iconKey: String, colorHex: String, isLocked: Boolean) {
        viewModelScope.launch {
            repository.createList(
                TodoList(
                    title = title,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    isLocked = isLocked,
                    ownerEmail = getCurrentUserEmail()
                )
            ).onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun updateList(list: TodoList) {
        viewModelScope.launch {
            if (list.isShared) {
                val existing = sharedLists.value.find { it.file.id == list.id } ?: return@launch
                val updated = existing.file.copy(
                    title = list.title,
                    iconKey = list.iconKey,
                    colorHex = list.colorHex,
                    updatedByEmail = getCurrentUserEmail()
                )
                syncManager.updateSharedListContent(updated)
                    .onFailure { _statusMessage.value = it.localizedMessage }
            } else {
                repository.updateList(list)
                    .onFailure { _statusMessage.value = it.localizedMessage }
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
                .filter { it.listId == listId }
            tasks.forEach { NotificationHelper.cancelReminder(appContext, it.id) }
            repository.deleteList(listId)
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun saveTask(task: TaskItem, listTitle: String, isNew: Boolean) {
        viewModelScope.launch {
            val sharedState = sharedLists.value.find { it.file.id == task.listId }
            if (sharedState != null) {
                if (sharedState.isReadOnly) {
                    _statusMessage.value = "This shared list is view-only."
                    return@launch
                }
                val now = System.currentTimeMillis()
                val actor = getCurrentUserEmail()
                val taskId = task.id.ifBlank { UUID.randomUUID().toString() }
                val sharedItem = SharedTaskItem(
                    id = taskId,
                    title = task.title.trim(),
                    notes = task.notes,
                    priority = task.priority,
                    isCompleted = task.isCompleted,
                    dueDateEpoch = task.dueDateEpoch,
                    reminderEpoch = task.reminderEpoch,
                    reminderSoundKey = task.reminderSoundKey,
                    category = task.category,
                    updatedByEmail = actor,
                    updatedAtEpoch = now
                )
                val nextTasks = if (isNew) {
                    sharedState.file.tasks + sharedItem
                } else {
                    sharedState.file.tasks.map { if (it.id == taskId) sharedItem else it }
                }
                syncManager.updateSharedListContent(
                    sharedState.file.copy(
                        tasks = nextTasks,
                        updatedByEmail = actor,
                        updatedAtEpoch = now
                    )
                ).onFailure { _statusMessage.value = it.localizedMessage }
            } else {
                val res = if (isNew) {
                    repository.createTask(task)
                } else {
                    repository.updateTask(task).map { task.id }
                }
                res.onSuccess { savedId ->
                    if (task.reminderEpoch > System.currentTimeMillis() && !task.isCompleted) {
                        NotificationHelper.scheduleReminder(
                            appContext,
                            savedId,
                            task.title,
                            listTitle,
                            task.reminderEpoch,
                            task.reminderSoundKey,
                            settingsState.value.notificationDescription
                        )
                    } else {
                        NotificationHelper.cancelReminder(appContext, savedId)
                    }
                }.onFailure { _statusMessage.value = it.localizedMessage }
            }
        }
    }

    fun toggleTaskCompleted(task: TaskItem, listTitle: String) {
        val next = task.copy(isCompleted = !task.isCompleted)
        saveTask(next, listTitle, isNew = false)
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            NotificationHelper.cancelReminder(appContext, task.id)
            val sharedState = sharedLists.value.find { it.file.id == task.listId }
            if (sharedState != null) {
                if (sharedState.isReadOnly) {
                    _statusMessage.value = "This shared list is view-only."
                    return@launch
                }
                val actor = getCurrentUserEmail()
                val nextTasks = sharedState.file.tasks.filterNot { it.id == task.id }
                syncManager.updateSharedListContent(
                    sharedState.file.copy(
                        tasks = nextTasks,
                        updatedByEmail = actor,
                        updatedAtEpoch = System.currentTimeMillis()
                    )
                ).onSuccess {
                    _lastUndo.value = UndoAction.DeletedTask(task)
                }.onFailure { _statusMessage.value = it.localizedMessage }
            } else {
                repository.deleteTask(task.id)
                    .onSuccess { _lastUndo.value = UndoAction.DeletedTask(task) }
                    .onFailure { _statusMessage.value = it.localizedMessage }
            }
        }
    }

    fun undoLastDelete(listTitle: String) {
        val action = _lastUndo.value ?: return
        _lastUndo.value = null
        when (action) {
            is UndoAction.DeletedTask -> saveTask(action.task, listTitle, isNew = true)
        }
    }

    fun clearUndo() {
        _lastUndo.value = null
    }

    suspend fun shareListAndInviteOneStep(
        list: TodoList,
        targetEmail: String,
        role: String
    ): Result<String> {
        val trimmedEmail = targetEmail.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Enter a valid email address."))
        }
        val authRes = driveAuthHelper.getAccessToken()
        if (authRes !is DriveAuthResult.Success) {
            if (authRes is DriveAuthResult.NeedsConsent) {
                _driveConsentIntent.value = authRes.consentIntent
            }
            return Result.failure(IllegalStateException("Connect Google Drive to share this list."))
        }
        _isDriveConnected.value = true
        val ownerEmail = authRes.account.email.orEmpty().ifBlank { getCurrentUserEmail() }

        val targetDriveId: String
        if (list.isShared && list.driveFileId.isNotBlank()) {
            targetDriveId = list.driveFileId
        } else {
            val localTasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
                .filter { it.listId == list.id }
            val uploadRes = syncManager.shareLocalListToDrive(list, localTasks, ownerEmail)
            if (uploadRes.isFailure) {
                return Result.failure(uploadRes.exceptionOrNull() ?: Exception("Upload failed"))
            }
            targetDriveId = uploadRes.getOrThrow().id
            if (!list.isShared) {
                repository.deleteList(list.id)
            }
        }

        val inviteRes = syncManager.inviteMember(targetDriveId, trimmedEmail, role)
        return inviteRes.map {
            "Shared with $trimmedEmail! Notification email sent and synced to their P Task app."
        }
    }

    fun parseVoiceOrTextWithAi(input: String) {
        viewModelScope.launch {
            _nlpState.value = NlpUiState.Parsing
            val cats = settingsState.value.customCategories.ifEmpty { DEFAULT_CATEGORIES }
            val res = geminiTaskParser.parseTaskInput(input, cats)
            _nlpState.value = res.fold(
                onSuccess = { NlpUiState.Ready(it) },
                onFailure = { NlpUiState.Error(it.localizedMessage ?: "Unable to parse task") }
            )
        }
    }

    fun resetNlpState() {
        _nlpState.value = NlpUiState.Idle
    }

    fun commitParsedTask(listId: String, listTitle: String, parsed: ParsedTaskResult) {
        val currentSettings = settingsState.value
        val dueEpoch = parsed.dueOffsetDays?.let { days ->
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, days)
                set(Calendar.HOUR_OF_DAY, currentSettings.defaultReminderHour.coerceIn(0, 23))
                set(Calendar.MINUTE, currentSettings.defaultReminderMinute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
            }.timeInMillis
        } ?: 0L
        saveTask(
            TaskItem(
                listId = listId,
                title = parsed.title,
                notes = parsed.notes,
                priority = parsed.priority,
                category = parsed.category,
                dueDateEpoch = dueEpoch,
                reminderSoundKey = currentSettings.defaultSoundKey
            ),
            listTitle = listTitle,
            isNew = true
        )
        _nlpState.value = NlpUiState.Idle
    }

    fun updateSettings(settings: UserSettings) {
        _settingsState.value = settings
        saveLocalSettings(settings)
        viewModelScope.launch {
            repository.saveSettings(settings)
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim().take(60)
        if (trimmed.isEmpty()) return
        val current = settingsState.value
        val cats = current.customCategories.ifEmpty { DEFAULT_CATEGORIES }
        if (cats.any { it.equals(trimmed, ignoreCase = true) }) {
            _statusMessage.value = "Category \"$trimmed\" already exists."
            return
        }
        updateSettings(current.copy(customCategories = cats + trimmed))
    }

    fun updateCategory(oldName: String, newName: String) {
        val trimmed = newName.trim().take(60)
        if (trimmed.isEmpty() || trimmed == oldName) return
        val current = settingsState.value
        val cats = current.customCategories.ifEmpty { DEFAULT_CATEGORIES }
        if (cats.any { !it.equals(oldName, ignoreCase = false) && it.equals(trimmed, ignoreCase = true) }) {
            _statusMessage.value = "Category \"$trimmed\" already exists."
            return
        }
        val updatedCats = cats.map { if (it == oldName) trimmed else it }.distinct()
        updateSettings(current.copy(customCategories = updatedCats))
        viewModelScope.launch {
            repository.replaceCategoryInTasks(oldName, trimmed)
        }
    }

    fun deleteCategory(name: String) {
        val current = settingsState.value
        val cats = current.customCategories.ifEmpty { DEFAULT_CATEGORIES }
        if (cats.size <= 1) {
            _statusMessage.value = "Keep at least one category."
            return
        }
        val remaining = cats.filterNot { it == name }
        val fallback = remaining.firstOrNull() ?: "General"
        updateSettings(current.copy(customCategories = remaining))
        viewModelScope.launch {
            repository.replaceCategoryInTasks(name, fallback)
        }
    }

    fun exportBackup(uri: Uri) {
        val lists = (listsState.value as? UiState.Success)?.data.orEmpty()
        val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
        BackupRestoreManager.exportToUri(appContext, uri, lists, tasks)
            .onSuccess { count -> _statusMessage.value = "Exported $count tasks successfully." }
            .onFailure { _statusMessage.value = it.localizedMessage ?: "Export failed." }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            BackupRestoreManager.importFromUri(appContext, uri)
                .onSuccess { payload ->
                    repository.restoreSnapshot(payload.lists, payload.tasks)
                        .onSuccess {
                            _statusMessage.value = "Restored ${payload.lists.size} lists and ${payload.tasks.size} tasks."
                        }
                        .onFailure { _statusMessage.value = it.localizedMessage ?: "Restore failed." }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Import failed." }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return TodoViewModel(application) as T
        }
    }
}
