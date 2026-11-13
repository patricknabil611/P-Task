package com.example

import com.example.data.drive.DriveColorPalette
import com.example.data.drive.SharedListSyncManager
import com.example.data.model.ListMember
import com.example.data.model.SharedTaskItem
import com.example.data.model.SharedTodoListFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class SharedListSyncTest {

    @Test
    fun testDeterministicPalette_sameEmailYieldsSameColor() {
        val color1 = DriveColorPalette.getDeterministicColor("alice@example.com")
        val color2 = DriveColorPalette.getDeterministicColor("ALICE@EXAMPLE.COM")
        assertEquals(color1, color2)
        assertTrue(DriveColorPalette.PALETTE_12.contains(color1))
    }

    @Test
    fun testDeterministicPalette_collisionAvoidance() {
        val email = "bob@example.com"
        val firstChoice = DriveColorPalette.getDeterministicColor(email)
        val collisionAvoided = DriveColorPalette.getDeterministicColor(email, listOf(firstChoice))
        assertNotEquals(firstChoice, collisionAvoided)
        assertTrue(DriveColorPalette.PALETTE_12.contains(collisionAvoided))
    }

    @Test
    fun testJsonSerialization_roundTrip() {
        val list = SharedTodoListFile(
            id = "test_list_123",
            name = "Groceries",
            ownerEmail = "owner@example.com",
            ownerName = "Owner",
            description = "Weekly shopping",
            category = "Shopping",
            colorHex = "#4F46E5",
            driveFileId = "drive_file_999",
            members = listOf(
                ListMember(
                    email = "owner@example.com",
                    name = "Owner",
                    color = "#4F46E5",
                    role = "owner"
                ),
                ListMember(
                    email = "collaborator@example.com",
                    name = "Collab",
                    color = "#10B981",
                    role = "editor"
                )
            ),
            tasks = listOf(
                SharedTaskItem(
                    id = "task_1",
                    listId = "test_list_123",
                    title = "Apples",
                    isCompleted = false,
                    createdBy = "owner@example.com",
                    updatedBy = "owner@example.com",
                    updatedAt = 1000L
                )
            )
        )

        val jsonString = list.toJsonString()
        val parsed = SharedTodoListFile.fromJsonString(jsonString)

        assertEquals(list.id, parsed.id)
        assertEquals(list.name, parsed.name)
        assertEquals(list.ownerEmail, parsed.ownerEmail)
        assertEquals(2, parsed.members.size)
        assertEquals(1, parsed.tasks.size)
        assertEquals("Apples", parsed.tasks[0].title)
    }

    @Test
    fun test30DayTombstonePurge() {
        val now = 100_000_000_000L
        val thirtyOneDaysAgo = now - (31L * 24 * 60 * 60 * 1000L)
        val fiveDaysAgo = now - (5L * 24 * 60 * 60 * 1000L)

        val list = SharedTodoListFile(
            id = "list_purge",
            tasks = listOf(
                SharedTaskItem(id = "active_task", isDeleted = false, updatedAt = fiveDaysAgo),
                SharedTaskItem(id = "recent_deleted", isDeleted = true, updatedAt = fiveDaysAgo),
                SharedTaskItem(id = "expired_deleted", isDeleted = true, updatedAt = thirtyOneDaysAgo)
            )
        )

        val cleaned = list.purgeExpiredTombstones(now)
        assertEquals(2, cleaned.tasks.size)
        assertTrue(cleaned.tasks.any { it.id == "active_task" })
        assertTrue(cleaned.tasks.any { it.id == "recent_deleted" })
        assertFalse(cleaned.tasks.any { it.id == "expired_deleted" })
    }

    @Test
    fun testReadOnlySharedList_doesNotTriggerBiometricLock() {
        val list = SharedTodoListFile(
            id = "shared_ro_1",
            name = "Viewer List",
            ownerEmail = "owner@gmail.com"
        )
        val todoList = list.toTodoList(isReadOnly = true)
        assertFalse(todoList.isLocked)
        assertEquals("Viewer List", todoList.title)
    }

    @Test
    fun testMergeLists_newestUpdatedAtWinsAndNoDuplicateIds() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = SharedListSyncManager(context)
        val remote = SharedTodoListFile(
            id = "list_m",
            updatedAt = 2000L,
            tasks = listOf(
                SharedTaskItem(id = "t1", title = "Old Title", updatedAt = 1000L),
                SharedTaskItem(id = "t2", title = "Remote Only", updatedAt = 2000L)
            )
        )
        val local = SharedTodoListFile(
            id = "list_m",
            updatedAt = 3000L,
            tasks = listOf(
                SharedTaskItem(id = "t1", title = "New Title", updatedAt = 3000L),
                SharedTaskItem(id = "t3", title = "Local Only", updatedAt = 3000L)
            )
        )
        val merged = manager.mergeLists(remote, local)
        assertEquals(3, merged.tasks.size)
        assertEquals(3, merged.tasks.map { it.id }.distinct().size)
        assertEquals("New Title", merged.tasks.first { it.id == "t1" }.title)
    }
}
