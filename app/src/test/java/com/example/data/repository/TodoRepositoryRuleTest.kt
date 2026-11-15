package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.data.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TodoRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createListAndTask_authenticatedOwner_succeedsAndEmitsRealtime() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = TodoRepository(firestore, auth)

        val listResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createList(
                TodoList(
                    title = "Work Sprint",
                    iconKey = "work",
                    colorHex = "#4F46E5",
                    ownerEmail = ALICE_EMAIL
                )
            )
        }
        assertTrue(listResult.isSuccess)
        val listId = listResult.getOrThrow()
        assertTrue(listId.isNotEmpty())

        val taskResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(
                TaskItem(
                    listId = listId,
                    title = "Ship v26.06.26.2",
                    priority = "HIGH",
                    category = "Work"
                )
            )
        }
        assertTrue(taskResult.isSuccess)
        val taskId = taskResult.getOrThrow()

        val observedLists = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLists().first { lists -> lists.any { it.id == listId } }
        }
        assertTrue(observedLists.any { it.id == listId && it.title == "Work Sprint" })

        val observedTasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks().first { tasks -> tasks.any { it.id == taskId } }
        }
        assertTrue(observedTasks.any { it.id == taskId && it.title == "Ship v26.06.26.2" })

        val settingsSave = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveSettings(UserSettings(useDarkTheme = true, biometricEnabled = false))
        }
        assertTrue(settingsSave.isSuccess)
    }

    @Test
    fun crossUserReadAndWrite_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TodoRepository(firestore, auth)
        val listId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createList(TodoList(title = "Alice Private", ownerEmail = ALICE_EMAIL)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("lists").document(listId).get().await()
            }
            fail("Expected cross-user read to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun unauthenticatedRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TodoRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createList(TodoList(title = "Alice Synced", ownerEmail = ALICE_EMAIL)).getOrThrow()
        }

        auth.signOut()

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).collection("lists").get().await()
            }
            fail("Expected unauthenticated read to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_ptask@test.com"
        const val BOB_EMAIL = "bob_ptask@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
