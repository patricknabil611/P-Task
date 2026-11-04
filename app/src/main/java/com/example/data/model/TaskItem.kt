package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TaskItem(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val listId: String = "",
    val title: String = "",
    val notes: String = "",
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH, URGENT
    val dueDateEpoch: Long = 0L,
    val reminderEpoch: Long = 0L,
    val reminderSound: String = "chime", // chime, bell, digital_pulse, gentle_harp, cosmic_beep
    val category: String = "General",
    val completedAt: Long = 0L,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("userId", userId)
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
    }
}
