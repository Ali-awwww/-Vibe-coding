package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import com.example.domain.repository.FirebaseAuthNotConfiguredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.example.util.recordCrashlyticsNonFatal
import com.example.util.setCrashlyticsUserId
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firebase Authentication repository supporting Google Sign-In, Email/Password sign-in & sign-up,
 * email verification, and password recovery across Firestore and Workspace repositories.
 */
class FirebaseAuthRepository(
    private val context: Context,
    private val firestoreProvider: () -> FirebaseFirestore?,
    private val firebaseAuthProvider: () -> FirebaseAuth?,
    private val credentialStateCleaner: suspend () -> Unit = {
        CredentialManager.create(context.applicationContext)
            .clearCredentialState(ClearCredentialStateRequest())
    },
    private val firebaseSignOutAction: (FirebaseAuth?) -> Unit = { it?.signOut() }
) : AuthRepository {

    private val auth: FirebaseAuth?
        get() = firebaseAuthProvider()

    override val currentUser: Flow<UserAccount?> = callbackFlow {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            setCrashlyticsUserId("")
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { state ->
            val account = state.currentUser?.takeIf { it.isSessionAuthorized() }?.toUserAccount()
            setCrashlyticsUserId(account?.id.orEmpty())
            trySend(account)
        }
        firebaseAuth.addAuthStateListener(listener)
        val initialAccount = firebaseAuth.currentUser?.takeIf { it.isSessionAuthorized() }?.toUserAccount()
        setCrashlyticsUserId(initialAccount?.id.orEmpty())
        trySend(initialAccount)
        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    override fun getCurrentUserSync(): UserAccount? =
        auth?.currentUser?.takeIf { it.isSessionAuthorized() }?.toUserAccount()

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        try {
            val firebaseAuth = auth ?: throw FirebaseAuthNotConfiguredException()
            require(idToken.isNotBlank()) { "Google ID token must not be blank." }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Firebase sign-in returned no user.")

            setCrashlyticsUserId(firebaseUser.uid)
            syncUserProfile(firebaseUser)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Google sign-in with Firebase failed", error)
            recordCrashlyticsNonFatal(error, "Firebase Google sign-in failed")
            Result.failure(error)
        }

    override suspend fun signIn(email: String, password: String): Result<UserAccount> {
        return try {
            val firebaseAuth = auth ?: throw FirebaseAuthNotConfiguredException()
            val trimmedEmail = email.trim()
            require(trimmedEmail.isNotBlank()) { "Email address must not be blank." }
            require(password.isNotBlank()) { "Password must not be blank." }

            val authResult = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Firebase sign-in returned no user.")

            runCatching { firebaseUser.reload().await() }
            if (!firebaseUser.isSessionAuthorized()) {
                runCatching { firebaseUser.sendEmailVerification().await() }
                firebaseAuth.signOut()
                setCrashlyticsUserId("")
                Result.failure(
                    EmailConfirmationRequiredException(
                        "Email verification is required before signing in."
                    )
                )
            } else {
                setCrashlyticsUserId(firebaseUser.uid)
                syncUserProfile(firebaseUser)
                Result.success(firebaseUser.toUserAccount())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Email/password sign-in failed", error)
            recordCrashlyticsNonFatal(error, "Firebase email sign-in failed")
            Result.failure(error)
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> =
        try {
            val firebaseAuth = auth ?: throw FirebaseAuthNotConfiguredException()
            val trimmedEmail = email.trim()
            val trimmedName = displayName.trim()
            val trimmedOrg = organization.trim()
            require(trimmedEmail.isNotBlank()) { "Email address must not be blank." }
            require(password.length >= 8) { "Password must be at least 8 characters." }

            val authResult = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Firebase account creation returned no user.")

            if (trimmedName.isNotBlank()) {
                runCatching {
                    val profileUpdate = UserProfileChangeRequest.Builder()
                        .setDisplayName(trimmedName)
                        .build()
                    firebaseUser.updateProfile(profileUpdate).await()
                }
            }

            syncUserProfile(firebaseUser, explicitDisplayName = trimmedName, explicitOrg = trimmedOrg)
            firebaseUser.sendEmailVerification().await()
            firebaseAuth.signOut()
            setCrashlyticsUserId("")

            Result.failure(
                EmailConfirmationRequiredException(
                    "Verification email sent. Confirm your email before signing in."
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Firebase sign-up failed", error)
            recordCrashlyticsNonFatal(error, "Firebase email sign-up failed")
            Result.failure(error)
        }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> =
        try {
            val firebaseAuth = auth ?: throw FirebaseAuthNotConfiguredException()
            val trimmedEmail = email.trim()
            require(trimmedEmail.isNotBlank()) { "Email address must not be blank." }
            firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Firebase password reset email failed", error)
            recordCrashlyticsNonFatal(error, "Firebase password recovery failed")
            Result.failure(error)
        }

    override suspend fun completePasswordRecovery(
        credentials: PasswordRecoveryCredentials,
        newPassword: String
    ): Result<UserAccount> =
        try {
            val firebaseAuth = auth ?: throw FirebaseAuthNotConfiguredException()
            val oobCode = credentials.oobCode?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Password recovery verification code is missing.")
            require(newPassword.length >= 8) { "Password must be at least 8 characters." }

            firebaseAuth.confirmPasswordReset(oobCode, newPassword).await()
            val current = firebaseAuth.currentUser?.takeIf { it.isSessionAuthorized() }?.toUserAccount()
            if (current != null) {
                Result.success(current)
            } else {
                Result.failure(
                    IllegalStateException("Password updated. Sign in with your new password.")
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Firebase password reset confirmation failed", error)
            Result.failure(error)
        }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
        try {
            val firebaseAuth = auth ?: throw FirebaseAuthNotConfiguredException()
            val trimmedEmail = email.trim()
            val current = firebaseAuth.currentUser
            if (current != null && current.email.equals(trimmedEmail, ignoreCase = true) && newPassword.length >= 8) {
                current.updatePassword(newPassword).await()
            } else {
                require(trimmedEmail.isNotBlank()) { "Email address must not be blank." }
                firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Firebase resetPassword failed", error)
            Result.failure(error)
        }

    override suspend fun signOut() {
        withContext(NonCancellable) {
            setCrashlyticsUserId("")
            try {
                val cleared = withTimeoutOrNull(CREDENTIAL_STATE_CLEAR_TIMEOUT_MS) {
                    credentialStateCleaner()
                    true
                } == true
                if (!cleared) {
                    Log.w(TAG, "Credential Manager cleanup timed out; continuing sign-out")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Unable to clear Credential Manager state on sign-out", error)
            } finally {
                // Authentication sign-out must not be skipped when Credential Manager is slow
                // or unavailable.
                firebaseSignOutAction(auth)
            }
        }
    }

    private fun FirebaseUser.isSessionAuthorized(): Boolean {
        val usesPasswordProvider = providerData.any { it.providerId == "password" }
        return !usesPasswordProvider || isEmailVerified
    }

    private suspend fun syncUserProfile(
        user: FirebaseUser,
        explicitDisplayName: String = "",
        explicitOrg: String = ""
    ) {
        val fs = firestoreProvider() ?: return
        val docRef = fs.collection("users").document(user.uid)
        try {
            val existing = docRef.get().await()
            val email = user.email.orEmpty().ifBlank { "${user.uid}@users.local" }
            val displayName = explicitDisplayName.ifBlank { user.displayName.orEmpty().trim() }
            val photoUrl = user.photoUrl?.toString().orEmpty()

            if (!existing.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to user.uid,
                    "email" to email,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (displayName.isNotBlank()) {
                    createPayload["displayName"] = displayName.take(100)
                }
                if (explicitOrg.isNotBlank()) {
                    createPayload["organization"] = explicitOrg.take(120)
                }
                if (photoUrl.isNotBlank()) {
                    createPayload["photoUrl"] = photoUrl.take(500)
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "email" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (displayName.isNotBlank()) {
                    updatePayload["displayName"] = displayName.take(100)
                }
                if (explicitOrg.isNotBlank()) {
                    updatePayload["organization"] = explicitOrg.take(120)
                }
                if (photoUrl.isNotBlank()) {
                    updatePayload["photoUrl"] = photoUrl.take(500)
                }
                docRef.update(updatePayload).await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            handleFirestoreError(error, OperationType.WRITE, docRef.path)
        }
    }

    private fun FirebaseUser.toUserAccount(): UserAccount {
        val metadata = metadata
        val nameFallback = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "Developer"
        return UserAccount(
            id = uid,
            email = email.orEmpty(),
            displayName = nameFallback,
            organization = "",
            createdAt = metadata?.creationTimestamp ?: System.currentTimeMillis(),
            lastLoginAt = metadata?.lastSignInTimestamp ?: System.currentTimeMillis()
        )
    }

    private companion object {
        const val TAG = "FirebaseAuthRepo"
        const val CREDENTIAL_STATE_CLEAR_TIMEOUT_MS = 2_000L
    }
}
