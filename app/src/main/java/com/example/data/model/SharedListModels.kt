package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ListMember(
    val email: String = "",
    val role: String = "writer",
    val displayName: String = ""
)

@JsonClass(generateAdapter = true)
data class SharedTaskItem(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val priority: String = "MEDIUM",
    val isCompleted: Boolean = false,
    val dueDateEpoch: Long = 0L,
    val reminderEpoch: Long = 0L,
    val reminderSoundKey: String = "chime",
    val category: String = "General",
    val updatedByEmail: String = "",
    val updatedAtEpoch: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SharedTodoListFile(
    val id: String = "",
    val title: String = "",
    val iconKey: String = "list",
    val colorHex: String = "#4F46E5",
    val ownerEmail: String = "",
    val updatedAtEpoch: Long = 0L,
    val updatedByEmail: String = "",
    val tasks: List<SharedTaskItem> = emptyList(),
    val members: List<ListMember> = emptyList()
) {
    fun toTodoList(isReadOnly: Boolean): TodoList = TodoList(
        id = id,
        title = title.ifBlank { "Shared List" },
        iconKey = iconKey.ifBlank { "list" },
        colorHex = colorHex.ifBlank { "#4F46E5" },
        isLocked = false,
        isShared = true,
        driveFileId = id,
        ownerEmail = ownerEmail,
        isReadOnly = isReadOnly
    )

    fun toTaskItems(): List<TaskItem> = tasks.map { item ->
        TaskItem(
            id = item.id,
            listId = id,
            title = item.title,
            notes = item.notes,
            priority = item.priority.ifBlank { "MEDIUM" },
            isCompleted = item.isCompleted,
            dueDateEpoch = item.dueDateEpoch,
            reminderEpoch = item.reminderEpoch,
            reminderSoundKey = item.reminderSoundKey.ifBlank { "chime" },
            category = item.category.ifBlank { "General" },
            updatedByEmail = item.updatedByEmail
        )
    }
}
