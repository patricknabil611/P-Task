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
import org.junit.Test

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
}
