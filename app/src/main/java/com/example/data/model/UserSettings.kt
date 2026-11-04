package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

val DEFAULT_CATEGORIES = listOf(
    "Personal", "Errands", "Home", "Travel", "Shopping", "Work", "Health", "Projects"
)

@IgnoreExtraProperties
data class UserSettings(
    val userId: String = "",
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val biometricLockEnabled: Boolean = false,
    val defaultSound: String = "chime",
    val categories: List<String> = emptyList(),
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun getResolvedCategories(): List<String> =
        if (categories.isNotEmpty()) categories else DEFAULT_CATEGORIES

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Personal", "Errands", "Home", "Travel", "Shopping", "Work", "Health", "Projects"
        )
    }
}
