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
import java.util.Date

data class ParsedTaskResult(
    val title: String,
    val targetList: String,
    val priority: String,
    val dueDateOffsetHours: Int,
    val notes: String,
    val category: String
)

object GeminiTaskParser {
    private const val TAG = "GeminiTaskParser"

    private val taskSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Clean, concise title of the task"),
            "targetList" to Schema.string(description = "Best matching list title (e.g. 'Personal Todo daily', 'Togo list', 'Todo El sha2a', 'To travel List', 'To buy')"),
            "priority" to Schema.string(description = "One of LOW, MEDIUM, HIGH, URGENT"),
            "dueDateOffsetHours" to Schema.integer(description = "Hours from right now when this task is due (e.g. 24 for tomorrow, 0 if no specific due date)"),
            "notes" to Schema.string(description = "Additional context or details mentioned"),
            "category" to Schema.string(description = "Category tag like Personal, Home, Errands, Travel, Shopping, Work")
        ),
        optionalProperties = listOf("notes")
    )

    private val config = generationConfig {
        responseMimeType = "application/json"
        responseSchema = taskSchema
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun parseNaturalLanguageTask(
        userInput: String,
        existingListTitles: List<String>,
        availableCategories: List<String> = emptyList()
    ): Result<ParsedTaskResult> {
        return try {
            val listsContext = if (existingListTitles.isNotEmpty()) {
                "Existing user lists: ${existingListTitles.joinToString(", ")}."
            } else {
                "Standard lists: Personal Todo daily, Togo list, Todo El sha2a, To travel List, To buy."
            }

            val categoriesContext = if (availableCategories.isNotEmpty()) {
                "Available categories: ${availableCategories.joinToString(", ")}."
            } else {
                "Available categories: Personal, Home, Errands, Travel, Shopping, Work."
            }

            val prompt = """
                You are a smart task parser for P Task todo app.
                User input: "$userInput"
                Current date/time context: ${Date()}
                $listsContext
                $categoriesContext
                
                Analyze the input and extract:
                1. title: The main action or item.
                2. targetList: The most appropriate list from the user's lists (e.g. if household chore or cleaning, use 'Todo El sha2a'; if shopping/buying, use 'To buy'; if travel/flight/packing, use 'To travel List'; if quick errand/on the go, use 'Togo list'; otherwise 'Personal Todo daily').
                3. priority: LOW, MEDIUM, HIGH, or URGENT based on urgency words.
                4. dueDateOffsetHours: estimated hours from now (e.g., if user says 'tomorrow' give 24, 'tonight' give 8, 'in 2 hours' give 2, or 0 if none).
                5. notes: any details, instructions, or subitems.
                6. category: pick the best matching category from the available categories list.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }

            val jsonString = response.text
                ?: throw IllegalStateException("Gemini model returned empty response.")

            val json = JSONObject(jsonString)
            val fallbackCategory = availableCategories.firstOrNull() ?: "Personal"
            val parsedCategory = json.optString("category", fallbackCategory)
            val resolvedCategory = if (availableCategories.isNotEmpty() && !availableCategories.contains(parsedCategory)) {
                availableCategories.firstOrNull { it.equals(parsedCategory, ignoreCase = true) } ?: fallbackCategory
            } else {
                parsedCategory
            }

            val result = ParsedTaskResult(
                title = json.optString("title", userInput),
                targetList = json.optString("targetList", "Personal Todo daily"),
                priority = json.optString("priority", "MEDIUM").uppercase().let {
                    if (it in listOf("LOW", "MEDIUM", "HIGH", "URGENT")) it else "MEDIUM"
                },
                dueDateOffsetHours = json.optInt("dueDateOffsetHours", 0),
                notes = json.optString("notes", ""),
                category = resolvedCategory
            )

            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini NLP parsing timed out", e)
            Result.failure(Exception("AI task parsing timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini NLP parsing failed", e)
            Result.failure(e)
        }
    }
}
