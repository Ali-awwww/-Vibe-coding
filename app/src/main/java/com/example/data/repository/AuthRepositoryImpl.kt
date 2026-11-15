package com.example.data.repository

import com.example.data.remote.supabase.SupabaseAuthApi
import com.example.data.remote.supabase.SupabaseRecoveryRequest
import com.example.data.remote.supabase.SupabaseUpdateUserRequest
import com.example.data.remote.supabase.SupabaseVerifyOtpRequest
import com.example.auth.SUPABASE_PASSWORD_RECOVERY_REDIRECT_URI
import com.example.auth.SUPABASE_EMAIL_CONFIRMATION_REDIRECT_URI
import com.example.data.remote.supabase.SupabaseAuthResponse
import com.example.data.remote.supabase.SupabasePasswordGrantRequest
import com.example.data.remote.supabase.SupabaseRefreshGrantRequest
import com.example.data.remote.supabase.SupabaseSessionStore
import com.example.data.remote.supabase.SupabaseSignUpRequest
import com.example.data.remote.supabase.SupabaseStoredSession
import com.example.data.remote.supabase.SupabaseUserMetadata
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.domain.repository.EmailConfirmationRequiredException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Supabase is the only identity authority in this repository.
 * No Firebase or Room credential fallback is permitted here.
 */
class AuthRepositoryImpl(
    private val api: SupabaseAuthApi,
    private val publishableKey: String,
    private val sessionStore: SupabaseSessionStore
) : AuthRepository {

    // Serialize network auth operations; sessionStateLock protects generation + local identity mutations.
    private val sessionMutex = Mutex()
    private val sessionStateLock = Any()
    private var sessionGeneration = 0L
    private val _currentUser = MutableStateFlow(
        sessionStore.read()?.takeIf { it.expiresAtEpochSeconds > nowEpochSeconds() }?.user?.toAccount()
    )

    override val currentUser: Flow<UserAccount?> = flow {
        refreshStoredSessionIfNeeded()
        emitAll(_currentUser.asStateFlow())
    }

    override fun getCurrentUserSync(): UserAccount? = _currentUser.value

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        organization: String
    ): Result<UserAccount> = authResult {
        require(publishableKey.isNotBlank()) { "إعدادات Supabase غير مكتملة." }
        sessionMutex.withLock {
            val requestGeneration = synchronized(sessionStateLock) { sessionGeneration }
            val response = api.signUp(
                publishableKey = publishableKey,
                redirectTo = SUPABASE_EMAIL_CONFIRMATION_REDIRECT_URI,
                request = SupabaseSignUpRequest(
                    email = email.trim(),
                    password = password,
                    data = SupabaseUserMetadata(
                        displayName = displayName.trim(),
                        organization = organization.trim()
                    )
                )
            )
            val body = requireSuccessfulBody(response)
            val session = body.toStoredSession()
                ?: throw EmailConfirmationRequiredException(
                    "تم قبول طلب إنشاء الحساب. تحقق من بريدك الإلكتروني لتأكيده، ثم سجّل الدخول."
                )
            synchronized(sessionStateLock) {
                check(sessionGeneration == requestGeneration) {
                    "تم إلغاء طلب إنشاء الحساب بسبب تسجيل الخروج."
                }
                sessionStore.write(session)
                session.user.toAccount().also { _currentUser.value = it }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<UserAccount> = authResult {
        require(publishableKey.isNotBlank()) { "إعدادات Supabase غير مكتملة." }
        sessionMutex.withLock {
            val requestGeneration = synchronized(sessionStateLock) { sessionGeneration }
            val response = api.signInWithPassword(
                publishableKey = publishableKey,
                request = SupabasePasswordGrantRequest(email = email.trim(), password = password)
            )
            val body = requireSuccessfulBody(response)
            val session = body.toStoredSession()
                ?: throw IllegalStateException("لم يُرجع Supabase جلسة مصادقة صالحة.")
            synchronized(sessionStateLock) {
                check(sessionGeneration == requestGeneration) {
                    "تم إلغاء طلب تسجيل الدخول بسبب تسجيل الخروج."
                }
                sessionStore.write(session)
                session.user.toAccount().also { _currentUser.value = it }
            }
        }
    }

    override suspend fun signOut() {
        // Invalidate any auth request already in flight, then clear local state before
        // waiting for the network-operation mutex. This keeps logout responsive during refresh.
        val session = synchronized(sessionStateLock) {
            val stored = sessionStore.read()
            sessionGeneration += 1L
            try {
                sessionStore.clear()
            } finally {
                _currentUser.value = null
            }
            stored
        }

        sessionMutex.withLock {
            // A sign-in queued before this lock acquisition may have written after the first
            // clear. Clear again so the logout request wins that race.
            synchronized(sessionStateLock) {
                try {
                    sessionStore.clear()
                } finally {
                    _currentUser.value = null
                }
            }

            if (session != null && publishableKey.isNotBlank()) {
                try {
                    api.signOut(
                        publishableKey = publishableKey,
                        bearerToken = "Bearer ${session.accessToken}"
                    )
                } catch (cancelled: CancellationException) {
                    // Local logout is already complete; preserve structured cancellation.
                    throw cancelled
                } catch (_: Exception) {
                    // Remote revocation is best-effort; local credentials have already been cleared.
                }
            }
        }
    }

    override suspend fun requestPasswordRecovery(email: String): Result<Unit> = authResult {
        require(publishableKey.isNotBlank()) { "إعدادات Supabase غير مكتملة." }
        require(email.isNotBlank()) { "أدخل البريد الإلكتروني المرتبط بحسابك." }
        val response = api.requestPasswordRecovery(
            publishableKey = publishableKey,
            redirectTo = SUPABASE_PASSWORD_RECOVERY_REDIRECT_URI,
            request = SupabaseRecoveryRequest(email = email.trim())
        )
        if (!response.isSuccessful) {
            throw IllegalStateException(
                response.message().takeIf(String::isNotBlank)
                    ?: "تعذر إرسال طلب استعادة كلمة المرور من Supabase."
            )
        }
    }

    override suspend fun completePasswordRecovery(
        credentials: PasswordRecoveryCredentials,
        newPassword: String
    ): Result<UserAccount> = authResult {
        require(publishableKey.isNotBlank()) { "إعدادات Supabase غير مكتملة." }
        require(newPassword.length >= 8) { "كلمة المرور يجب أن تتكون من 8 أحرف على الأقل." }

        sessionMutex.withLock {
            val requestGeneration = synchronized(sessionStateLock) { sessionGeneration }
            val operationContext = currentCoroutineContext()
            val verifiedSession = credentials.tokenHash?.takeIf(String::isNotBlank)?.let { tokenHash ->
                val verification = api.verifyEmailOtp(
                    publishableKey = publishableKey,
                    request = SupabaseVerifyOtpRequest(type = "recovery", tokenHash = tokenHash)
                )
                operationContext.ensureActive()
                requireSuccessfulBody(
                    verification.isSuccessful,
                    verification.body(),
                    verification.message()
                ).toStoredSession()
                    ?: throw IllegalStateException("لم يُرجع رابط الاستعادة جلسة تحقق صالحة.")
            }

            operationContext.ensureActive()
            val accessToken = verifiedSession?.accessToken
                ?: credentials.accessToken?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("رابط الاستعادة لا يحتوي بيانات تحقق صالحة.")
            val refreshToken = verifiedSession?.refreshToken
                ?: credentials.refreshToken?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("رابط الاستعادة لا يحتوي رمز تحديث صالحًا.")
            val expiresAt = verifiedSession?.expiresAtEpochSeconds
                ?: credentials.expiresAtEpochSeconds
                ?: throw IllegalArgumentException("انتهت صلاحية رابط الاستعادة.")
            require(expiresAt > nowEpochSeconds()) { "انتهت صلاحية رابط الاستعادة. اطلب رابطًا جديدًا." }

            val update = api.updatePassword(
                publishableKey = publishableKey,
                bearerToken = "Bearer $accessToken",
                request = SupabaseUpdateUserRequest(password = newPassword)
            )
            operationContext.ensureActive()
            if (!update.isSuccessful) {
                throw IllegalStateException(
                    update.message().takeIf(String::isNotBlank)
                        ?: "رفض Supabase تحديث كلمة المرور. قد يكون رابط الاستعادة منتهيًا."
                )
            }
            val updatedUser = update.body()
                ?: throw IllegalStateException("لم يُرجع Supabase بيانات الحساب بعد تحديث كلمة المرور.")

            val updatedSession = SupabaseStoredSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochSeconds = expiresAt,
                user = updatedUser
            )
            synchronized(sessionStateLock) {
                operationContext.ensureActive()
                check(sessionGeneration == requestGeneration) {
                    "تم إلغاء استعادة كلمة المرور بسبب تغيير حالة الجلسة."
                }
                sessionStore.write(updatedSession)
                updatedUser.toAccount().also { _currentUser.value = it }
            }
        }
    }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> = authResult {
        require(publishableKey.isNotBlank()) { "إعدادات Supabase غير مكتملة." }
        require(newPassword.length >= 8) { "كلمة المرور يجب أن تتكون من 8 أحرف على الأقل." }
        refreshStoredSessionIfNeeded()

        sessionMutex.withLock {
            val snapshot = synchronized(sessionStateLock) {
                sessionGeneration to sessionStore.read()
            }
            val generation = snapshot.first
            val session = snapshot.second
                ?: throw IllegalStateException("انتهت الجلسة. سجّل الدخول مجددًا قبل تغيير كلمة المرور.")
            require(session.expiresAtEpochSeconds > nowEpochSeconds() + 60L) {
                "انتهت الجلسة. سجّل الدخول مجددًا قبل تغيير كلمة المرور."
            }
            require(session.user.email.equals(email.trim(), ignoreCase = true)) {
                "البريد الإلكتروني لا يطابق الحساب الحالي."
            }

            val response = api.updatePassword(
                publishableKey = publishableKey,
                bearerToken = "Bearer ${session.accessToken}",
                request = SupabaseUpdateUserRequest(password = newPassword)
            )
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    response.message().takeIf(String::isNotBlank)
                        ?: "تعذر تحديث كلمة المرور."
                )
            }
            val updatedUser = response.body()
                ?: throw IllegalStateException("لم يُرجع Supabase بيانات الحساب بعد تحديث كلمة المرور.")
            synchronized(sessionStateLock) {
                check(sessionGeneration == generation) {
                    "تم إلغاء تغيير كلمة المرور بسبب تسجيل الخروج."
                }
                sessionStore.write(session.copy(user = updatedUser))
                _currentUser.value = updatedUser.toAccount()
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "تسجيل الدخول عبر Google لم يُربط بعد بمزوّد OAuth في Supabase."
            )
        )

    private suspend fun refreshStoredSessionIfNeeded() = sessionMutex.withLock {
        val snapshot = synchronized(sessionStateLock) {
            sessionGeneration to sessionStore.read()
        }
        val requestGeneration = snapshot.first
        val stored = snapshot.second

        if (stored == null) {
            synchronized(sessionStateLock) {
                _currentUser.value = null
            }
            return@withLock
        }

        if (stored.expiresAtEpochSeconds > nowEpochSeconds() + 60L) {
            synchronized(sessionStateLock) {
                _currentUser.value = if (sessionGeneration == requestGeneration) {
                    stored.user.toAccount()
                } else {
                    null
                }
            }
            return@withLock
        }

        try {
            val response = api.refreshSession(
                publishableKey = publishableKey,
                request = SupabaseRefreshGrantRequest(stored.refreshToken)
            )

            val generationStillCurrent = synchronized(sessionStateLock) {
                sessionGeneration == requestGeneration
            }
            if (!generationStillCurrent) {
                synchronized(sessionStateLock) { _currentUser.value = null }
                return@withLock
            }

            if (!response.isSuccessful) {
                synchronized(sessionStateLock) {
                    if (sessionGeneration == requestGeneration) {
                        // Revoke local identity only when the refresh credential is rejected.
                        if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
                            sessionStore.clear()
                        }
                        _currentUser.value = null
                    } else {
                        _currentUser.value = null
                    }
                }
                return@withLock
            }

            val refreshed = response.body()?.toStoredSession()
                ?: throw IllegalStateException("لم يُرجع Supabase جلسة مجددة صالحة.")
            val persisted = synchronized(sessionStateLock) {
                if (sessionGeneration != requestGeneration) {
                    _currentUser.value = null
                    false
                } else {
                    sessionStore.write(refreshed)
                    _currentUser.value = refreshed.user.toAccount()
                    true
                }
            }
            if (!persisted) return@withLock
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            synchronized(sessionStateLock) {
                if (sessionGeneration == requestGeneration) {
                    // Preserve the encrypted refresh token during transient connectivity failures.
                    _currentUser.value = null
                }
            }
        } catch (_: Exception) {
            synchronized(sessionStateLock) {
                if (sessionGeneration == requestGeneration) {
                    _currentUser.value = null
                }
            }
        }
    }

    private suspend fun <T> authResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            // Cancellation is control flow, not an authentication failure.
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun SupabaseAuthResponse.toStoredSession(): SupabaseStoredSession? {
        val access = accessToken?.takeIf(String::isNotBlank) ?: return null
        val refresh = refreshToken?.takeIf(String::isNotBlank) ?: return null
        val authUser = user ?: return null
        val expires = expiresIn ?: return null
        return SupabaseStoredSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = nowEpochSeconds() + expires,
            user = authUser
        )
    }

    private fun requireSuccessfulBody(
        response: Response<SupabaseAuthResponse>
    ): SupabaseAuthResponse {
        if (!response.isSuccessful) {
            val serverError = response.errorBody()?.string()?.let(::parseSupabaseError)
            throw IllegalStateException(
                serverError
                    ?: response.message().takeIf(String::isNotBlank)
                    ?: "رفض خادم Supabase طلب المصادقة."
            )
        }
        return response.body() ?: throw IllegalStateException("استجابة المصادقة من Supabase فارغة.")
    }

    private fun parseSupabaseError(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        val parsed = runCatching {
            Moshi.Builder().build().adapter(SupabaseAuthError::class.java).fromJson(rawBody)
        }.getOrNull()
        return listOf(parsed?.errorDescription, parsed?.message, parsed?.msg, parsed?.error)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: rawBody.trim().takeIf { it.isNotBlank() }
    }

    private fun com.example.data.remote.supabase.SupabaseAuthUser.toAccount(): UserAccount {
        val metadata = userMetadata.orEmpty()
        return UserAccount(
            id = id,
            email = email.orEmpty(),
            displayName = metadata["display_name"].orEmpty(),
            organization = metadata["organization"].orEmpty(),
            createdAt = parseCreatedAt(createdAt),
            lastLoginAt = nowEpochSeconds() * 1000L
        )
    }

    private fun parseCreatedAt(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        val normalized = value.substringBefore('.').substringBefore('Z') + "Z"
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(normalized)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L
}
