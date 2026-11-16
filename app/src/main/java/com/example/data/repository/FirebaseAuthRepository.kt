package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase Authentication (Google Sign-In via Jetpack Credential Manager) repository.
 * Acts as the primary identity provider across Firestore and Workspace repositories.
 */
class FirebaseAuthRepository(
    private val context: Context,
    private val firestoreProvider: () -> FirebaseFirestore?,
    private val firebaseAuthProvider: () -> FirebaseAuth?
) : AuthRepository {

    private val auth: FirebaseAuth?
        get() = firebaseAuthProvider()

    override val currentUser: Flow<UserAccount?> = callbackFlow {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { state ->
            trySend(state.currentUser?.toUserAccount())
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser?.toUserAccount())
        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    override fun getCurrentUserSync(): UserAccount? =
        auth?.currentUser?.toUserAccount()

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        try {
            val firebaseAuth = auth ?: throw IllegalStateException(
                "Firebase Authentication is not configured."
            )
            require(idToken.isNotBlank()) { "Google ID token must not be blank." }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Firebase sign-in returned no user.")

            syncUserProfile(firebaseUser)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Google sign-in with Firebase failed", error)
            Result.failure(error)
        }

    override suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context.applicationContext)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Unable to clear Credential Manager state on sign-out", e)
            }
            auth?.signOut()
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> = Result.failure(
        UnsupportedOperationException("Use Google Sign-In via Firebase Authentication.")
    )

    override suspend fun signIn(email: String, password: String): Result<UserAccount> = Result.failure(
        UnsupportedOperationException("Use Google Sign-In via Firebase Authentication.")
    )

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Password management is handled by Google Account.")
    )

    private suspend fun syncUserProfile(user: FirebaseUser) {
        val fs = firestoreProvider() ?: return
        val docRef = fs.collection("users").document(user.uid)
        try {
            val existing = docRef.get().await()
            val email = user.email.orEmpty().ifBlank { "${user.uid}@users.local" }
            val displayName = user.displayName.orEmpty().trim()
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
    }
}
