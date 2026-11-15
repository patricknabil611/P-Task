package com.example.data.drive

import com.example.data.model.ListMember
import com.example.data.model.SharedTodoListFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveFileSummary(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String = "",
    val canEdit: Boolean = true,
    val ownerEmail: String = ""
)

sealed interface DriveApiResult<out T> {
    data class Success<T>(val data: T) : DriveApiResult<T>
    data class HttpError(val code: Int, val message: String) : DriveApiResult<Nothing>
    data class NetworkError(val message: String) : DriveApiResult<Nothing>
}

class GoogleDriveService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listFileAdapter = moshi.adapter(SharedTodoListFile::class.java)

    private fun extractErrorSnippet(code: Int, body: String): String {
        val cleaned = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        return cleaned.ifBlank { body.take(180).ifBlank { "HTTP $code" } }
    }

    suspend fun listSharedTaskFiles(accessToken: String): DriveApiResult<List<DriveFileSummary>> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(
                "trashed = false and name contains 'ptask_list_' and mimeType = 'application/json'",
                "UTF-8"
            )
            val fields = URLEncoder.encode(
                "files(id,name,mimeType,modifiedTime,capabilities(canEdit),owners(emailAddress))",
                "UTF-8"
            )
            val url =
                "https://www.googleapis.com/drive/v3/files?q=$q&fields=$fields&includeItemsFromAllDrives=true&supportsAllDrives=true&pageSize=50"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext DriveApiResult.HttpError(
                            response.code,
                            extractErrorSnippet(response.code, body)
                        )
                    }
                    val filesArray = JSONObject(body).optJSONArray("files") ?: JSONArray()
                    val result = buildList {
                        for (i in 0 until filesArray.length()) {
                            val obj = filesArray.getJSONObject(i)
                            val canEdit = obj.optJSONObject("capabilities")?.optBoolean("canEdit", true) ?: true
                            val ownerEmail = obj.optJSONArray("owners")?.optJSONObject(0)?.optString("emailAddress").orEmpty()
                            add(
                                DriveFileSummary(
                                    id = obj.optString("id"),
                                    name = obj.optString("name"),
                                    mimeType = obj.optString("mimeType"),
                                    modifiedTime = obj.optString("modifiedTime"),
                                    canEdit = canEdit,
                                    ownerEmail = ownerEmail
                                )
                            )
                        }
                    }.filter { it.id.isNotBlank() }
                    DriveApiResult.Success(result)
                }
            } catch (e: Exception) {
                DriveApiResult.NetworkError(e.localizedMessage ?: "Network error listing Drive files")
            }
        }

    suspend fun downloadListFile(accessToken: String, fileId: String): DriveApiResult<SharedTodoListFile> =
        withContext(Dispatchers.IO) {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext DriveApiResult.HttpError(
                            response.code,
                            extractErrorSnippet(response.code, body)
                        )
                    }
                    val parsed = listFileAdapter.fromJson(body)
                        ?: return@withContext DriveApiResult.NetworkError("Invalid list JSON format")
                    val normalized = if (parsed.id.isBlank()) parsed.copy(id = fileId) else parsed
                    DriveApiResult.Success(normalized)
                }
            } catch (e: Exception) {
                DriveApiResult.NetworkError(e.localizedMessage ?: "Failed to read shared list")
            }
        }

    suspend fun createSharedListFile(
        accessToken: String,
        file: SharedTodoListFile
    ): DriveApiResult<SharedTodoListFile> = withContext(Dispatchers.IO) {
        val boundary = "ptask_boundary_${System.currentTimeMillis()}"
        val fileName = "ptask_list_${file.title.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32)}.json"
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/json")
        }.toString()
        val contentJson = listFileAdapter.toJson(file)

        val multipartPayload = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(contentJson)
            append("\r\n--$boundary--")
        }

        val body = multipartPayload.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext DriveApiResult.HttpError(
                        response.code,
                        extractErrorSnippet(response.code, respBody)
                    )
                }
                val newId = JSONObject(respBody).optString("id")
                if (newId.isBlank()) {
                    return@withContext DriveApiResult.NetworkError("Drive did not return a file ID")
                }
                val updated = file.copy(id = newId)
                updateSharedListFile(accessToken, newId, updated)
            }
        } catch (e: Exception) {
            DriveApiResult.NetworkError(e.localizedMessage ?: "Failed to upload shared list")
        }
    }

    suspend fun updateSharedListFile(
        accessToken: String,
        fileId: String,
        file: SharedTodoListFile
    ): DriveApiResult<SharedTodoListFile> = withContext(Dispatchers.IO) {
        val normalized = file.copy(id = fileId)
        val contentJson = listFileAdapter.toJson(normalized)
        val body = contentJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&supportsAllDrives=true")
            .header("Authorization", "Bearer $accessToken")
            .patch(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext DriveApiResult.HttpError(
                        response.code,
                        extractErrorSnippet(response.code, respBody)
                    )
                }
                DriveApiResult.Success(normalized)
            }
        } catch (e: Exception) {
            DriveApiResult.NetworkError(e.localizedMessage ?: "Failed to update shared list")
        }
    }

    suspend fun addPermission(
        accessToken: String,
        fileId: String,
        email: String,
        role: String,
        listTitle: String
    ): DriveApiResult<Unit> = withContext(Dispatchers.IO) {
        val safeRole = if (role == "reader") "reader" else "writer"
        val emailMessage = URLEncoder.encode(
            "You were invited to collaborate on the \"$listTitle\" list in P Task.",
            "UTF-8"
        )
        val url =
            "https://www.googleapis.com/drive/v3/files/$fileId/permissions?sendNotificationEmail=true&emailMessage=$emailMessage&supportsAllDrives=true"
        val payload = JSONObject().apply {
            put("type", "user")
            put("role", safeRole)
            put("emailAddress", email.trim())
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext DriveApiResult.HttpError(
                        response.code,
                        extractErrorSnippet(response.code, respBody)
                    )
                }
                DriveApiResult.Success(Unit)
            }
        } catch (e: Exception) {
            DriveApiResult.NetworkError(e.localizedMessage ?: "Failed to send invitation")
        }
    }

    suspend fun fetchPermissions(accessToken: String, fileId: String): DriveApiResult<List<ListMember>> =
        withContext(Dispatchers.IO) {
            val fields = URLEncoder.encode("permissions(emailAddress,role,displayName)", "UTF-8")
            val url =
                "https://www.googleapis.com/drive/v3/files/$fileId/permissions?fields=$fields&supportsAllDrives=true"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext DriveApiResult.HttpError(
                            response.code,
                            extractErrorSnippet(response.code, body)
                        )
                    }
                    val perms = JSONObject(body).optJSONArray("permissions") ?: JSONArray()
                    val members = buildList {
                        for (i in 0 until perms.length()) {
                            val p = perms.getJSONObject(i)
                            val email = p.optString("emailAddress")
                            if (email.isNotBlank()) {
                                add(
                                    ListMember(
                                        email = email,
                                        role = p.optString("role", "writer"),
                                        displayName = p.optString("displayName")
                                    )
                                )
                            }
                        }
                    }
                    DriveApiResult.Success(members)
                }
            } catch (e: Exception) {
                DriveApiResult.NetworkError(e.localizedMessage ?: "Failed to fetch members")
            }
        }
}
