package com.example.data.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DriveAuthResult {
    data class Success(val token: String, val account: GoogleSignInAccount) : DriveAuthResult
    data class NeedsUserConsent(val intent: Intent) : DriveAuthResult
    data class NeedsSignIn(val signInIntent: Intent) : DriveAuthResult
    data class Error(val message: String, val isRecoverable: Boolean = false) : DriveAuthResult
}

object GoogleDriveAuthHelper {
    private const val TAG = "DriveAuthHelper"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    private const val TOKEN_SCOPE_STRING = "oauth2:https://www.googleapis.com/auth/drive"

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun hasDrivePermission(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DRIVE_SCOPE))
    }

    fun getCurrentGoogleAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Extracts the signed-in Google account from the ActivityResult Intent.
     * This saves the account into GoogleSignIn.getLastSignedInAccount(context).
     */
    fun handleSignInResult(intent: Intent?): GoogleSignInAccount? {
        if (intent == null) return null
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            task.getResult(com.google.android.gms.common.api.ApiException::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed retrieving signed in account from intent: ${e.message}")
            null
        }
    }

    /**
     * Obtains a valid OAuth token for Google Drive API v3.
     * Must be called from a coroutine (runs on Dispatchers.IO).
     */
    suspend fun getAccessToken(context: Context): DriveAuthResult = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null || account.account == null) {
            val signInIntent = getGoogleSignInClient(context).signInIntent
            return@withContext DriveAuthResult.NeedsSignIn(signInIntent)
        }

        try {
            val token = GoogleAuthUtil.getToken(
                context.applicationContext,
                account.account!!,
                TOKEN_SCOPE_STRING
            )
            return@withContext DriveAuthResult.Success(token, account)
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "User consent needed for Drive scope: ${e.message}")
            val recoveryIntent = e.intent
            if (recoveryIntent != null) {
                return@withContext DriveAuthResult.NeedsUserConsent(recoveryIntent)
            } else {
                return@withContext DriveAuthResult.NeedsSignIn(getGoogleSignInClient(context).signInIntent)
            }
        } catch (e: GoogleAuthException) {
            Log.e(TAG, "GoogleAuthException getting token", e)
            return@withContext DriveAuthResult.Error(
                message = "Authentication failed: ${e.message}. Please reconnect your Google account.",
                isRecoverable = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting Drive token", e)
            return@withContext DriveAuthResult.Error(
                message = e.localizedMessage ?: "Failed to connect to Google Drive",
                isRecoverable = false
            )
        }
    }

    /**
     * Clears cached token when Google Drive API returns 401 Unauthorized or token expires.
     */
    suspend fun invalidateToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context.applicationContext, token)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing token", e)
        }
    }

    /**
     * Signs out of Google Drive client.
     */
    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        getGoogleSignInClient(context).signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
