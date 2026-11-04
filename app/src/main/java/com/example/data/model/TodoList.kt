package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TodoList(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Personal",
    val icon: String = "checklist",
    val colorHex: String = "#6366F1",
    @get:PropertyName("isPinned") @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    @get:PropertyName("isLocked") @set:PropertyName("isLocked")
    var isLocked: Boolean = false,
    val orderIndex: Int = 0,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("userId", userId)
        put("title", title)
        if (description.isNotBlank()) put("description", description)
        put("category", category)
        put("icon", icon)
        put("colorHex", colorHex)
        put("isPinned", isPinned)
        put("isLocked", isLocked)
        put("orderIndex", orderIndex)
    }
}
