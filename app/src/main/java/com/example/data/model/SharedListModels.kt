package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class ListMember(
    val email: String = "",
    val name: String = "",
    val color: String = "#4F46E5",
    val role: String = "editor", // "owner", "editor", "viewer"
    val permissionId: String = ""
) {
    fun canEdit(): Boolean = role == "owner" || role == "editor"
    fun isOwner(): Boolean = role == "owner"

    fun toJson(): JSONObject = JSONObject().apply {
        put("email", email)
        put("name", name)
        put("color", color)
        put("role", role)
        put("permissionId", permissionId)
    }

    companion object {
        fun fromJson(obj: JSONObject): ListMember {
            return ListMember(
                email = obj.optString("email", ""),
                name = obj.optString("name", ""),
                color = obj.optString("color", "#4F46E5"),
                role = obj.optString("role", "editor"),
                permissionId = obj.optString("permissionId", "")
            )
        }
    }
}

data class SharedTaskItem(
    val id: String = "",
    val listId: String = "",
    val title: String = "",
    val notes: String = "",
    val isCompleted: Boolean = false,
    val priority: String = "MEDIUM",
    val dueDateEpoch: Long = 0L,
    val reminderEpoch: Long = 0L,
    val reminderSound: String = "chime",
    val category: String = "General",
    val completedAt: Long = 0L,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val updatedByName: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    fun toTaskItem(): TaskItem = TaskItem(
        id = id,
        userId = createdBy,
        listId = listId,
        title = title,
        notes = notes,
        isCompleted = isCompleted,
        priority = priority,
        dueDateEpoch = dueDateEpoch,
        reminderEpoch = reminderEpoch,
        reminderSound = reminderSound,
        category = category,
        completedAt = completedAt
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("listId", listId)
        put("title", title)
        put("notes", notes)
        put("isCompleted", isCompleted)
        put("priority", priority)
        put("dueDateEpoch", dueDateEpoch)
        put("reminderEpoch", reminderEpoch)
        put("reminderSound", reminderSound)
        put("category", category)
        put("completedAt", completedAt)
        put("createdBy", createdBy)
        put("createdByName", createdByName)
        put("createdAt", createdAt)
        put("updatedBy", updatedBy)
        put("updatedByName", updatedByName)
        put("updatedAt", updatedAt)
        put("isDeleted", isDeleted)
    }

    companion object {
        fun fromJson(obj: JSONObject): SharedTaskItem {
            return SharedTaskItem(
                id = obj.optString("id", ""),
                listId = obj.optString("listId", ""),
                title = obj.optString("title", ""),
                notes = obj.optString("notes", ""),
                isCompleted = obj.optBoolean("isCompleted", false),
                priority = obj.optString("priority", "MEDIUM"),
                dueDateEpoch = obj.optLong("dueDateEpoch", 0L),
                reminderEpoch = obj.optLong("reminderEpoch", 0L),
                reminderSound = obj.optString("reminderSound", "chime"),
                category = obj.optString("category", "General"),
                completedAt = obj.optLong("completedAt", 0L),
                createdBy = obj.optString("createdBy", ""),
                createdByName = obj.optString("createdByName", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedBy = obj.optString("updatedBy", ""),
                updatedByName = obj.optString("updatedByName", ""),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                isDeleted = obj.optBoolean("isDeleted", false)
            )
        }

        fun fromTaskItem(
            task: TaskItem,
            userEmail: String,
            userName: String
        ): SharedTaskItem {
            val now = System.currentTimeMillis()
            return SharedTaskItem(
                id = task.id.ifBlank { "task_${java.util.UUID.randomUUID()}" },
                listId = task.listId,
                title = task.title,
                notes = task.notes,
                isCompleted = task.isCompleted,
                priority = task.priority,
                dueDateEpoch = task.dueDateEpoch,
                reminderEpoch = task.reminderEpoch,
                reminderSound = task.reminderSound,
                category = task.category,
                completedAt = task.completedAt,
                createdBy = userEmail,
                createdByName = userName,
                createdAt = task.createdAt?.toDate()?.time ?: now,
                updatedBy = userEmail,
                updatedByName = userName,
                updatedAt = now,
                isDeleted = false
            )
        }
    }
}

data class SharedTodoListFile(
    val id: String = "",
    val name: String = "",
    val ownerEmail: String = "",
    val ownerName: String = "",
    val description: String = "",
    val category: String = "Shared",
    val icon: String = "checklist",
    val colorHex: String = "#6366F1",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val driveFileId: String = "",
    val members: List<ListMember> = emptyList(),
    val tasks: List<SharedTaskItem> = emptyList()
) {
    fun toTodoList(isReadOnly: Boolean = false): TodoList {
        return TodoList(
            id = id,
            userId = ownerEmail,
            title = name,
            description = description,
            category = category,
            icon = icon,
            colorHex = colorHex,
            isPinned = false,
            isLocked = false, // Read-only access is handled by isReadOnly, not biometric lock
            orderIndex = 0
        )
    }

    /**
     * Purges tombstone deletions older than 30 days.
     */
    fun purgeExpiredTombstones(now: Long = System.currentTimeMillis()): SharedTodoListFile {
        val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000L
        val cleanedTasks = tasks.filterNot { it.isDeleted && (now - it.updatedAt > thirtyDaysMillis) }
        return copy(tasks = cleanedTasks)
    }

    fun toJsonString(): String {
        val root = JSONObject()
        root.put("id", id)
        root.put("name", name)
        root.put("ownerEmail", ownerEmail)
        root.put("ownerName", ownerName)
        root.put("description", description)
        root.put("category", category)
        root.put("icon", icon)
        root.put("colorHex", colorHex)
        root.put("createdAt", createdAt)
        root.put("updatedAt", updatedAt)
        root.put("driveFileId", driveFileId)

        val membersArray = JSONArray()
        members.forEach { membersArray.put(it.toJson()) }
        root.put("members", membersArray)

        val tasksArray = JSONArray()
        tasks.forEach { tasksArray.put(it.toJson()) }
        root.put("tasks", tasksArray)

        return root.toString(2)
    }

    companion object {
        fun fromJsonString(json: String, driveFileIdFallback: String = ""): SharedTodoListFile {
            val root = JSONObject(json)
            val membersList = mutableListOf<ListMember>()
            val membersArray = root.optJSONArray("members")
            if (membersArray != null) {
                for (i in 0 until membersArray.length()) {
                    val obj = membersArray.getJSONObject(i)
                    membersList.add(ListMember.fromJson(obj))
                }
            }

            val tasksList = mutableListOf<SharedTaskItem>()
            val tasksArray = root.optJSONArray("tasks")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val obj = tasksArray.getJSONObject(i)
                    tasksList.add(SharedTaskItem.fromJson(obj))
                }
            }

            return SharedTodoListFile(
                id = root.optString("id", ""),
                name = root.optString("name", ""),
                ownerEmail = root.optString("ownerEmail", ""),
                ownerName = root.optString("ownerName", ""),
                description = root.optString("description", ""),
                category = root.optString("category", "Shared"),
                icon = root.optString("icon", "checklist"),
                colorHex = root.optString("colorHex", "#6366F1"),
                createdAt = root.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = root.optLong("updatedAt", System.currentTimeMillis()),
                driveFileId = root.optString("driveFileId", driveFileIdFallback),
                members = membersList,
                tasks = tasksList
            )
        }
    }
}
