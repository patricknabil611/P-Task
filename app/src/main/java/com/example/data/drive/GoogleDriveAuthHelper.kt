package com.example.data.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DriveAuthResult {
    data class Success(val account: GoogleSignInAccount, val accessToken: String) : DriveAuthResult
    data class NeedsConsent(val consentIntent: Intent) : DriveAuthResult
    data class Error(val message: String) : DriveAuthResult
}

@Suppress("DEPRECATION")
class GoogleDriveAuthHelper(private val context: Context) {

    companion object {
        const val DRIVE_SCOPE_URI = "https://www.googleapis.com/auth/drive"
        private const val OAUTH_TOKEN_SCOPE = "oauth2:$DRIVE_SCOPE_URI"
    }

    private fun buildClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE_URI))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = buildClient().signInIntent

    fun getConnectedAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, Scope(DRIVE_SCOPE_URI))) {
            account
        } else {
            null
        }
    }

    fun isConnected(): Boolean = getConnectedAccount() != null

    suspend fun getAccessToken(account: GoogleSignInAccount? = getConnectedAccount()): DriveAuthResult {
        val target = account ?: return DriveAuthResult.NeedsConsent(getSignInIntent())
        val androidAccount = target.account ?: return DriveAuthResult.NeedsConsent(getSignInIntent())
        return withContext(Dispatchers.IO) {
            try {
                val token = GoogleAuthUtil.getToken(context, androidAccount, OAUTH_TOKEN_SCOPE)
                DriveAuthResult.Success(target, token)
            } catch (e: UserRecoverableAuthException) {
                val recoverIntent = e.intent
                if (recoverIntent != null) {
                    DriveAuthResult.NeedsConsent(recoverIntent)
                } else {
                    DriveAuthResult.Error(e.localizedMessage ?: "Google Drive consent required.")
                }
            } catch (e: Exception) {
                DriveAuthResult.Error(e.localizedMessage ?: "Unable to obtain Google Drive token.")
            }
        }
    }

    suspend fun invalidateToken(token: String) {
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.clearToken(context, token)
            } catch (_: Exception) {
            }
        }
    }

    fun handleSignInResultIntent(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            buildClient().signOut()
        } catch (_: Exception) {
        }
    }
}
