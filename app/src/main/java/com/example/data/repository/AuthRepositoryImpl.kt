package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.local.CrashDao
import com.example.domain.model.AuditLog
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.util.SecurityUtils
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuthRepositoryImpl(
    private val db: FirebaseFirestore? = null,
    private val auth: FirebaseAuth? = null,
    private val crashDao: CrashDao? = null
) : AuthRepository {

    private val _localCurrentUser = MutableStateFlow<UserAccount?>(null)

    override val currentUser: Flow<UserAccount?> = callbackFlow {
        val currentAuth = auth
        if (currentAuth == null) {
            val job = CoroutineScope(Dispatchers.IO).launch {
                _localCurrentUser.collect { user ->
                    trySend(user)
                }
            }
            awaitClose { job.cancel() }
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser != null) {
                val currentDb = db
                if (currentDb != null) {
                    val docRef = currentDb.collection("users").document(firebaseUser.uid)
                    val registration = docRef.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("Auth", "Error fetching user profile", error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val userAccount = snapshot.toObject(UserAccount::class.java)
                            trySend(userAccount)
                        } else {
                            trySend(UserAccount(
                                id = firebaseUser.uid,
                                email = firebaseUser.email ?: "",
                                displayName = firebaseUser.displayName ?: "",
                                createdAt = System.currentTimeMillis()
                            ))
                        }
                    }
                } else {
                    trySend(UserAccount(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        displayName = firebaseUser.displayName ?: "",
                        createdAt = System.currentTimeMillis()
                    ))
                }
            } else {
                // If not signed in to Firebase, check if local user is active
                trySend(_localCurrentUser.value)
            }
        }
        currentAuth.addAuthStateListener(listener)
        awaitClose {
            currentAuth.removeAuthStateListener(listener)
        }
    }

    override fun getCurrentUserSync(): UserAccount? {
        val firebaseUser = auth?.currentUser
        if (firebaseUser != null) {
            return UserAccount(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                createdAt = System.currentTimeMillis()
            )
        }
        return _localCurrentUser.value
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> {
        val dao = crashDao
        if (dao != null) {
            val existing = dao.getUserByEmail(email)
            if (existing != null) {
                return Result.failure(IllegalArgumentException("البريد الإلكتروني مسجل مسبقاً"))
            }
            val salt = SecurityUtils.generateSalt()
            val hash = SecurityUtils.hashPassword(password, salt)
            val newUser = UserAccount(
                id = UUID.randomUUID().toString(),
                email = email,
                passwordHash = hash,
                salt = salt,
                displayName = displayName.ifBlank { email.substringBefore("@") },
                organization = organization.ifBlank { "Independent Team" },
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )
            dao.insertUser(newUser)
            _localCurrentUser.value = newUser
            return Result.success(newUser)
        }
        return Result.failure(IllegalStateException("تخزين الحسابات المحلية غير مهيأ."))
    }

    override suspend fun signIn(email: String, password: String): Result<UserAccount> {
        val dao = crashDao
        if (dao != null) {
            val user = dao.getUserByEmail(email)
            if (user != null) {
                if (user.passwordHash.isBlank() || SecurityUtils.verifyPassword(password, user.salt, user.passwordHash)) {
                    dao.updateLastLogin(user.id, System.currentTimeMillis())
                    val updated = user.copy(lastLoginAt = System.currentTimeMillis())
                    _localCurrentUser.value = updated
                    return Result.success(updated)
                } else {
                    return Result.failure(IllegalArgumentException("كلمة المرور غير صحيحة"))
                }
            }
            return Result.failure(IllegalArgumentException("المستخدم غير موجود"))
        }

        return Result.failure(IllegalStateException("المصادقة المحلية غير مهيأة؛ تعذر التحقق من بيانات الاعتماد."))
    }

    override suspend fun signOut() {
        auth?.signOut()
        _localCurrentUser.value = null
    }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> {
        val dao = crashDao
        if (dao != null) {
            val user = dao.getUserByEmail(email)
            if (user != null) {
                val salt = SecurityUtils.generateSalt()
                val hash = SecurityUtils.hashPassword(newPassword, salt)
                dao.updateUserPassword(email, hash, salt)
                return Result.success(Unit)
            }
            return Result.failure(IllegalArgumentException("البريد الإلكتروني غير مسجل"))
        }
        return Result.failure(IllegalStateException("خدمة استعادة كلمة المرور غير مهيأة."))
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        val currentAuth = auth ?: throw IllegalStateException("Auth not initialized")
        val currentDb = db ?: throw IllegalStateException("Firestore not initialized")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = currentAuth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user ?: throw IllegalStateException("Auth failed")

        val userDoc = currentDb.collection("users").document(firebaseUser.uid)
        val profile = mapOf(
            "id" to firebaseUser.uid,
            "email" to firebaseUser.email,
            "displayName" to firebaseUser.displayName,
            "lastLoginAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        userDoc.set(profile, SetOptions.merge()).await()

        val auditRef = currentDb.collection("audit_logs").document()
        auditRef.set(mapOf(
            "id" to auditRef.id,
            "userId" to firebaseUser.uid,
            "userEmail" to firebaseUser.email,
            "action" to "USER_LOGIN_GOOGLE",
            "details" to "Successful Google Sign-In",
            "timestamp" to FieldValue.serverTimestamp()
        )).await()

        Unit
    }
}
