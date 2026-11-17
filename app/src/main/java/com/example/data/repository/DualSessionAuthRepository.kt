package com.example.data.repository

import com.example.data.remote.supabase.SupabaseIdentityLinkProvider
import com.example.data.remote.supabase.SupabaseIdentityLinkStore
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PasswordRecoveryCredentials
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Keeps Firebase as the app-wide identity while establishing a second Supabase session from the
 * exact same Google ID token. The app user ID remains the Firebase UID; the encrypted link is used
 * only when a Supabase-backed repository needs the distinct Supabase subject.
 *
 * If the secondary exchange is unavailable, Firebase sign-in remains valid. Supabase-backed
 * repositories use the link only after the stored Supabase session independently validates.
 */
class DualSessionAuthRepository(
    private val firebaseRepository: AuthRepository,
    private val supabaseRepository: AuthRepository,
    private val identityLinkStore: SupabaseIdentityLinkStore,
    private val onSecondarySessionUnavailable: (Throwable) -> Unit = {}
) : AuthRepository, SupabaseIdentityLinkProvider {

    private val identityMutationMutex = Mutex()
    private val identityMutationInProgress = MutableStateFlow(false)

    override val currentUser: Flow<UserAccount?> = combine(
        firebaseRepository.currentUser,
        identityMutationInProgress
    ) { user, mutationInProgress ->
        if (mutationInProgress) null else user
    }

    override fun getCurrentUserSync(): UserAccount? =
        if (identityMutationInProgress.value) null else firebaseRepository.getCurrentUserSync()

    override fun getSupabaseUserId(firebaseUserId: String): String? {
        if (identityMutationInProgress.value || firebaseUserId.isBlank()) return null
        return identityLinkStore.getSupabaseUserId(firebaseUserId)
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> = firebaseRepository.signUp(email, password, displayName, organization)

    override suspend fun signIn(email: String, password: String): Result<UserAccount> =
        firebaseRepository.signIn(email, password)

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        identityMutationMutex.withLock {
            identityMutationInProgress.value = true
            try {
                val firebaseResult = firebaseRepository.signInWithGoogle(idToken)
                if (firebaseResult.isFailure) return@withLock firebaseResult

                val firebaseUser = firebaseRepository.getCurrentUserSync()
                    ?: return@withLock Result.failure(
                        IllegalStateException("Firebase sign-in completed without an active user.")
                    )
                if (firebaseUser.id.isBlank()) {
                    return@withLock Result.failure(
                        IllegalStateException("Firebase sign-in returned an invalid user ID.")
                    )
                }

                val firebaseEmail = normalizeEmail(firebaseUser.email)
                if (firebaseEmail == null) {
                    clearSecondarySession()
                    return@withLock Result.success(Unit)
                }

                try {
                    // Remove any previous provider link/session before attempting the new account.
                    identityLinkStore.clear()
                    supabaseRepository.signOut()

                    val supabaseResult = supabaseRepository.signInWithGoogle(idToken)
                    if (supabaseResult.isFailure) {
                        val error = supabaseResult.exceptionOrNull()
                            ?: IllegalStateException("Supabase Google sign-in failed.")
                        clearSecondarySession()
                        reportSecondaryFailure(error)
                        return@withLock Result.success(Unit)
                    }

                    val supabaseUser = supabaseRepository.getCurrentUserSync()
                    val supabaseEmail = normalizeEmail(supabaseUser?.email)
                    if (supabaseUser == null ||
                        supabaseUser.id.isBlank() ||
                        supabaseEmail == null ||
                        supabaseEmail != firebaseEmail
                    ) {
                        val error = SecurityException(
                            "Firebase and Supabase did not authenticate the same Google account."
                        )
                        clearSecondarySession()
                        reportSecondaryFailure(error)
                        return@withLock Result.success(Unit)
                    }

                    identityLinkStore.write(
                        firebaseUserId = firebaseUser.id,
                        supabaseUserId = supabaseUser.id,
                        email = firebaseEmail
                    )
                    Result.success(Unit)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    clearSecondarySession()
                    throw cancelled
                } catch (error: Exception) {
                    clearSecondarySession()
                    reportSecondaryFailure(error)
                    // The primary Firebase authentication is still real and remains usable.
                    Result.success(Unit)
                }
            } finally {
                identityMutationInProgress.value = false
            }
        }

    override suspend fun signOut() = withContext(NonCancellable) {
        identityMutationMutex.withLock {
            identityMutationInProgress.value = true
            try {
                clearSecondarySession()
                firebaseRepository.signOut()
            } finally {
                identityMutationInProgress.value = false
            }
        }
    }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> =
        supabaseRepository.requestPasswordRecovery(email)

    override suspend fun completePasswordRecovery(
        credentials: PasswordRecoveryCredentials,
        newPassword: String
    ): Result<UserAccount> = supabaseRepository.completePasswordRecovery(credentials, newPassword)

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
        supabaseRepository.resetPassword(email, newPassword)

    private suspend fun clearSecondarySession() = withContext(NonCancellable) {
        try {
            identityLinkStore.clear()
        } catch (error: Exception) {
            reportSecondaryFailure(error)
        }
        try {
            supabaseRepository.signOut()
        } catch (error: Exception) {
            reportSecondaryFailure(error)
        }
    }

    private fun reportSecondaryFailure(error: Throwable) {
        runCatching { onSecondarySessionUnavailable(error) }
    }

    private fun normalizeEmail(email: String?): String? =
        email?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
}
