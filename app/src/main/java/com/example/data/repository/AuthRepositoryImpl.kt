package com.example.data.repository

import com.example.data.remote.supabase.SupabaseAuthApi
import com.example.data.remote.supabase.SupabaseAuthResponse
import com.example.data.remote.supabase.SupabasePasswordGrantRequest
import com.example.data.remote.supabase.SupabaseRefreshGrantRequest
import com.example.data.remote.supabase.SupabaseRecoveryRequest
import com.example.data.remote.supabase.SupabaseSessionStore
import com.example.data.remote.supabase.SupabaseSignUpRequest
import com.example.data.remote.supabase.SupabaseStoredSession
import com.example.data.remote.supabase.SupabaseUserMetadata
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Supabase is the only identity authority in this repository.
 * No Firebase or Room credential fallback is permitted here.
 */
class AuthRepositoryImpl(
    private val api: SupabaseAuthApi,
    private val publishableKey: String,
    private val sessionStore: SupabaseSessionStore
) : AuthRepository {

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
    ): Result<UserAccount> = runCatching {
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
            ?: throw IllegalStateException(
                "تم إرسال طلب إنشاء الحساب. تحقق من بريدك الإلكتروني لتأكيده، ثم سجّل الدخول."
            )
        sessionStore.write(session)
        session.user.toAccount()
    }

    override suspend fun signIn(email: String, password: String): Result<UserAccount> = runCatching {
        require(publishableKey.isNotBlank()) { "إعدادات Supabase غير مكتملة." }
        val response = api.signInWithPassword(
            publishableKey = publishableKey,
            request = SupabasePasswordGrantRequest(email = email.trim(), password = password)
        )
        val body = requireSuccessfulBody(response.isSuccessful, response.body(), response.message())
        val session = body.toStoredSession()
            ?: throw IllegalStateException("لم يُرجع Supabase جلسة مصادقة صالحة.")
        sessionStore.write(session)
        session.user.toAccount()
    }

    override suspend fun signOut() {
        val session = sessionStore.read()
        try {
            if (session != null && publishableKey.isNotBlank()) {
                api.signOut(
                    publishableKey = publishableKey,
                    bearerToken = "Bearer ${session.accessToken}"
                )
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

    private suspend fun refreshStoredSessionIfNeeded() {
        val stored = sessionStore.read() ?: run {
            _currentUser.value = null
            return
        }
        if (stored.expiresAtEpochSeconds > nowEpochSeconds() + 60L) {
            _currentUser.value = stored.user.toAccount()
            return
        }

        val refreshed = runCatching {
            val response = api.refreshSession(
                publishableKey = publishableKey,
                request = SupabaseRefreshGrantRequest(stored.refreshToken)
            )
            requireSuccessfulBody(response.isSuccessful, response.body(), response.message())
                .toStoredSession()
                ?: throw IllegalStateException("لم يُرجع Supabase جلسة مجددة صالحة.")
        }.getOrNull()

        if (refreshed == null) {
            sessionStore.clear()
            _currentUser.value = null
        } else {
            sessionStore.write(refreshed)
            _currentUser.value = refreshed.user.toAccount()
        }
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
            createdAt = nowEpochSeconds() * 1000L,
            lastLoginAt = nowEpochSeconds() * 1000L
        )
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L
}
