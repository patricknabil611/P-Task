package com.example.data.drive

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object DriveColorPalette {

    // 12 high-contrast vibrant colors designed specifically for white text readability
    val PALETTE_12 = listOf(
        "#4F46E5", // Indigo
        "#1D4ED8", // Royal Blue
        "#0F766E", // Deep Teal
        "#047857", // Emerald Green
        "#15803D", // Forest Green
        "#B45309", // Warm Amber
        "#C2410C", // Rich Orange
        "#B91C1C", // Crimson Red
        "#BE123C", // Deep Rose
        "#BE185D", // Berry Pink
        "#7E22CE", // Deep Purple
        "#0E7490"  // Dark Cyan
    )

    private const val PREFS_NAME = "ptask_drive_prefs"
    private const val KEY_CUSTOM_COLOR = "custom_avatar_color_"

    /**
     * Determines a deterministic high-contrast color from an email address.
     * If existing colors in the shared list collide, picks the next available color from the 12-palette.
     */
    fun getDeterministicColor(email: String, existingColors: Collection<String> = emptyList()): String {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return PALETTE_12[0]

        val hash = abs(cleanEmail.hashCode())
        val startIndex = hash % PALETTE_12.size

        // Look for an unused color starting from hash index
        for (i in PALETTE_12.indices) {
            val candidate = PALETTE_12[(startIndex + i) % PALETTE_12.size]
            if (candidate !in existingColors) {
                return candidate
            }
        }
        return PALETTE_12[startIndex]
    }

    /**
     * Parse hex string safely into Compose Color, fallback to first palette color.
     */
    fun parseColor(hex: String?): Color {
        if (hex.isNullOrBlank()) return Color(0xFF4F46E5)
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color(0xFF4F46E5)
        }
    }

    /**
     * Save customized user avatar color in local preferences.
     */
    fun saveUserColor(context: Context, email: String, colorHex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_COLOR + email.lowercase(), colorHex).apply()
    }

    /**
     * Get customized user avatar color, or compute deterministic fallback.
     */
    fun getUserColor(context: Context, email: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_COLOR + email.lowercase(), null)
        return if (!saved.isNullOrBlank()) saved else getDeterministicColor(email)
    }
}
