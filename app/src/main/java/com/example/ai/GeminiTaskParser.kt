package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ParsedTaskResult(
    val title: String,
    val priority: String = "MEDIUM",
    val category: String = "General",
    val notes: String = "",
    val dueOffsetDays: Int? = null
)

class GeminiTaskParser {

    private val schema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Clean, concise action item title"),
            "priority" to Schema.enumeration(
                values = listOf("URGENT", "HIGH", "MEDIUM", "LOW"),
                description = "Task urgency level"
            ),
            "category" to Schema.string(description = "Best matching category"),
            "notes" to Schema.string(description = "Optional extra details"),
            "dueOffsetDays" to Schema.integer(
                description = "Days from today if a due date was mentioned (0=today, 1=tomorrow), or null",
                nullable = true
            )
        ),
        optionalProperties = listOf("notes", "dueOffsetDays")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    suspend fun parseTaskInput(
        naturalText: String,
        availableCategories: List<String>
    ): Result<ParsedTaskResult> {
        val cleaned = naturalText.trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter or speak a task first."))
        }

        val prompt = buildString {
            appendLine("Convert the following user task utterance into a structured task JSON.")
            appendLine("Available categories: ${availableCategories.joinToString(", ")}.")
            appendLine("Choose the closest category from the list above, or 'General'.")
            appendLine("Utterance: \"$cleaned\"")
        }

        return try {
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty response from Gemini.")
            val obj = JSONObject(jsonText)
            val title = obj.optString("title", cleaned).trim().ifEmpty { cleaned }
            val priority = obj.optString("priority", "MEDIUM").uppercase().let {
                if (it in listOf("URGENT", "HIGH", "MEDIUM", "LOW")) it else "MEDIUM"
            }
            val category = obj.optString("category", "General").trim().ifEmpty { "General" }
            val notes = obj.optString("notes", "").trim()
            val dueOffset = if (obj.has("dueOffsetDays") && !obj.isNull("dueOffsetDays")) {
                obj.optInt("dueOffsetDays", 0).coerceAtLeast(0)
            } else {
                null
            }
            Result.success(
                ParsedTaskResult(
                    title = title,
                    priority = priority,
                    category = category,
                    notes = notes,
                    dueOffsetDays = dueOffset
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini task parsing timed out", e)
            Result.failure(Exception("AI task parsing timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini task parsing failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI parsing failed."))
        }
    }

    private companion object {
        const val TAG = "GeminiTaskParser"
    }
}
