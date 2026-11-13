package com.example.data.drive

import android.util.Log
import com.example.data.model.ListMember
import com.example.data.model.SharedTodoListFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DriveFileSummary(
    val fileId: String,
    val name: String,
    val modifiedTime: String,
    val canEdit: Boolean,
    val isOwner: Boolean,
    val ownerEmail: String,
    val isTrashed: Boolean = false
)

sealed interface DriveApiResult<out T> {
    data class Success<T>(val data: T) : DriveApiResult<T>
    data class Error(val message: String, val isUnauthorized: Boolean = false, val isNotFound: Boolean = false) : DriveApiResult<Nothing>
}

class GoogleDriveService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GoogleDriveService"
        private const val BASE_DRIVE_URL = "https://www.googleapis.com/drive/v3"
        private const val BASE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val FOLDER_NAME = "P Task Shared"
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Find or create the "P Task Shared" folder in Google Drive.
     */
    suspend fun getOrCreateSharedFolder(token: String): DriveApiResult<String> = withContext(Dispatchers.IO) {
        // Step 1: Search for existing folder
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$FOLDER_NAME' and trashed = false"
        val searchUrl = "$BASE_DRIVE_URL/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)"

        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    return@withContext DriveApiResult.Error("Access token expired", isUnauthorized = true)
                }
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(200).orEmpty()
                    return@withContext DriveApiResult.Error("Failed to find folder: HTTP ${response.code} $err")
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val folderId = files.getJSONObject(0).getString("id")
                    return@withContext DriveApiResult.Success(folderId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search folder error", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Network error searching folder")
        }

        // Step 2: Create folder if not found
        val createBody = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString().toRequestBody(jsonMediaType)

        val createRequest = Request.Builder()
            .url("$BASE_DRIVE_URL/files")
            .addHeader("Authorization", "Bearer $token")
            .post(createBody)
            .build()

        try {
            client.newCall(createRequest).execute().use { response ->
                if (response.code == 401) {
                    return@withContext DriveApiResult.Error("Access token expired", isUnauthorized = true)
                }
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(200).orEmpty()
                    return@withContext DriveApiResult.Error("Failed to create '$FOLDER_NAME' folder: HTTP ${response.code} $err")
                }
                val body = response.body?.string().orEmpty()
                val folderId = JSONObject(body).getString("id")
                return@withContext DriveApiResult.Success(folderId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create folder error", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Network error creating folder")
        }
    }

    /**
     * Search for all files that have appProperties ptask='sharedList'
     */
    suspend fun querySharedLists(token: String, currentUserEmail: String): DriveApiResult<List<DriveFileSummary>> = withContext(Dispatchers.IO) {
        val query = "trashed = false and (name contains 'ptask_list_' or properties has { key='ptask' and value='sharedList' } or appProperties has { key='ptask' and value='sharedList' })"
        val fields = "files(id,name,modifiedTime,capabilities,owners,trashed,properties,appProperties,sharedWithMe)"
        val url = "$BASE_DRIVE_URL/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=${java.net.URLEncoder.encode(fields, "UTF-8")}&spaces=drive&supportsAllDrives=true&includeItemsFromAllDrives=true&pageSize=100"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                }
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(200).orEmpty()
                    return@withContext DriveApiResult.Error("Query failed: HTTP ${response.code} $err")
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val files = json.optJSONArray("files") ?: JSONArray()
                val summaries = mutableListOf<DriveFileSummary>()

                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val fileId = f.getString("id")
                    val name = f.optString("name", "")
                    val modifiedTime = f.optString("modifiedTime", "")
                    val capabilities = f.optJSONObject("capabilities")
                    val canEdit = capabilities?.optBoolean("canEdit", true) ?: true
                    val owners = f.optJSONArray("owners")
                    val ownerEmail = owners?.optJSONObject(0)?.optString("emailAddress", "").orEmpty()
                    val isOwner = ownerEmail.equals(currentUserEmail, ignoreCase = true)

                    summaries.add(
                        DriveFileSummary(
                            fileId = fileId,
                            name = name,
                            modifiedTime = modifiedTime,
                            canEdit = canEdit,
                            isOwner = isOwner,
                            ownerEmail = ownerEmail,
                            isTrashed = f.optBoolean("trashed", false)
                        )
                    )
                }
                return@withContext DriveApiResult.Success(summaries)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query shared lists error", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Failed to load shared lists")
        }
    }

    /**
     * Download the JSON content of a shared list along with its current metadata.
     */
    suspend fun downloadSharedList(
        token: String,
        fileId: String,
        currentUserEmail: String
    ): DriveApiResult<Pair<SharedTodoListFile, DriveFileSummary>> = withContext(Dispatchers.IO) {
        // Step 1: Get metadata
        val metaUrl = "$BASE_DRIVE_URL/files/$fileId?fields=id,name,modifiedTime,capabilities,owners,permissions,trashed&supportsAllDrives=true"
        val metaRequest = Request.Builder()
            .url(metaUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val summary: DriveFileSummary
        try {
            client.newCall(metaRequest).execute().use { response ->
                if (response.code == 401) {
                    return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                }
                if (response.code == 404) {
                    return@withContext DriveApiResult.Error("File not found or access revoked", isNotFound = true)
                }
                if (!response.isSuccessful) {
                    return@withContext DriveApiResult.Error("Failed to fetch metadata: HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optBoolean("trashed", false)) {
                    return@withContext DriveApiResult.Error("This list was deleted by the owner", isNotFound = true)
                }
                val capabilities = json.optJSONObject("capabilities")
                val canEdit = capabilities?.optBoolean("canEdit", true) ?: true
                val owners = json.optJSONArray("owners")
                val ownerEmail = owners?.optJSONObject(0)?.optString("emailAddress", "").orEmpty()

                summary = DriveFileSummary(
                    fileId = fileId,
                    name = json.optString("name", ""),
                    modifiedTime = json.optString("modifiedTime", ""),
                    canEdit = canEdit,
                    isOwner = ownerEmail.equals(currentUserEmail, ignoreCase = true),
                    ownerEmail = ownerEmail
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching file metadata", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Network error downloading file metadata")
        }

        // Step 2: Download JSON file body
        val downloadUrl = "$BASE_DRIVE_URL/files/$fileId?alt=media&supportsAllDrives=true"
        val downloadRequest = Request.Builder()
            .url(downloadUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(downloadRequest).execute().use { response ->
                if (response.code == 401) {
                    return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                }
                if (response.code == 404) {
                    return@withContext DriveApiResult.Error("File not found", isNotFound = true)
                }
                if (!response.isSuccessful) {
                    return@withContext DriveApiResult.Error("Failed to download file content: HTTP ${response.code}")
                }
                val jsonContent = response.body?.string().orEmpty()
                val sharedList = SharedTodoListFile.fromJsonString(jsonContent, driveFileIdFallback = fileId)
                return@withContext DriveApiResult.Success(Pair(sharedList.copy(driveFileId = fileId), summary))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading content", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Network error downloading list")
        }
    }

    /**
     * Upload a new shared list JSON file to Drive into the "P Task Shared" folder.
     */
    suspend fun createSharedListFile(
        token: String,
        folderId: String,
        sharedList: SharedTodoListFile
    ): DriveApiResult<String> = withContext(Dispatchers.IO) {
        val fileName = "ptask_list_${sharedList.id}.json"

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
            put("mimeType", "application/json")
            put("appProperties", JSONObject().put("ptask", "sharedList"))
            put("properties", JSONObject().put("ptask", "sharedList"))
        }.toString()

        val contentString = sharedList.toJsonString()

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody(jsonMediaType))
            .addPart(contentString.toRequestBody(jsonMediaType))
            .build()

        val request = Request.Builder()
            .url("$BASE_UPLOAD_URL?uploadType=multipart&supportsAllDrives=true")
            .addHeader("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                }
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(200).orEmpty()
                    return@withContext DriveApiResult.Error("Failed to create file on Drive: HTTP ${response.code} $err")
                }
                val body = response.body?.string().orEmpty()
                val fileId = JSONObject(body).getString("id")
                return@withContext DriveApiResult.Success(fileId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating shared file", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Failed to upload shared list")
        }
    }

    /**
     * Update the content of an existing shared list file on Drive.
     */
    suspend fun updateSharedListFile(
        token: String,
        fileId: String,
        sharedList: SharedTodoListFile
    ): DriveApiResult<String> = withContext(Dispatchers.IO) {
        val contentString = sharedList.toJsonString()
        val requestBody = contentString.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$BASE_UPLOAD_URL/$fileId?uploadType=media&supportsAllDrives=true")
            .addHeader("Authorization", "Bearer $token")
            .patch(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                }
                if (response.code == 404) {
                    return@withContext DriveApiResult.Error("File not found or removed", isNotFound = true)
                }
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(200).orEmpty()
                    return@withContext DriveApiResult.Error("Failed to update file: HTTP ${response.code} $err")
                }
                val body = response.body?.string().orEmpty()
                val modifiedTime = JSONObject(body).optString("modifiedTime", "")
                return@withContext DriveApiResult.Success(modifiedTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating file", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Network error updating shared list")
        }
    }

    /**
     * Check modifiedTime of a file on Drive (used for 20s polling).
     */
    suspend fun getFileModifiedTime(token: String, fileId: String): DriveApiResult<String> = withContext(Dispatchers.IO) {
        val url = "$BASE_DRIVE_URL/files/$fileId?fields=modifiedTime,trashed&supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                if (response.code == 404) return@withContext DriveApiResult.Error("File not found", isNotFound = true)
                if (!response.isSuccessful) return@withContext DriveApiResult.Error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optBoolean("trashed", false)) {
                    return@withContext DriveApiResult.Error("This list was deleted by the owner", isNotFound = true)
                }
                val modifiedTime = json.optString("modifiedTime", "")
                return@withContext DriveApiResult.Success(modifiedTime)
            }
        } catch (e: Exception) {
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Failed checking modifiedTime")
        }
    }

    /**
     * Add member permission via Google Drive Permissions API.
     * role: "writer" ("Can edit") or "reader" ("Can view")
     */
    suspend fun addPermission(
        token: String,
        fileId: String,
        email: String,
        role: String,
        emailMessage: String = "You have been invited to collaborate on a shared todo list in P Task. Open the P Task app and check 'Shared with me'."
    ): DriveApiResult<String> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("role", role)
            put("type", "user")
            put("emailAddress", email.trim())
        }.toString().toRequestBody(jsonMediaType)

        val encodedMsg = java.net.URLEncoder.encode(emailMessage, "UTF-8")
        val url = "$BASE_DRIVE_URL/files/$fileId/permissions?sendNotificationEmail=true&emailMessage=$encodedMsg&supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext DriveApiResult.Error("Failed to add member permission: HTTP ${response.code} $err")
                }
                val respBody = response.body?.string().orEmpty()
                val permId = JSONObject(respBody).optString("id", "")
                return@withContext DriveApiResult.Success(permId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding permission", e)
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Failed inviting member")
        }
    }

    /**
     * Update member permission role (e.g. from writer to reader or vice versa).
     */
    suspend fun updatePermissionRole(
        token: String,
        fileId: String,
        permissionId: String,
        newRole: String
    ): DriveApiResult<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("role", newRole)
        }.toString().toRequestBody(jsonMediaType)

        val url = "$BASE_DRIVE_URL/files/$fileId/permissions/$permissionId?supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .patch(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                if (!response.isSuccessful) {
                    return@withContext DriveApiResult.Error("Failed to update role: HTTP ${response.code}")
                }
                return@withContext DriveApiResult.Success(Unit)
            }
        } catch (e: Exception) {
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Failed updating role")
        }
    }

    /**
     * Remove member permission from Drive file.
     */
    suspend fun removePermission(
        token: String,
        fileId: String,
        permissionId: String
    ): DriveApiResult<Unit> = withContext(Dispatchers.IO) {
        val url = "$BASE_DRIVE_URL/files/$fileId/permissions/$permissionId?supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                if (!response.isSuccessful && response.code != 404) {
                    return@withContext DriveApiResult.Error("Failed to remove permission: HTTP ${response.code}")
                }
                return@withContext DriveApiResult.Success(Unit)
            }
        } catch (e: Exception) {
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Failed removing member")
        }
    }

    /**
     * Delete file from Drive (owner only).
     */
    suspend fun deleteFile(
        token: String,
        fileId: String
    ): DriveApiResult<Unit> = withContext(Dispatchers.IO) {
        val url = "$BASE_DRIVE_URL/files/$fileId?supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return@withContext DriveApiResult.Error("Unauthorized", isUnauthorized = true)
                if (!response.isSuccessful && response.code != 404) {
                    return@withContext DriveApiResult.Error("Failed to delete file: HTTP ${response.code}")
                }
                return@withContext DriveApiResult.Success(Unit)
            }
        } catch (e: Exception) {
            return@withContext DriveApiResult.Error(e.localizedMessage ?: "Failed deleting file")
        }
    }
}
