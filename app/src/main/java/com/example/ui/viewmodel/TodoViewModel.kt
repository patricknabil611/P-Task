package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiTaskParser
import com.example.ai.ParsedTaskResult
import com.example.backup.BackupRestoreManager
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.data.model.UserSettings
import com.example.data.repository.TodoRepository
import com.example.notification.NotificationHelper
import com.example.widget.OmniTaskWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface NlpUiState {
    data object Idle : NlpUiState
    data object Loading : NlpUiState
    data class Success(val parsed: ParsedTaskResult) : NlpUiState
    data class Error(val message: String) : NlpUiState
}

data class UndoAction(
    val message: String,
    val onUndo: () -> Unit
)

class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TodoRepository.create(application)

    // Map of taskId -> target isCompleted state for instant optimistic UI
    private val _optimisticToggles = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val listsState: StateFlow<UiState<List<TodoList>>> = repository.observeLists()
        .map<List<TodoList>, UiState<List<TodoList>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load lists")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val allTasksState: StateFlow<UiState<List<TaskItem>>> = combine(
        repository.observeTasks(),
        _optimisticToggles
    ) { tasks, optimisticMap ->
        val effectiveTasks = if (optimisticMap.isEmpty()) {
            tasks
        } else {
            tasks.map { task ->
                val optCompleted = optimisticMap[task.id]
                if (optCompleted != null) {
                    val completedAt = if (optCompleted) {
                        if (task.completedAt > 0L) task.completedAt else System.currentTimeMillis()
                    } else {
                        0L
                    }
                    task.copy(isCompleted = optCompleted, completedAt = completedAt)
                } else {
                    task
                }
            }
        }
        // Keep home widget updated with active non-completed tasks
        OmniTaskWidgetProvider.updateAllWidgets(getApplication(), effectiveTasks)
        UiState.Success(effectiveTasks) as UiState<List<TaskItem>>
    }.catch { emit(UiState.Error(it.message ?: "Failed to load tasks")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val settingsState: StateFlow<UserSettings?> = repository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>("All")
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _isBiometricUnlocked = MutableStateFlow(false)
    val isBiometricUnlocked: StateFlow<Boolean> = _isBiometricUnlocked.asStateFlow()

    private val _nlpState = MutableStateFlow<NlpUiState>(NlpUiState.Idle)
    val nlpState: StateFlow<NlpUiState> = _nlpState.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: StateFlow<UndoAction?> = _undoAction.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.seedDefaultListsIfEmpty()
            } catch (e: Exception) {
                Log.e("TodoViewModel", "Error seeding default lists", e)
            }
        }
    }

    fun selectList(listId: String?) {
        _selectedListId.value = listId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setBiometricUnlocked(unlocked: Boolean) {
        _isBiometricUnlocked.value = unlocked
    }

    fun clearSnackMessage() {
        _snackMessage.value = null
    }

    fun showSnack(msg: String) {
        _snackMessage.value = msg
    }

    fun clearUndoAction() {
        _undoAction.value = null
    }

    fun addTodoList(list: TodoList, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.addTodoList(list)
                showSnack("List created: ${list.title}")
                onComplete()
            } catch (e: Exception) {
                showSnack("Error creating list: ${e.message}")
            }
        }
    }

    fun updateTodoList(list: TodoList, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateTodoList(list)
                showSnack("List updated: ${list.title}")
                onComplete()
            } catch (e: Exception) {
                showSnack("Error updating list: ${e.message}")
            }
        }
    }

    fun deleteTodoList(listId: String, listTitle: String) {
        viewModelScope.launch {
            try {
                repository.deleteTodoList(listId)
                if (_selectedListId.value == listId) {
                    _selectedListId.value = null
                }
                showSnack("Deleted list: $listTitle")
            } catch (e: Exception) {
                showSnack("Error deleting list: ${e.message}")
            }
        }
    }

    fun addTask(task: TaskItem, listTitle: String = "P Task", onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val taskId = repository.addTask(task)
                if (task.reminderEpoch > System.currentTimeMillis()) {
                    NotificationHelper.scheduleReminder(
                        context = getApplication(),
                        taskId = taskId,
                        taskTitle = task.title,
                        listTitle = listTitle,
                        reminderEpoch = task.reminderEpoch,
                        soundKey = task.reminderSound
                    )
                }
                showSnack("Task added: ${task.title}")
                onComplete()
            } catch (e: Exception) {
                showSnack("Error adding task: ${e.message}")
            }
        }
    }

    fun updateTask(task: TaskItem, listTitle: String = "P Task", onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateTask(task)
                if (task.reminderEpoch > System.currentTimeMillis() && !task.isCompleted) {
                    NotificationHelper.scheduleReminder(
                        context = getApplication(),
                        taskId = task.id,
                        taskTitle = task.title,
                        listTitle = listTitle,
                        reminderEpoch = task.reminderEpoch,
                        soundKey = task.reminderSound
                    )
                } else {
                    NotificationHelper.cancelReminder(getApplication(), task.id)
                }
                showSnack("Task updated")
                onComplete()
            } catch (e: Exception) {
                showSnack("Error updating task: ${e.message}")
            }
        }
    }

    fun toggleTask(task: TaskItem) {
        val targetCompleted = !task.isCompleted
        // Apply optimistic toggle immediately
        _optimisticToggles.value = _optimisticToggles.value + (task.id to targetCompleted)

        viewModelScope.launch {
            try {
                repository.toggleTaskCompletion(task)
                // Remove optimistic override once persisted
                _optimisticToggles.value = _optimisticToggles.value - task.id

                if (targetCompleted) {
                    NotificationHelper.cancelReminder(getApplication(), task.id)
                    _undoAction.value = UndoAction(
                        message = "Task completed",
                        onUndo = {
                            toggleTask(task.copy(isCompleted = true))
                        }
                    )
                } else {
                    // Task uncompleted - reschedule reminder if still in future
                    if (task.reminderEpoch > System.currentTimeMillis()) {
                        val currentLists = (listsState.value as? UiState.Success)?.data ?: emptyList()
                        val listTitle = currentLists.firstOrNull { it.id == task.listId }?.title ?: "P Task"
                        NotificationHelper.scheduleReminder(
                            context = getApplication(),
                            taskId = task.id,
                            taskTitle = task.title,
                            listTitle = listTitle,
                            reminderEpoch = task.reminderEpoch,
                            soundKey = task.reminderSound
                        )
                    }
                }
            } catch (e: Exception) {
                // Revert optimistic toggle
                _optimisticToggles.value = _optimisticToggles.value - task.id
                showSnack("Error updating status: ${e.message}")
            }
        }
    }

    fun clearCompletedTasks(listId: String) {
        viewModelScope.launch {
            try {
                repository.clearCompletedTasksInList(listId)
                showSnack("Cleared completed tasks")
            } catch (e: Exception) {
                showSnack("Error clearing completed: ${e.message}")
            }
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            try {
                repository.deleteTask(task.id)
                NotificationHelper.cancelReminder(getApplication(), task.id)
                showSnack("Task deleted")
            } catch (e: Exception) {
                showSnack("Error deleting task: ${e.message}")
            }
        }
    }

    fun updateSettings(themeMode: String, biometricEnabled: Boolean, defaultSound: String) {
        viewModelScope.launch {
            try {
                val current = settingsState.value ?: UserSettings()
                val updated = current.copy(
                    themeMode = themeMode,
                    biometricLockEnabled = biometricEnabled,
                    defaultSound = defaultSound,
                    categories = current.getResolvedCategories()
                )
                repository.updateSettings(updated)
                showSnack("Settings saved")
            } catch (e: Exception) {
                showSnack("Error saving settings: ${e.message}")
            }
        }
    }

    // Category Management
    fun addCategory(rawName: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "Category name cannot be empty")
            return
        }
        if (trimmed.length > 50) {
            onResult(false, "Category name must be 50 characters or less")
            return
        }
        val currentCategories = (settingsState.value ?: UserSettings()).getResolvedCategories()
        if (currentCategories.any { it.equals(trimmed, ignoreCase = true) }) {
            onResult(false, "Category \"$trimmed\" already exists")
            return
        }

        val updatedList = currentCategories + trimmed
        val currentSettings = settingsState.value ?: UserSettings()
        viewModelScope.launch {
            try {
                repository.updateSettings(currentSettings.copy(categories = updatedList))
                showSnack("Category added: $trimmed")
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to add category")
            }
        }
    }

    fun renameCategory(oldCategory: String, rawNewName: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = rawNewName.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "Category name cannot be empty")
            return
        }
        if (trimmed.length > 50) {
            onResult(false, "Category name must be 50 characters or less")
            return
        }
        val currentCategories = (settingsState.value ?: UserSettings()).getResolvedCategories()
        if (!oldCategory.equals(trimmed, ignoreCase = true) &&
            currentCategories.any { it.equals(trimmed, ignoreCase = true) }
        ) {
            onResult(false, "Category \"$trimmed\" already exists")
            return
        }

        val updatedList = currentCategories.map { if (it == oldCategory) trimmed else it }
        val currentSettings = settingsState.value ?: UserSettings()
        viewModelScope.launch {
            try {
                repository.renameCategoryAcrossLists(oldCategory, trimmed)
                repository.updateSettings(currentSettings.copy(categories = updatedList))
                showSnack("Renamed category to $trimmed")
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to rename category")
            }
        }
    }

    fun deleteCategory(categoryToDelete: String, replacementCategory: String, onResult: (Boolean, String?) -> Unit) {
        val currentCategories = (settingsState.value ?: UserSettings()).getResolvedCategories()
        if (currentCategories.size <= 1) {
            onResult(false, "You must keep at least one category")
            return
        }
        val updatedList = currentCategories.filter { it != categoryToDelete }
        val currentSettings = settingsState.value ?: UserSettings()
        viewModelScope.launch {
            try {
                repository.renameCategoryAcrossLists(categoryToDelete, replacementCategory)
                repository.updateSettings(currentSettings.copy(categories = updatedList))
                showSnack("Deleted \"$categoryToDelete\" (reassigned to \"$replacementCategory\")")
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to delete category")
            }
        }
    }

    fun moveCategory(category: String, up: Boolean) {
        val currentCategories = (settingsState.value ?: UserSettings()).getResolvedCategories().toMutableList()
        val index = currentCategories.indexOf(category)
        if (index == -1) return
        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= currentCategories.size) return

        currentCategories.removeAt(index)
        currentCategories.add(targetIndex, category)
        val currentSettings = settingsState.value ?: UserSettings()
        viewModelScope.launch {
            try {
                repository.updateSettings(currentSettings.copy(categories = currentCategories))
            } catch (e: Exception) {
                showSnack("Error reordering categories: ${e.message}")
            }
        }
    }

    fun resetCategoriesToDefaults() {
        val currentSettings = settingsState.value ?: UserSettings()
        viewModelScope.launch {
            try {
                repository.updateSettings(currentSettings.copy(categories = UserSettings.DEFAULT_CATEGORIES))
                showSnack("Reset categories to default")
            } catch (e: Exception) {
                showSnack("Error resetting categories: ${e.message}")
            }
        }
    }

    // Natural Language Voice & Text Task Processing
    fun parseNlpInput(input: String, currentLists: List<TodoList>) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _nlpState.value = NlpUiState.Loading
            val listTitles = currentLists.map { it.title }
            val categories = (settingsState.value ?: UserSettings()).getResolvedCategories()
            val result = GeminiTaskParser.parseNaturalLanguageTask(input, listTitles, categories)
            result.onSuccess {
                _nlpState.value = NlpUiState.Success(it)
            }.onFailure {
                _nlpState.value = NlpUiState.Error(it.message ?: "Could not parse task")
            }
        }
    }

    fun resetNlpState() {
        _nlpState.value = NlpUiState.Idle
    }

    // Backup & Restore
    fun exportBackupJson(onReady: (String) -> Unit) {
        val lists = (listsState.value as? UiState.Success)?.data ?: emptyList()
        val tasks = (allTasksState.value as? UiState.Success)?.data ?: emptyList()
        val json = BackupRestoreManager.exportToJson(lists, tasks)
        onReady(json)
    }

    fun importBackupJson(jsonString: String, onComplete: (Boolean, String) -> Unit) {
        val parsed = BackupRestoreManager.parseBackup(jsonString)
        parsed.onSuccess { (importedLists, importedTasks) ->
            viewModelScope.launch {
                try {
                    val currentLists = (listsState.value as? UiState.Success)?.data ?: emptyList()
                    val titleToIdMap = currentLists.associate { it.title to it.id }.toMutableMap()

                    // Add lists that don't exist
                    for (list in importedLists) {
                        if (!titleToIdMap.containsKey(list.title)) {
                            val newId = repository.addTodoList(list)
                            titleToIdMap[list.title] = newId
                        }
                    }

                    // Add tasks
                    for (task in importedTasks) {
                        repository.addTask(task)
                    }

                    showSnack("Restored ${importedLists.size} lists and ${importedTasks.size} tasks!")
                    onComplete(true, "Restore successful")
                } catch (e: Exception) {
                    onComplete(false, e.message ?: "Failed to restore backup")
                }
            }
        }.onFailure {
            onComplete(false, "Invalid backup format: ${it.message}")
        }
    }
}
