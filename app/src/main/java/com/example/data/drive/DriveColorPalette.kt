package com.example.data.drive

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

object DriveColorPalette {
    private const val PREFS_NAME = "drive_color_prefs"
    private const val KEY_CUSTOM_COLOR = "custom_color_"

    val PRESET_COLORS = listOf(
        "#4F46E5", // Indigo
        "#0EA5E9", // Sky
        "#10B981", // Emerald
        "#F59E0B", // Amber
        "#EF4444", // Rose
        "#8B5CF6", // Violet
        "#EC4899", // Pink
        "#14B8A6"  // Teal
    )

    fun parseColor(hex: String, fallback: Color = Color(0xFF4F46E5)): Color {
        return try {
            Color(hex.toColorInt())
        } catch (_: Exception) {
            fallback
        }
    }

    fun getColorForMember(context: Context, email: String): Color {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_COLOR + email.lowercase(), null)
        if (!saved.isNullOrBlank()) {
            return parseColor(saved)
        }
        val idx = (email.lowercase().hashCode() and 0x7FFFFFFF) % PRESET_COLORS.size
        return parseColor(PRESET_COLORS[idx])
    }

    fun setColorForMember(context: Context, email: String, colorHex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_COLOR + email.lowercase(), colorHex).apply()
    }
}
