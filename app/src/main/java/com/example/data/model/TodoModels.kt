package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TodoList(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val iconKey: String = "list",
    val colorHex: String = "#4F46E5",
    val isLocked: Boolean = false,
    val isShared: Boolean = false,
    val driveFileId: String = "",
    val ownerEmail: String = "",
    val isReadOnly: Boolean = false,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val listId: String = "",
    val title: String = "",
    val notes: String = "",
    val priority: String = "MEDIUM",
    val isCompleted: Boolean = false,
    val dueDateEpoch: Long = 0L,
    val reminderEpoch: Long = 0L,
    val reminderSoundKey: String = "chime",
    val category: String = "General",
    val updatedByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserSettings(
    val userId: String = "",
    val defaultSoundKey: String = "chime",
    val useDarkTheme: Boolean = false,
    val biometricEnabled: Boolean = true,
    val customCategories: List<String> = DEFAULT_CATEGORIES,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

val DEFAULT_CATEGORIES = listOf("General", "Work", "Personal", "Shopping", "Health")
