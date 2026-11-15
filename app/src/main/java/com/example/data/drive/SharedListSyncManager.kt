package com.example.data.drive

import android.content.Context
import com.example.data.model.ListMember
import com.example.data.model.SharedTaskItem
import com.example.data.model.SharedTodoListFile
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class SyncStatus {
    IDLE, SYNCING, SYNCED, ERROR
}

data class SharedListState(
    val file: SharedTodoListFile,
    val isReadOnly: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE
)

class SharedListSyncManager(
    private val context: Context,
    private val driveService: GoogleDriveService,
    private val authHelper: GoogleDriveAuthHelper
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val cacheType = Types.newParameterizedType(List::class.java, SharedTodoListFile::class.java)
    private val cacheAdapter = moshi.adapter<List<SharedTodoListFile>>(cacheType)
    private val cacheFile = File(context.filesDir, "shared_lists_cache.json")

    private val _sharedLists = MutableStateFlow<List<SharedListState>>(emptyList())
    val sharedLists: StateFlow<List<SharedListState>> = _sharedLists.asStateFlow()

    init {
        loadCacheFromDisk()
    }

    private fun loadCacheFromDisk() {
        try {
            if (!cacheFile.exists()) return
            val json = cacheFile.readText()
            val items = cacheAdapter.fromJson(json).orEmpty()
                .filter { it.id.isNotBlank() }
                .distinctBy { it.id }
            val currentEmail = authHelper.getConnectedAccount()?.email.orEmpty()
            _sharedLists.value = items.map { file ->
                val isReadOnly = file.members.any {
                    it.email.equals(currentEmail, ignoreCase = true) && it.role == "reader"
                }
                SharedListState(file = file, isReadOnly = isReadOnly, syncStatus = SyncStatus.IDLE)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveCacheToDisk(states: List<SharedListState>) {
        try {
            val files = states.map { it.file }.filter { it.id.isNotBlank() }.distinctBy { it.id }
            cacheFile.writeText(cacheAdapter.toJson(files))
        } catch (_: Exception) {
        }
    }

    fun clearAllCaches() {
        _sharedLists.value = emptyList()
        try {
            if (cacheFile.exists()) cacheFile.delete()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun mergeListFiles(
            existing: List<SharedTodoListFile>,
            incoming: List<SharedTodoListFile>
        ): List<SharedTodoListFile> {
            val byId = LinkedHashMap<String, SharedTodoListFile>()
            for (item in existing) {
                if (item.id.isNotBlank()) byId[item.id] = item
            }
            for (item in incoming) {
                if (item.id.isBlank()) continue
                val current = byId[item.id]
                if (current == null || item.updatedAtEpoch >= current.updatedAtEpoch) {
                    byId[item.id] = item
                }
            }
            return byId.values.toList()
        }
    }

    private suspend fun withFreshToken(
        block: suspend (token: String, email: String) -> DriveApiResult<Unit>
    ): Result<Unit> {
        val authRes = authHelper.getAccessToken()
        if (authRes !is DriveAuthResult.Success) {
            return Result.failure(IllegalStateException("Google Drive is not connected yet."))
        }
        val email = authRes.account.email.orEmpty()
        return when (val first = block(authRes.accessToken, email)) {
            is DriveApiResult.Success -> Result.success(Unit)
            is DriveApiResult.HttpError -> {
                if (first.code == 401) {
                    authHelper.invalidateToken(authRes.accessToken)
                    val retryAuth = authHelper.getAccessToken(authRes.account)
                    if (retryAuth is DriveAuthResult.Success) {
                        when (val second = block(retryAuth.accessToken, email)) {
                            is DriveApiResult.Success -> Result.success(Unit)
                            is DriveApiResult.HttpError -> Result.failure(Exception("Drive error (${second.code}): ${second.message}"))
                            is DriveApiResult.NetworkError -> Result.failure(Exception(second.message))
                        }
                    } else {
                        Result.failure(Exception("Google Drive authorization expired. Please reconnect."))
                    }
                } else {
                    Result.failure(Exception("Drive error (${first.code}): ${first.message}"))
                }
            }
            is DriveApiResult.NetworkError -> Result.failure(Exception(first.message))
        }
    }

    suspend fun refreshFromDrive(): Result<Unit> {
        return withFreshToken { token, currentEmail ->
            when (val listRes = driveService.listSharedTaskFiles(token)) {
                is DriveApiResult.Success -> {
                    val downloaded = mutableListOf<SharedListState>()
                    for (summary in listRes.data) {
                        when (val dl = driveService.downloadListFile(token, summary.id)) {
                            is DriveApiResult.Success -> {
                                val membersResult = driveService.fetchPermissions(token, summary.id)
                                val members = (membersResult as? DriveApiResult.Success)?.data ?: dl.data.members
                                val resolvedFile = dl.data.copy(
                                    id = summary.id,
                                    ownerEmail = dl.data.ownerEmail.ifBlank { summary.ownerEmail },
                                    members = members.ifEmpty { dl.data.members }
                                )
                                val isReadOnly = !summary.canEdit || resolvedFile.members.any {
                                    it.email.equals(currentEmail, ignoreCase = true) && it.role == "reader"
                                }
                                downloaded.add(
                                    SharedListState(
                                        file = resolvedFile,
                                        isReadOnly = isReadOnly,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                            }
                            else -> Unit
                        }
                    }
                    val mergedFiles = mergeListFiles(
                        existing = _sharedLists.value.map { it.file },
                        incoming = downloaded.map { it.file }
                    )
                    val downloadedMap = downloaded.associateBy { it.file.id }
                    val updatedStates = mergedFiles.map { file ->
                        downloadedMap[file.id] ?: SharedListState(file = file, syncStatus = SyncStatus.IDLE)
                    }
                    _sharedLists.value = updatedStates
                    saveCacheToDisk(updatedStates)
                    DriveApiResult.Success(Unit)
                }
                is DriveApiResult.HttpError -> listRes
                is DriveApiResult.NetworkError -> listRes
            }
        }
    }

    suspend fun shareLocalListToDrive(
        localList: TodoList,
        localTasks: List<TaskItem>,
        ownerEmail: String
    ): Result<SharedTodoListFile> {
        val now = System.currentTimeMillis()
        val sharedTasks = localTasks.map { task ->
            SharedTaskItem(
                id = task.id,
                title = task.title,
                notes = task.notes,
                priority = task.priority,
                isCompleted = task.isCompleted,
                dueDateEpoch = task.dueDateEpoch,
                reminderEpoch = task.reminderEpoch,
                reminderSoundKey = task.reminderSoundKey,
                category = task.category,
                updatedByEmail = ownerEmail,
                updatedAtEpoch = now
            )
        }
        val initialFile = SharedTodoListFile(
            id = "",
            title = localList.title,
            iconKey = localList.iconKey,
            colorHex = localList.colorHex,
            ownerEmail = ownerEmail,
            updatedAtEpoch = now,
            updatedByEmail = ownerEmail,
            tasks = sharedTasks,
            members = if (ownerEmail.isNotBlank()) listOf(ListMember(email = ownerEmail, role = "owner")) else emptyList()
        )

        var createdFile: SharedTodoListFile? = null
        val result = withFreshToken { token, _ ->
            when (val res = driveService.createSharedListFile(token, initialFile)) {
                is DriveApiResult.Success -> {
                    createdFile = res.data
                    val newState = SharedListState(file = res.data, isReadOnly = false, syncStatus = SyncStatus.SYNCED)
                    val updatedList = (_sharedLists.value.filterNot { it.file.id == res.data.id } + newState)
                        .distinctBy { it.file.id }
                    _sharedLists.value = updatedList
                    saveCacheToDisk(updatedList)
                    DriveApiResult.Success(Unit)
                }
                is DriveApiResult.HttpError -> res
                is DriveApiResult.NetworkError -> res
            }
        }
        return result.map { createdFile ?: initialFile }
    }

    suspend fun inviteMember(
        fileId: String,
        targetEmail: String,
        role: String
    ): Result<SharedTodoListFile> {
        val trimmedEmail = targetEmail.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        val currentState = _sharedLists.value.find { it.file.id == fileId }
            ?: return Result.failure(IllegalStateException("Shared list not found."))

        var updatedResultFile = currentState.file
        val result = withFreshToken { token, actorEmail ->
            when (val permRes = driveService.addPermission(token, fileId, trimmedEmail, role, currentState.file.title)) {
                is DriveApiResult.Success -> {
                    val existingMembers = currentState.file.members.filterNot {
                        it.email.equals(trimmedEmail, ignoreCase = true)
                    }
                    val newMember = ListMember(email = trimmedEmail, role = if (role == "reader") "reader" else "writer")
                    val nextFile = currentState.file.copy(
                        members = existingMembers + newMember,
                        updatedAtEpoch = System.currentTimeMillis(),
                        updatedByEmail = actorEmail
                    )
                    when (val saveRes = driveService.updateSharedListFile(token, fileId, nextFile)) {
                        is DriveApiResult.Success -> {
                            updatedResultFile = saveRes.data
                            val nextStates = _sharedLists.value.map {
                                if (it.file.id == fileId) it.copy(file = saveRes.data, syncStatus = SyncStatus.SYNCED) else it
                            }
                            _sharedLists.value = nextStates
                            saveCacheToDisk(nextStates)
                            DriveApiResult.Success(Unit)
                        }
                        is DriveApiResult.HttpError -> saveRes
                        is DriveApiResult.NetworkError -> saveRes
                    }
                }
                is DriveApiResult.HttpError -> permRes
                is DriveApiResult.NetworkError -> permRes
            }
        }
        return result.map { updatedResultFile }
    }

    suspend fun updateSharedListContent(updatedFile: SharedTodoListFile): Result<Unit> {
        val normalized = updatedFile.copy(updatedAtEpoch = System.currentTimeMillis())
        val optimistic = _sharedLists.value.map {
            if (it.file.id == normalized.id) it.copy(file = normalized, syncStatus = SyncStatus.SYNCING) else it
        }
        _sharedLists.value = optimistic
        saveCacheToDisk(optimistic)

        return withFreshToken { token, _ ->
            when (val res = driveService.updateSharedListFile(token, normalized.id, normalized)) {
                is DriveApiResult.Success -> {
                    val synced = _sharedLists.value.map {
                        if (it.file.id == normalized.id) it.copy(file = res.data, syncStatus = SyncStatus.SYNCED) else it
                    }
                    _sharedLists.value = synced
                    saveCacheToDisk(synced)
                    DriveApiResult.Success(Unit)
                }
                is DriveApiResult.HttpError -> {
                    _sharedLists.value = _sharedLists.value.map {
                        if (it.file.id == normalized.id) it.copy(syncStatus = SyncStatus.ERROR) else it
                    }
                    res
                }
                is DriveApiResult.NetworkError -> {
                    _sharedLists.value = _sharedLists.value.map {
                        if (it.file.id == normalized.id) it.copy(syncStatus = SyncStatus.ERROR) else it
                    }
                    res
                }
            }
        }
    }
}
