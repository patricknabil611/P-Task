package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupRestoreManager
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("P Task", appName)
    }

    @Test
    fun `task item completion and toMap serialization`() {
        val task = TaskItem(
            id = "t1",
            title = "Test Task",
            isCompleted = true,
            completedAt = 123456789L
        )
        val map = task.toMap()
        assertEquals(true, map["isCompleted"])
        assertEquals(123456789L, map["completedAt"])
    }

    @Test
    fun `todo list pin and lock toMap serialization`() {
        val list = TodoList(
            id = "l1",
            title = "Test List",
            isPinned = true,
            isLocked = true
        )
        val map = list.toMap()
        assertEquals(true, map["isPinned"])
        assertEquals(true, map["isLocked"])
    }

    @Test
    fun `user settings categories fallback and customization`() {
        val defaultSettings = UserSettings()
        val defaultCats = defaultSettings.getResolvedCategories()
        assertTrue(defaultCats.contains("Personal"))
        assertTrue(defaultCats.contains("Home"))

        val customSettings = UserSettings(categories = listOf("Work", "Gym", "Side Projects"))
        val customCats = customSettings.getResolvedCategories()
        assertEquals(3, customCats.size)
        assertEquals("Work", customCats[0])
    }

    @Test
    fun `backup and restore serialization roundtrip with P Task`() {
        val testLists = listOf(
            TodoList(id = "list_1", title = "Todo El sha2a", category = "Home", colorHex = "#F59E0B", isPinned = true),
            TodoList(id = "list_2", title = "To travel List", category = "Travel", colorHex = "#06B6D4")
        )
        val testTasks = listOf(
            TaskItem(id = "task_1", listId = "list_1", title = "Clean air filter", priority = "HIGH", isCompleted = true, completedAt = 999999L),
            TaskItem(id = "task_2", listId = "list_2", title = "Renew passport", priority = "URGENT")
        )

        val json = BackupRestoreManager.exportToJson(testLists, testTasks)
        assertTrue(json.contains("P Task"))
        assertTrue(json.contains("Todo El sha2a"))
        assertTrue(json.contains("Clean air filter"))

        val parseResult = BackupRestoreManager.parseBackup(json)
        assertTrue(parseResult.isSuccess)
        val (restoredLists, restoredTasks) = parseResult.getOrThrow()
        assertEquals(2, restoredLists.size)
        assertEquals(2, restoredTasks.size)
        assertEquals("Todo El sha2a", restoredLists[0].title)
        assertTrue(restoredLists[0].isPinned)
        assertEquals("Clean air filter", restoredTasks[0].title)
        assertTrue(restoredTasks[0].isCompleted)
        assertEquals(999999L, restoredTasks[0].completedAt)
    }
}
