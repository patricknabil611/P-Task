package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val lists: List<TodoList>,
    val tasks: List<TaskItem>
)

object BackupRestoreManager {

    fun exportToUri(
        context: Context,
        uri: Uri,
        lists: List<TodoList>,
        tasks: List<TaskItem>
    ): Result<Int> {
        return try {
            val root = JSONObject().apply {
                put("app", "P Task")
                put("exportedAt", System.currentTimeMillis())
                put("lists", JSONArray().apply {
                    lists.filter { !it.isShared }.forEach { list ->
                        put(JSONObject().apply {
                            put("id", list.id)
                            put("title", list.title)
                            put("iconKey", list.iconKey)
                            put("colorHex", list.colorHex)
                            put("isLocked", list.isLocked)
                            put("sortOrder", list.sortOrder)
                        })
                    }
                })
                put("tasks", JSONArray().apply {
                    tasks.forEach { task ->
                        put(JSONObject().apply {
                            put("id", task.id)
                            put("listId", task.listId)
                            put("title", task.title)
                            put("notes", task.notes)
                            put("priority", task.priority)
                            put("isCompleted", task.isCompleted)
                            put("dueDateEpoch", task.dueDateEpoch)
                            put("reminderEpoch", task.reminderEpoch)
                            put("reminderSoundKey", task.reminderSoundKey)
                            put("category", task.category)
                        })
                    }
                })
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(IllegalStateException("Could not open export destination"))
            Result.success(tasks.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importFromUri(context: Context, uri: Uri): Result<BackupPayload> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: return Result.failure(IllegalStateException("Could not read backup file"))

            val root = JSONObject(content)
            val listsJson = root.optJSONArray("lists") ?: JSONArray()
            val tasksJson = root.optJSONArray("tasks") ?: JSONArray()

            val lists = buildList {
                for (i in 0 until listsJson.length()) {
                    val obj = listsJson.getJSONObject(i)
                    add(
                        TodoList(
                            id = obj.optString("id"),
                            title = obj.optString("title", "Imported List"),
                            iconKey = obj.optString("iconKey", "list"),
                            colorHex = obj.optString("colorHex", "#4F46E5"),
                            isLocked = obj.optBoolean("isLocked", false),
                            sortOrder = obj.optLong("sortOrder", i.toLong())
                        )
                    )
                }
            }
            val tasks = buildList {
                for (i in 0 until tasksJson.length()) {
                    val obj = tasksJson.getJSONObject(i)
                    add(
                        TaskItem(
                            id = obj.optString("id"),
                            listId = obj.optString("listId"),
                            title = obj.optString("title", "Imported Task"),
                            notes = obj.optString("notes"),
                            priority = obj.optString("priority", "MEDIUM"),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            dueDateEpoch = obj.optLong("dueDateEpoch", 0L),
                            reminderEpoch = obj.optLong("reminderEpoch", 0L),
                            reminderSoundKey = obj.optString("reminderSoundKey", "chime"),
                            category = obj.optString("category", "General")
                        )
                    )
                }
            }
            Result.success(BackupPayload(lists = lists, tasks = tasks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
