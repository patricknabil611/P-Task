package com.example

import com.example.data.drive.SharedListSyncManager
import com.example.data.model.ListMember
import com.example.data.model.SharedTaskItem
import com.example.data.model.SharedTodoListFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedListSyncTest {

    @Test
    fun toTodoList_readOnlySharedList_alwaysDisablesBiometricLock() {
        val sharedFile = SharedTodoListFile(
            id = "drive_file_1",
            title = "Team Roadmap",
            iconKey = "work",
            colorHex = "#0EA5E9",
            ownerEmail = "owner@example.com",
            members = listOf(
                ListMember(email = "owner@example.com", role = "owner"),
                ListMember(email = "viewer@example.com", role = "reader")
            )
        )

        val todoList = sharedFile.toTodoList(isReadOnly = true)

        assertEquals("drive_file_1", todoList.id)
        assertEquals("drive_file_1", todoList.driveFileId)
        assertTrue(todoList.isShared)
        assertTrue(todoList.isReadOnly)
        assertFalse(todoList.isLocked)
    }

    @Test
    fun mergeListFiles_keepsNewestByTimestamp_andDeduplicatesBlankAndDuplicateIds() {
        val oldTask = SharedTaskItem(id = "t1", title = "Old title")
        val newTask = SharedTaskItem(id = "t1", title = "Updated title")

        val existing = listOf(
            SharedTodoListFile(
                id = "file_a",
                title = "Groceries v1",
                updatedAtEpoch = 1000L,
                tasks = listOf(oldTask)
            ),
            SharedTodoListFile(
                id = "file_b",
                title = "Chores",
                updatedAtEpoch = 2000L
            ),
            SharedTodoListFile(
                id = "",
                title = "Corrupt blank ID"
            )
        )

        val incoming = listOf(
            SharedTodoListFile(
                id = "file_a",
                title = "Groceries v2",
                updatedAtEpoch = 2500L,
                tasks = listOf(newTask)
            ),
            SharedTodoListFile(
                id = "file_b",
                title = "Chores Stale",
                updatedAtEpoch = 1500L
            ),
            SharedTodoListFile(
                id = "file_c",
                title = "Trip Packing",
                updatedAtEpoch = 3000L
            )
        )

        val merged = SharedListSyncManager.mergeListFiles(existing, incoming)

        assertEquals(3, merged.size)
        assertEquals(listOf("file_a", "file_b", "file_c"), merged.map { it.id })
        assertEquals("Groceries v2", merged.first { it.id == "file_a" }.title)
        assertEquals("Updated title", merged.first { it.id == "file_a" }.tasks.single().title)
        assertEquals("Chores", merged.first { it.id == "file_b" }.title)
    }
}
