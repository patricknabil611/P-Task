package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DEFAULT_CATEGORIES
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TodoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    companion object {
        fun create(context: Context): TodoRepository {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            return TodoRepository(FirebaseFirestore.getInstance(dbId), Firebase.auth)
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun newDocId(): String = UUID.randomUUID().toString().replace("-", "")

    fun observeLists(): Flow<List<TodoList>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/lists"
        emitAll(
            db.collection("users").document(uid).collection("lists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TodoList::class.java)
                        .sortedWith(compareBy<TodoList> { it.sortOrder }.thenBy { it.title.lowercase() })
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTasks(): Flow<List<TaskItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/tasks"
        emitAll(
            db.collection("users").document(uid).collection("tasks")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(TaskItem::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSettings(): Flow<UserSettings> = flow {
        val uid = requireUserId()
        val path = "users/$uid/settings/preferences"
        emitAll(
            db.collection("users").document(uid).collection("settings").document("preferences")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserSettings::class.java) ?: UserSettings(userId = uid)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDefaultListIfEmpty() {
        val uid = requireUserId()
        val listsRef = db.collection("users").document(uid).collection("lists")
        try {
            val existing = listsRef.limit(1).get().await()
            if (existing.isEmpty) {
                createList(
                    TodoList(
                        title = "Personal",
                        iconKey = "person",
                        colorHex = "#4F46E5",
                        ownerEmail = auth.currentUser?.email.orEmpty()
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid/lists")
        }
    }

    suspend fun createList(list: TodoList): Result<String> {
        val uid = requireUserId()
        val listId = if (list.id.isNotBlank() && list.id.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            list.id
        } else {
            newDocId()
        }
        val docRef = db.collection("users").document(uid).collection("lists").document(listId)
        val payload = mapOf(
            "userId" to uid,
            "title" to list.title.trim().take(120).ifEmpty { "Untitled List" },
            "iconKey" to list.iconKey.ifBlank { "list" }.take(40),
            "colorHex" to list.colorHex.ifBlank { "#4F46E5" }.take(16),
            "isLocked" to list.isLocked,
            "isShared" to list.isShared,
            "driveFileId" to list.driveFileId.take(200),
            "ownerEmail" to list.ownerEmail.ifBlank { auth.currentUser?.email.orEmpty() }.take(200),
            "isReadOnly" to list.isReadOnly,
            "sortOrder" to list.sortOrder,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(listId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateList(list: TodoList): Result<Unit> {
        val uid = requireUserId()
        require(list.id.isNotBlank()) { "List ID is required for update" }
        val docRef = db.collection("users").document(uid).collection("lists").document(list.id)
        val updates = mapOf(
            "title" to list.title.trim().take(120).ifEmpty { "Untitled List" },
            "iconKey" to list.iconKey.ifBlank { "list" }.take(40),
            "colorHex" to list.colorHex.ifBlank { "#4F46E5" }.take(16),
            "isLocked" to list.isLocked,
            "isShared" to list.isShared,
            "driveFileId" to list.driveFileId.take(200),
            "ownerEmail" to list.ownerEmail.take(200),
            "isReadOnly" to list.isReadOnly,
            "sortOrder" to list.sortOrder,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteList(listId: String): Result<Unit> {
        val uid = requireUserId()
        val listRef = db.collection("users").document(uid).collection("lists").document(listId)
        return try {
            val tasksSnap = db.collection("users").document(uid).collection("tasks")
                .whereEqualTo("listId", listId)
                .get()
                .await()
            val batch = db.batch()
            for (doc in tasksSnap.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(listRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, listRef.path)
            Result.failure(e)
        }
    }

    suspend fun createTask(task: TaskItem): Result<String> {
        val uid = requireUserId()
        val taskId = if (task.id.isNotBlank() && task.id.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            task.id
        } else {
            newDocId()
        }
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        val priority = if (task.priority in listOf("URGENT", "HIGH", "MEDIUM", "LOW")) task.priority else "MEDIUM"
        val payload = mapOf(
            "userId" to uid,
            "listId" to task.listId.ifBlank { "default" }.take(128),
            "title" to task.title.trim().take(250).ifEmpty { "Untitled Task" },
            "notes" to task.notes.take(2000),
            "priority" to priority,
            "isCompleted" to task.isCompleted,
            "dueDateEpoch" to task.dueDateEpoch.coerceAtLeast(0L),
            "reminderEpoch" to task.reminderEpoch.coerceAtLeast(0L),
            "reminderSoundKey" to task.reminderSoundKey.ifBlank { "chime" }.take(40),
            "category" to task.category.ifBlank { "General" }.take(60),
            "updatedByEmail" to task.updatedByEmail.take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTask(task: TaskItem): Result<Unit> {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID is required for update" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
        val priority = if (task.priority in listOf("URGENT", "HIGH", "MEDIUM", "LOW")) task.priority else "MEDIUM"
        val updates = mapOf(
            "listId" to task.listId.ifBlank { "default" }.take(128),
            "title" to task.title.trim().take(250).ifEmpty { "Untitled Task" },
            "notes" to task.notes.take(2000),
            "priority" to priority,
            "isCompleted" to task.isCompleted,
            "dueDateEpoch" to task.dueDateEpoch.coerceAtLeast(0L),
            "reminderEpoch" to task.reminderEpoch.coerceAtLeast(0L),
            "reminderSoundKey" to task.reminderSoundKey.ifBlank { "chime" }.take(40),
            "category" to task.category.ifBlank { "General" }.take(60),
            "updatedByEmail" to task.updatedByEmail.take(200),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markTaskCompletedById(taskId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        return try {
            docRef.update(
                mapOf(
                    "isCompleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveSettings(settings: UserSettings): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
        val categories = settings.customCategories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(30)
            .ifEmpty { DEFAULT_CATEGORIES }
        val payload = mapOf(
            "userId" to uid,
            "defaultSoundKey" to settings.defaultSoundKey.ifBlank { "chime" }.take(40),
            "useDarkTheme" to settings.useDarkTheme,
            "biometricEnabled" to settings.biometricEnabled,
            "customCategories" to categories,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun restoreSnapshot(lists: List<TodoList>, tasks: List<TaskItem>): Result<Unit> {
        val uid = requireUserId()
        return try {
            for (list in lists) {
                createList(list.copy(userId = uid))
            }
            for (task in tasks) {
                createTask(task.copy(userId = uid))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
