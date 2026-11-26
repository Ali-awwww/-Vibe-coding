package com.example.domain.repository

import com.example.domain.model.UserAccount
import kotlinx.coroutines.flow.Flow

/**
 * Expected signup or unverified sign-in outcome when Firebase Auth creates/finds the account
 * and requires email verification before granting an authenticated session.
 */
class EmailConfirmationRequiredException(message: String) : Exception(message)

/**
 * Thrown when Firebase Authentication is not configured in the current build.
 */
class FirebaseAuthNotConfiguredException(
    message: String = "Firebase Authentication is not configured in this build."
) : IllegalStateException(message)

data class PasswordRecoveryCredentials(
    val oobCode: String? = null,
    val tokenHash: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long? = null
)

interface AuthRepository {
    val currentUser: Flow<UserAccount?>
    fun getCurrentUserSync(): UserAccount?
    suspend fun signUp(email: String, password: String, displayName: String, organization: String): Result<UserAccount>
    suspend fun signIn(email: String, password: String): Result<UserAccount>
    suspend fun signOut()
    suspend fun requestPasswordRecovery(email: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Password recovery is not implemented by this repository."))

    suspend fun completePasswordRecovery(
        credentials: PasswordRecoveryCredentials,
        newPassword: String
    ): Result<Unit> =
        Result.failure(UnsupportedOperationException("Verified password recovery is not implemented by this repository."))

    suspend fun resetPassword(email: String, newPassword: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
}
