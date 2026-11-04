package com.example.backup

import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupRestoreManager {

    fun exportToJson(lists: List<TodoList>, tasks: List<TaskItem>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "P Task")
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        val listsArray = JSONArray()
        for (list in lists) {
            val listObj = JSONObject().apply {
                put("id", list.id)
                put("title", list.title)
                put("description", list.description)
                put("category", list.category)
                put("icon", list.icon)
                put("colorHex", list.colorHex)
                put("isPinned", list.isPinned)
                put("isLocked", list.isLocked)
                put("orderIndex", list.orderIndex)
            }
            listsArray.put(listObj)
        }
        root.put("lists", listsArray)

        val tasksArray = JSONArray()
        for (task in tasks) {
            val taskObj = JSONObject().apply {
                put("id", task.id)
                put("listId", task.listId)
                put("title", task.title)
                put("notes", task.notes)
                put("isCompleted", task.isCompleted)
                put("completedAt", task.completedAt)
                put("priority", task.priority)
                put("dueDateEpoch", task.dueDateEpoch)
                put("reminderEpoch", task.reminderEpoch)
                put("reminderSound", task.reminderSound)
                put("category", task.category)
            }
            tasksArray.put(taskObj)
        }
        root.put("tasks", tasksArray)

        return root.toString(2)
    }

    fun parseBackup(jsonString: String): Result<Pair<List<TodoList>, List<TaskItem>>> {
        return try {
            val root = JSONObject(jsonString)
            // Accepts old backups created by "OmniTask" as well as "P Task"
            val listsArray = root.getJSONArray("lists")
            val tasksArray = root.getJSONArray("tasks")

            val lists = mutableListOf<TodoList>()
            for (i in 0 until listsArray.length()) {
                val obj = listsArray.getJSONObject(i)
                lists.add(
                    TodoList(
                        id = obj.optString("id", ""),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", "Personal"),
                        icon = obj.optString("icon", "checklist"),
                        colorHex = obj.optString("colorHex", "#6366F1"),
                        isPinned = obj.optBoolean("isPinned", false),
                        isLocked = obj.optBoolean("isLocked", false),
                        orderIndex = obj.optInt("orderIndex", i)
                    )
                )
            }

            val tasks = mutableListOf<TaskItem>()
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.getJSONObject(i)
                val isCompleted = obj.optBoolean("isCompleted", false)
                val completedAt = obj.optLong(
                    "completedAt",
                    if (isCompleted) System.currentTimeMillis() else 0L
                )
                tasks.add(
                    TaskItem(
                        id = obj.optString("id", ""),
                        listId = obj.getString("listId"),
                        title = obj.getString("title"),
                        notes = obj.optString("notes", ""),
                        isCompleted = isCompleted,
                        completedAt = completedAt,
                        priority = obj.optString("priority", "MEDIUM"),
                        dueDateEpoch = obj.optLong("dueDateEpoch", 0L),
                        reminderEpoch = obj.optLong("reminderEpoch", 0L),
                        reminderSound = obj.optString("reminderSound", "chime"),
                        category = obj.optString("category", "General")
                    )
                )
            }

            Result.success(Pair(lists, tasks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
