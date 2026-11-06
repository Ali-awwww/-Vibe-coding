package com.example.data.repository

import com.example.data.remote.supabase.SupabaseAuthApi
import com.example.data.remote.supabase.SupabaseAuthResponse
import com.example.data.remote.supabase.SupabasePasswordGrantRequest
import com.example.data.remote.supabase.SupabaseRefreshGrantRequest
import com.example.data.remote.supabase.SupabaseSessionStore
import com.example.data.remote.supabase.SupabaseSignUpRequest
import com.example.data.remote.supabase.SupabaseStoredSession
import com.example.data.remote.supabase.SupabaseUserMetadata
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.EmailConfirmationRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val refreshMutex = Mutex()
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
        val response = api.signUp(
            publishableKey = publishableKey,
            request = SupabaseSignUpRequest(
                email = email.trim(),
                password = password,
                data = SupabaseUserMetadata(
                    displayName = displayName.trim(),
                    organization = organization.trim()
                )
            )
        )
        val body = requireSuccessfulBody(response.isSuccessful, response.body(), response.message())
        val session = body.toStoredSession()
            ?: throw EmailConfirmationRequiredException(
                "تم قبول طلب إنشاء الحساب. تحقق من بريدك الإلكتروني لتأكيده، ثم سجّل الدخول."
            )
        sessionStore.write(session)
        session.user.toAccount().also { _currentUser.value = it }
    }

    override suspend fun signIn(email: String, password: String): Result<UserAccount> = authResult {
        require(publishableKey.isNotBlank()) { "إعدادات Supabase غير مكتملة." }
        val response = api.signInWithPassword(
            publishableKey = publishableKey,
            request = SupabasePasswordGrantRequest(email = email.trim(), password = password)
        )
        val body = requireSuccessfulBody(response.isSuccessful, response.body(), response.message())
        val session = body.toStoredSession()
            ?: throw IllegalStateException("لم يُرجع Supabase جلسة مصادقة صالحة.")
        sessionStore.write(session)
        session.user.toAccount().also { _currentUser.value = it }
    }

    override suspend fun signOut() {
        val session = sessionStore.read()
        try {
            if (session != null && publishableKey.isNotBlank()) {
                try {
                    api.signOut(
                        publishableKey = publishableKey,
                        bearerToken = "Bearer ${session.accessToken}"
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Remote revocation is best-effort; local credentials are always cleared below.
                }
            }
        } finally {
            sessionStore.clear()
            _currentUser.value = null
        }
    }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "استعادة كلمة المرور لم تُفعّل بعد؛ يجب إكمال التحقق من رابط البريد وتدفق تعيين كلمة المرور الجديدة أولاً."
            )
        )

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "تسجيل الدخول عبر Google لم يُربط بعد بمزوّد OAuth في Supabase."
            )
        )

    private suspend fun refreshStoredSessionIfNeeded() = refreshMutex.withLock {
        val stored = sessionStore.read() ?: run {
            _currentUser.value = null
            return
        }
        if (stored.expiresAtEpochSeconds > nowEpochSeconds() + 60L) {
            _currentUser.value = stored.user.toAccount()
            return
        }

        try {
            val response = api.refreshSession(
                publishableKey = publishableKey,
                request = SupabaseRefreshGrantRequest(stored.refreshToken)
            )
            if (!response.isSuccessful) {
                // Revoke local identity only when the refresh credential is rejected.
                if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
                    sessionStore.clear()
                }
                _currentUser.value = null
                return
            }
            val refreshed = response.body()?.toStoredSession()
                ?: throw IllegalStateException("لم يُرجع Supabase جلسة مجددة صالحة.")
            sessionStore.write(refreshed)
            _currentUser.value = refreshed.user.toAccount()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            // Preserve the encrypted refresh token during transient connectivity failures.
            _currentUser.value = null
        } catch (_: Exception) {
            _currentUser.value = null
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
        successful: Boolean,
        body: SupabaseAuthResponse?,
        responseMessage: String
    ): SupabaseAuthResponse {
        if (!successful) {
            throw IllegalStateException(
                responseMessage.takeIf(String::isNotBlank)
                    ?: "رفض خادم Supabase طلب المصادقة."
            )
        }
        return body ?: throw IllegalStateException("استجابة المصادقة من Supabase فارغة.")
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
