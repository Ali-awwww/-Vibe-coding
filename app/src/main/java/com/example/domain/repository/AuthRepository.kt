package com.example.domain.repository

import com.example.domain.model.UserAccount
import kotlinx.coroutines.flow.Flow

/**
 * Expected signup outcome when Supabase accepts the account but requires email verification
 * before issuing an authenticated session.
 */
class EmailConfirmationRequiredException(message: String) : Exception(message)

interface AuthRepository {
    val currentUser: Flow<UserAccount?>
    fun getCurrentUserSync(): UserAccount?
    suspend fun signUp(email: String, password: String, displayName: String, organization: String): Result<UserAccount>
    suspend fun signIn(email: String, password: String): Result<UserAccount>
    suspend fun signOut()
    suspend fun resetPassword(email: String, newPassword: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
}
