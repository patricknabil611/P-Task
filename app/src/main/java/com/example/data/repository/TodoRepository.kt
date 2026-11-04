package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.TaskItem
import com.example.data.model.TodoList
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TodoRepository(
    private val db: FirebaseFirestore
) {
    companion object {
        fun create(context: Context): TodoRepository {
            val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            return TodoRepository(firestore)
        }
    }

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // Observe all user lists in real time
    fun observeLists(): Flow<List<TodoList>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/lists"
        emitAll(
            db.collection("users").document(uid).collection("lists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TodoList::class.java).sortedWith(
                        compareByDescending<TodoList> { it.isPinned }
                            .thenBy { it.orderIndex }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    // Observe tasks for a specific list, or all tasks if listId is empty
    fun observeTasks(listId: String? = null): Flow<List<TaskItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/tasks"
        val query = if (!listId.isNullOrBlank()) {
            db.collection("users").document(uid).collection("tasks")
                .whereEqualTo("listId", listId)
        } else {
            db.collection("users").document(uid).collection("tasks")
        }

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TaskItem::class.java).sortedWith(
                        compareBy<TaskItem> { it.isCompleted }
                            .thenByDescending {
                                when (it.priority) {
                                    "URGENT" -> 4
                                    "HIGH" -> 3
                                    "MEDIUM" -> 2
                                    else -> 1
                                }
                            }
                            .thenBy { if (it.dueDateEpoch > 0) it.dueDateEpoch else Long.MAX_VALUE }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    // Observe user settings
    fun observeSettings(): Flow<UserSettings?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(UserSettings::class.java) else null
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.READ, docRef.path)
                    throw error
                }
        )
    }

    // Seed default lists if user has none
    suspend fun seedDefaultListsIfEmpty() {
        val uid = requireUserId()
        val listsRef = db.collection("users").document(uid).collection("lists")
        val existing = listsRef.limit(1).get().await()
        if (existing.isEmpty) {
            val defaults = listOf(
                TodoList(
                    title = "Personal Todo daily",
                    description = "Daily personal goals, habits, and quick wins",
                    category = "Personal",
                    icon = "today",
                    colorHex = "#6366F1",
                    isPinned = true,
                    orderIndex = 1
                ),
                TodoList(
                    title = "Togo list",
                    description = "Errands on the go, pharmacy, dry cleaners, post office",
                    category = "Errands",
                    icon = "directions_run",
                    colorHex = "#10B981",
                    isPinned = true,
                    orderIndex = 2
                ),
                TodoList(
                    title = "Todo El sha2a",
                    description = "Apartment chores: cleaning, organizing, home maintenance",
                    category = "Home",
                    icon = "home",
                    colorHex = "#F59E0B",
                    isPinned = false,
                    orderIndex = 3
                ),
                TodoList(
                    title = "To travel List",
                    description = "Trip planning, packing checklist, passports, tickets",
                    category = "Travel",
                    icon = "flight",
                    colorHex = "#06B6D4",
                    isPinned = false,
                    orderIndex = 4
                ),
                TodoList(
                    title = "To buy",
                    description = "Groceries, tech, supplies, and shopping items",
                    category = "Shopping",
                    icon = "shopping_cart",
                    colorHex = "#EC4899",
                    isPinned = false,
                    orderIndex = 5
                )
            )

            for (list in defaults) {
                addTodoList(list)
            }
        }
    }

    suspend fun addTodoList(list: TodoList): String {
        val uid = requireUserId()
        val collection = db.collection("users").document(uid).collection("lists")
        val docRef = collection.document()
        val payload = list.toMap().filterValues { it != null } + mapOf(
            "userId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateTodoList(list: TodoList) {
        val uid = requireUserId()
        require(list.id.isNotBlank()) { "List ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("lists").document(list.id)
        val payload = list.toMap().filterValues { it != null } + mapOf(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTodoList(listId: String) {
        val uid = requireUserId()
        require(listId.isNotBlank())
        val docRef = db.collection("users").document(uid).collection("lists").document(listId)
        try {
            docRef.delete().await()
            // Delete associated tasks in list
            val tasksSnapshot = db.collection("users").document(uid).collection("tasks")
                .whereEqualTo("listId", listId).get().await()
            for (task in tasksSnapshot.documents) {
                task.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun addTask(task: TaskItem): String {
        val uid = requireUserId()
        val collection = db.collection("users").document(uid).collection("tasks")
        val docRef = collection.document()
        val payload = task.toMap().filterValues { it != null } + mapOf(
            "userId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateTask(task: TaskItem) {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
        val payload = task.toMap().filterValues { it != null } + mapOf(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleTaskCompletion(task: TaskItem) {
        val newCompleted = !task.isCompleted
        val newCompletedAt = if (newCompleted) System.currentTimeMillis() else 0L
        updateTask(task.copy(isCompleted = newCompleted, completedAt = newCompletedAt))
    }

    suspend fun markTaskCompletedById(taskId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        try {
            docRef.update(
                mapOf(
                    "isCompleted" to true,
                    "completedAt" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun renameCategoryAcrossLists(oldCategory: String, newCategory: String) {
        val uid = requireUserId()
        val listsRef = db.collection("users").document(uid).collection("lists")
        val query = listsRef.whereEqualTo("category", oldCategory).get().await()
        if (!query.isEmpty) {
            val batch = db.batch()
            for (doc in query.documents) {
                batch.update(doc.reference, mapOf(
                    "category" to newCategory,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            batch.commit().await()
        }
    }

    suspend fun clearCompletedTasksInList(listId: String) {
        val uid = requireUserId()
        val tasksRef = db.collection("users").document(uid).collection("tasks")
        val query = tasksRef.whereEqualTo("listId", listId).whereEqualTo("isCompleted", true).get().await()
        if (!query.isEmpty) {
            val batch = db.batch()
            for (doc in query.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
    }

    suspend fun deleteTask(taskId: String) {
        val uid = requireUserId()
        require(taskId.isNotBlank())
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun updateSettings(settings: UserSettings) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
        val payload = mapOf(
            "userId" to uid,
            "themeMode" to settings.themeMode,
            "biometricLockEnabled" to settings.biometricLockEnabled,
            "defaultSound" to settings.defaultSound,
            "categories" to settings.categories,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }
}
