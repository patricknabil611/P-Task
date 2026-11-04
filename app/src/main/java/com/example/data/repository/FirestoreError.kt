package com.example.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    LIST
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String
) {
    val errorDetails = JSONObject().apply {
        put("operation", operationType.name)
        put("path", path)
        put("exceptionClass", exception.javaClass.simpleName)
        put("message", exception.message ?: "Unknown error")
        if (exception is FirebaseFirestoreException) {
            put("code", exception.code.name)
        }
    }
    Log.e("FirestoreError", errorDetails.toString(2), exception)
}
