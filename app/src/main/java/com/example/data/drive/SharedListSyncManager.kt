package com.example.data.drive

import android.content.Context
import android.util.Log
import com.example.data.model.ListMember
import com.example.data.model.SharedTaskItem
import com.example.data.model.SharedTodoListFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE,
    ACCESS_REVOKED,
    DELETED_BY_OWNER,
    ERROR
}

data class SharedListState(
    val file: SharedTodoListFile,
    val summary: DriveFileSummary?,
    val isReadOnly: Boolean = false,
    val hasPendingUpload: Boolean = false
)

class SharedListSyncManager(
    private val context: Context,
    private val driveService: GoogleDriveService = GoogleDriveService()
) {
    companion object {
        private const val TAG = "SharedListSyncManager"
        private const val DIR_SHARED_CACHE = "shared_lists"
        private const val POLL_INTERVAL_MS = 20_000L // 20 seconds
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L
    }

    private val cacheDir: File by lazy {
        File(context.filesDir, DIR_SHARED_CACHE).apply { mkdirs() }
    }

    // Map of listId -> SharedListState
    private val _sharedListsMap = MutableStateFlow<Map<String, SharedListState>>(emptyMap())
    val sharedListsMap: StateFlow<Map<String, SharedListState>> = _sharedListsMap.asStateFlow()

    // Map of listId -> SyncStatus
    private val _syncStatusMap = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    val syncStatusMap: StateFlow<Map<String, SyncStatus>> = _syncStatusMap.asStateFlow()

    // Map of listId -> last known remote modifiedTime string
    private val lastKnownModifiedTime = mutableMapOf<String, String>()

    // Polling job for active list
    private var pollingJob: Job? = null
    private var currentlyOpenListId: String? = null

    init {
        // Load initial local caches on startup
        loadCachedListsFromDisk()
    }

    private fun loadCachedListsFromDisk() {
        try {
            val files = cacheDir.listFiles { f -> f.extension == "json" } ?: return
            val map = mutableMapOf<String, SharedListState>()
            for (f in files) {
                try {
                    val content = f.readText()
                    val shared = SharedTodoListFile.fromJsonString(content)
                    if (shared.id.isNotBlank()) {
                        map[shared.id] = SharedListState(
                            file = shared,
                            summary = null,
                            isReadOnly = false,
                            hasPendingUpload = false
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading cache file ${f.name}", e)
                }
            }
            _sharedListsMap.value = map
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached lists", e)
        }
    }

    fun clearAllCaches() {
        stopActivePolling()
        lastKnownModifiedTime.clear()
        _sharedListsMap.value = emptyMap()
        _syncStatusMap.value = emptyMap()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing shared cache dir", e)
        }
    }

    private fun writeToDisk(shared: SharedTodoListFile) {
        try {
            val file = File(cacheDir, "shared_${shared.id}.json")
            file.writeText(shared.toJsonString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed caching shared list to disk", e)
        }
    }

    private fun deleteFromDisk(listId: String) {
        try {
            val file = File(cacheDir, "shared_${listId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed deleting cached list from disk", e)
        }
    }

    fun getSyncStatus(listId: String): SyncStatus {
        return _syncStatusMap.value[listId] ?: SyncStatus.SYNCED
    }

    private fun setSyncStatus(listId: String, status: SyncStatus) {
        val current = _syncStatusMap.value.toMutableMap()
        current[listId] = status
        _syncStatusMap.value = current
    }

    /**
     * Start active polling for an open shared list (checks modifiedTime every 20s).
     */
    fun startActivePolling(listId: String, scope: CoroutineScope) {
        currentlyOpenListId = listId
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive && currentlyOpenListId == listId) {
                delay(POLL_INTERVAL_MS)
                if (!isActive || currentlyOpenListId != listId) break
                try {
                    checkAndSyncModifiedList(listId)
                } catch (e: Exception) {
                    Log.w(TAG, "Active polling error for $listId", e)
                }
            }
        }
    }

    /**
     * Stop active polling when exiting detail screen.
     */
    fun stopActivePolling() {
        currentlyOpenListId = null
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Discover all shared lists for current user from Google Drive.
     */
    suspend fun refreshDiscoveredSharedLists(token: String, currentUserEmail: String): Result<List<SharedListState>> = withContext(Dispatchers.IO) {
        val queryResult = driveService.querySharedLists(token, currentUserEmail)
        if (queryResult is DriveApiResult.Error) {
            if (queryResult.isUnauthorized) {
                GoogleDriveAuthHelper.invalidateToken(context, token)
            }
            return@withContext Result.failure(Exception(queryResult.message))
        }

        val summaries = (queryResult as DriveApiResult.Success).data
        val updatedMap = _sharedListsMap.value.toMutableMap()

        for (summary in summaries) {
            val existingState = updatedMap.values.find { it.file.driveFileId == summary.fileId }
            val downloadRes = driveService.downloadSharedList(token, summary.fileId, currentUserEmail)
            if (downloadRes is DriveApiResult.Success) {
                val (downloadedFile, updatedSummary) = downloadRes.data
                lastKnownModifiedTime[downloadedFile.id] = updatedSummary.modifiedTime

                val merged = if (existingState != null && existingState.hasPendingUpload) {
                    mergeLists(downloadedFile, existingState.file)
                } else {
                    downloadedFile.purgeExpiredTombstones()
                }

                writeToDisk(merged)
                val isReadOnly = !updatedSummary.canEdit ||
                        merged.members.find { it.email.equals(currentUserEmail, ignoreCase = true) }?.role == "viewer"

                updatedMap[merged.id] = SharedListState(
                    file = merged,
                    summary = updatedSummary,
                    isReadOnly = isReadOnly,
                    hasPendingUpload = false
                )
                setSyncStatus(merged.id, SyncStatus.SYNCED)
            }
        }

        _sharedListsMap.value = updatedMap
        Result.success(updatedMap.values.toList())
    }

    /**
     * Checks if remote file modifiedTime changed, and reloads if newer.
     */
    private suspend fun checkAndSyncModifiedList(listId: String) = withContext(Dispatchers.IO) {
        val state = _sharedListsMap.value[listId] ?: return@withContext
        val driveFileId = state.file.driveFileId
        if (driveFileId.isBlank()) return@withContext

        val tokenResult = GoogleDriveAuthHelper.getAccessToken(context)
        if (tokenResult !is DriveAuthResult.Success) {
            setSyncStatus(listId, SyncStatus.OFFLINE)
            return@withContext
        }
        val token = tokenResult.token

        val modTimeResult = driveService.getFileModifiedTime(token, driveFileId)
        if (modTimeResult is DriveApiResult.Error) {
            if (modTimeResult.isNotFound) {
                setSyncStatus(listId, SyncStatus.DELETED_BY_OWNER)
            } else if (modTimeResult.isUnauthorized) {
                GoogleDriveAuthHelper.invalidateToken(context, token)
                setSyncStatus(listId, SyncStatus.ACCESS_REVOKED)
            } else {
                setSyncStatus(listId, SyncStatus.OFFLINE)
            }
            return@withContext
        }

        val remoteModTime = (modTimeResult as DriveApiResult.Success).data
        val lastSeen = lastKnownModifiedTime[listId]

        if (lastSeen == null || remoteModTime != lastSeen) {
            // Remote has changed! Download and merge
            setSyncStatus(listId, SyncStatus.SYNCING)
            val downloadRes = driveService.downloadSharedList(token, driveFileId, tokenResult.account.email.orEmpty())
            if (downloadRes is DriveApiResult.Success) {
                val (downloadedFile, summary) = downloadRes.data
                lastKnownModifiedTime[listId] = summary.modifiedTime
                val merged = mergeLists(downloadedFile, state.file)
                writeToDisk(merged)

                val isReadOnly = !summary.canEdit ||
                        merged.members.find { it.email.equals(tokenResult.account.email, ignoreCase = true) }?.role == "viewer"

                val updatedMap = _sharedListsMap.value.toMutableMap()
                updatedMap[listId] = SharedListState(
                    file = merged,
                    summary = summary,
                    isReadOnly = isReadOnly,
                    hasPendingUpload = state.hasPendingUpload
                )
                _sharedListsMap.value = updatedMap
                setSyncStatus(listId, SyncStatus.SYNCED)
            } else {
                setSyncStatus(listId, SyncStatus.OFFLINE)
            }
        }
    }

    /**
     * Converts a local list to a Google Drive shared list and creates it in the "P Task Shared" folder.
     */
    suspend fun createAndUploadSharedList(
        token: String,
        sharedList: SharedTodoListFile,
        ownerEmail: String
    ): Result<SharedTodoListFile> = withContext(Dispatchers.IO) {
        setSyncStatus(sharedList.id, SyncStatus.SYNCING)

        // 1. Ensure folder exists
        val folderResult = driveService.getOrCreateSharedFolder(token)
        if (folderResult is DriveApiResult.Error) {
            setSyncStatus(sharedList.id, SyncStatus.OFFLINE)
            return@withContext Result.failure(Exception(folderResult.message))
        }
        val folderId = (folderResult as DriveApiResult.Success).data

        // 2. Upload file
        val uploadResult = driveService.createSharedListFile(token, folderId, sharedList)
        if (uploadResult is DriveApiResult.Error) {
            setSyncStatus(sharedList.id, SyncStatus.OFFLINE)
            return@withContext Result.failure(Exception(uploadResult.message))
        }
        val driveFileId = (uploadResult as DriveApiResult.Success).data

        val finalSharedList = sharedList.copy(driveFileId = driveFileId)
        writeToDisk(finalSharedList)

        val updatedMap = _sharedListsMap.value.toMutableMap()
        updatedMap[finalSharedList.id] = SharedListState(
            file = finalSharedList,
            summary = DriveFileSummary(
                fileId = driveFileId,
                name = "ptask_list_${finalSharedList.id}.json",
                modifiedTime = "",
                canEdit = true,
                isOwner = true,
                ownerEmail = ownerEmail
            ),
            isReadOnly = false,
            hasPendingUpload = false
        )
        _sharedListsMap.value = updatedMap
        setSyncStatus(finalSharedList.id, SyncStatus.SYNCED)
        Result.success(finalSharedList)
    }

    /**
     * Save/Queue updates to a shared list.
     * Merges with remote if changed before uploading.
     */
    suspend fun saveSharedListChanges(
        listId: String,
        updater: (SharedTodoListFile) -> SharedTodoListFile
    ): Result<SharedTodoListFile> = withContext(Dispatchers.IO) {
        val currentState = _sharedListsMap.value[listId]
            ?: return@withContext Result.failure(Exception("Shared list not found"))

        val modifiedFile = updater(currentState.file).copy(updatedAt = System.currentTimeMillis())
        writeToDisk(modifiedFile)

        // Optimistically update memory state
        val updatedMap = _sharedListsMap.value.toMutableMap()
        updatedMap[listId] = currentState.copy(
            file = modifiedFile,
            hasPendingUpload = true
        )
        _sharedListsMap.value = updatedMap
        setSyncStatus(listId, SyncStatus.SYNCING)

        // Attempt upload to Google Drive
        val tokenRes = GoogleDriveAuthHelper.getAccessToken(context)
        if (tokenRes !is DriveAuthResult.Success) {
            setSyncStatus(listId, SyncStatus.OFFLINE)
            return@withContext Result.success(modifiedFile)
        }
        val token = tokenRes.token

        // Check if remote file changed since last download
        val driveFileId = modifiedFile.driveFileId
        if (driveFileId.isNotBlank()) {
            val modRes = driveService.getFileModifiedTime(token, driveFileId)
            val remoteModTime = (modRes as? DriveApiResult.Success)?.data
            val lastSeen = lastKnownModifiedTime[listId]

            val fileToUpload = if (remoteModTime != null && lastSeen != null && remoteModTime != lastSeen) {
                // Conflict detected: download latest and merge before uploading!
                val downRes = driveService.downloadSharedList(token, driveFileId, tokenRes.account.email.orEmpty())
                if (downRes is DriveApiResult.Success) {
                    val (remoteFile, _) = downRes.data
                    mergeLists(remoteFile, modifiedFile)
                } else {
                    modifiedFile
                }
            } else {
                modifiedFile
            }

            val uploadRes = driveService.updateSharedListFile(token, driveFileId, fileToUpload)
            if (uploadRes is DriveApiResult.Success) {
                lastKnownModifiedTime[listId] = uploadRes.data
                writeToDisk(fileToUpload)
                val finalMap = _sharedListsMap.value.toMutableMap()
                finalMap[listId] = currentState.copy(file = fileToUpload, hasPendingUpload = false)
                _sharedListsMap.value = finalMap
                setSyncStatus(listId, SyncStatus.SYNCED)
                Result.success(fileToUpload)
            } else {
                setSyncStatus(listId, SyncStatus.OFFLINE)
                Result.success(fileToUpload)
            }
        } else {
            setSyncStatus(listId, SyncStatus.OFFLINE)
            Result.success(modifiedFile)
        }
    }

    /**
     * Merge algorithm: task-by-task merge using updatedAt (newest wins).
     * Deletions kept as tombstones and cleaned after 30 days.
     */
    fun mergeLists(remote: SharedTodoListFile, local: SharedTodoListFile): SharedTodoListFile {
        val now = System.currentTimeMillis()
        val allTaskIds = (remote.tasks.map { it.id } + local.tasks.map { it.id }).distinct()
        val mergedTasks = mutableListOf<SharedTaskItem>()

        val remoteMap = remote.tasks.associateBy { it.id }
        val localMap = local.tasks.associateBy { it.id }

        for (id in allTaskIds) {
            val rTask = remoteMap[id]
            val lTask = localMap[id]

            val winner = when {
                rTask == null -> lTask!!
                lTask == null -> rTask
                lTask.updatedAt >= rTask.updatedAt -> lTask
                else -> rTask
            }

            // Clean tombstones older than 30 days
            if (winner.isDeleted && (now - winner.updatedAt > THIRTY_DAYS_MS)) {
                // skip / purge expired tombstone
                continue
            }
            mergedTasks.add(winner)
        }

        // Merge members list
        val memberEmails = (remote.members.map { it.email.lowercase() } + local.members.map { it.email.lowercase() }).distinct()
        val rMemberMap = remote.members.associateBy { it.email.lowercase() }
        val lMemberMap = local.members.associateBy { it.email.lowercase() }
        val mergedMembers = memberEmails.mapNotNull { email ->
            val rm = rMemberMap[email]
            val lm = lMemberMap[email]
            rm ?: lm
        }

        val latestUpdatedAt = maxOf(remote.updatedAt, local.updatedAt)

        return remote.copy(
            name = if (local.updatedAt >= remote.updatedAt) local.name else remote.name,
            description = if (local.updatedAt >= remote.updatedAt) local.description else remote.description,
            category = if (local.updatedAt >= remote.updatedAt) local.category else remote.category,
            icon = if (local.updatedAt >= remote.updatedAt) local.icon else remote.icon,
            colorHex = if (local.updatedAt >= remote.updatedAt) local.colorHex else remote.colorHex,
            updatedAt = latestUpdatedAt,
            members = mergedMembers,
            tasks = mergedTasks
        )
    }

    /**
     * Invite member to shared list (creates Drive permission & updates JSON members).
     */
    suspend fun inviteMember(
        listId: String,
        email: String,
        role: String // "writer" or "reader"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val state = _sharedListsMap.value[listId]
            ?: return@withContext Result.failure(Exception("List not found"))

        val tokenRes = GoogleDriveAuthHelper.getAccessToken(context)
        if (tokenRes !is DriveAuthResult.Success) {
            return@withContext Result.failure(Exception("Not authenticated with Google Drive"))
        }

        val driveRole = if (role == "viewer" || role == "reader") "reader" else "writer"
        val appRole = if (driveRole == "reader") "viewer" else "editor"

        val permRes = driveService.addPermission(tokenRes.token, state.file.driveFileId, email, driveRole)
        if (permRes is DriveApiResult.Error) {
            if (permRes.isUnauthorized) {
                GoogleDriveAuthHelper.invalidateToken(context, tokenRes.token)
            }
            return@withContext Result.failure(Exception(permRes.message))
        }
        val permId = (permRes as DriveApiResult.Success).data

        val saveRes = saveSharedListChanges(listId) { file ->
            val existingColors = file.members.map { it.color }
            val memberColor = DriveColorPalette.getDeterministicColor(email, existingColors)
            val updatedMembers = file.members.filterNot { it.email.equals(email, ignoreCase = true) } +
                    ListMember(
                        email = email.trim(),
                        name = email.substringBefore("@"),
                        color = memberColor,
                        role = appRole,
                        permissionId = permId
                    )
            file.copy(members = updatedMembers)
        }
        saveRes.map { Unit }
    }

    /**
     * Change member role.
     */
    suspend fun updateMemberRole(
        listId: String,
        memberEmail: String,
        newRole: String // "editor" or "viewer"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val state = _sharedListsMap.value[listId] ?: return@withContext Result.failure(Exception("List not found"))
        val member = state.file.members.find { it.email.equals(memberEmail, ignoreCase = true) }
            ?: return@withContext Result.failure(Exception("Member not found"))

        val tokenRes = GoogleDriveAuthHelper.getAccessToken(context)
        if (tokenRes is DriveAuthResult.Success && member.permissionId.isNotBlank()) {
            val driveRole = if (newRole == "viewer") "reader" else "writer"
            driveService.updatePermissionRole(tokenRes.token, state.file.driveFileId, member.permissionId, driveRole)
        }

        saveSharedListChanges(listId) { file ->
            val updated = file.members.map {
                if (it.email.equals(memberEmail, ignoreCase = true)) it.copy(role = newRole) else it
            }
            file.copy(members = updated)
        }
        Result.success(Unit)
    }

    /**
     * Remove member from shared list.
     */
    suspend fun removeMember(
        listId: String,
        memberEmail: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val state = _sharedListsMap.value[listId] ?: return@withContext Result.failure(Exception("List not found"))
        val member = state.file.members.find { it.email.equals(memberEmail, ignoreCase = true) }

        val tokenRes = GoogleDriveAuthHelper.getAccessToken(context)
        if (tokenRes is DriveAuthResult.Success && member != null && member.permissionId.isNotBlank()) {
            driveService.removePermission(tokenRes.token, state.file.driveFileId, member.permissionId)
        }

        saveSharedListChanges(listId) { file ->
            val updated = file.members.filterNot { it.email.equals(memberEmail, ignoreCase = true) }
            file.copy(members = updated)
        }
        Result.success(Unit)
    }

    /**
     * Leave shared list (for non-owner members).
     */
    suspend fun leaveSharedList(
        listId: String,
        currentUserEmail: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val state = _sharedListsMap.value[listId] ?: return@withContext Result.failure(Exception("List not found"))
        val member = state.file.members.find { it.email.equals(currentUserEmail, ignoreCase = true) }

        val tokenRes = GoogleDriveAuthHelper.getAccessToken(context)
        if (tokenRes is DriveAuthResult.Success && member != null && member.permissionId.isNotBlank()) {
            driveService.removePermission(tokenRes.token, state.file.driveFileId, member.permissionId)
        }

        deleteFromDisk(listId)
        val map = _sharedListsMap.value.toMutableMap()
        map.remove(listId)
        _sharedListsMap.value = map
        Result.success(Unit)
    }

    /**
     * Delete shared list completely from Drive and disk (owner only).
     */
    suspend fun deleteSharedListCompletely(listId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val state = _sharedListsMap.value[listId] ?: return@withContext Result.failure(Exception("List not found"))

        val tokenRes = GoogleDriveAuthHelper.getAccessToken(context)
        if (tokenRes is DriveAuthResult.Success && state.file.driveFileId.isNotBlank()) {
            driveService.deleteFile(tokenRes.token, state.file.driveFileId)
        }

        deleteFromDisk(listId)
        val map = _sharedListsMap.value.toMutableMap()
        map.remove(listId)
        _sharedListsMap.value = map
        Result.success(Unit)
    }
}
