package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.supabase.SupabaseAuthApi
import com.example.data.remote.supabase.SupabaseAuthResponse
import com.example.data.remote.supabase.SupabaseAuthUser
import com.example.data.remote.supabase.SupabasePasswordGrantRequest
import com.example.data.remote.supabase.SupabaseRecoveryRequest
import com.example.data.remote.supabase.SupabaseRefreshGrantRequest
import com.example.data.remote.supabase.SupabaseSessionStore
import com.example.data.remote.supabase.SupabaseStoredSession
import com.example.data.remote.supabase.SupabaseSignUpRequest
import com.example.data.remote.supabase.SupabaseUpdateUserRequest
import com.example.data.remote.supabase.SupabaseVerifyOtpRequest
import com.example.auth.SUPABASE_PASSWORD_RECOVERY_REDIRECT_URI
import com.example.data.repository.AuthRepositoryImpl
import com.example.domain.repository.PasswordRecoveryCredentials
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryPasswordRecoveryTest {

    private lateinit var context: Context
    private lateinit var sessionStore: SupabaseSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionStore = SupabaseSessionStore(context)
        sessionStore.clear()
    }

    @After
    fun tearDown() {
        sessionStore.clear()
    }

    @Test
    fun recoveryRequestUsesTheRegisteredAppRedirectAndDoesNotCreateASession() = runTest {
        var requestedEmail: String? = null
        var redirectUri: String? = null
        val api = object : SupabaseAuthApi by unusedApi() {
            override suspend fun requestPasswordRecovery(
                publishableKey: String,
                redirectTo: String?,
                request: SupabaseRecoveryRequest
            ): Response<Unit> {
                requestedEmail = request.email
                redirectUri = redirectTo
                return Response.success(Unit)
            }
        }
        val repository = AuthRepositoryImpl(api, "publishable-test-key", sessionStore)

        val result = repository.requestPasswordRecovery(" owner@example.com ")

        assertTrue(result.isSuccess)
        assertEquals("owner@example.com", requestedEmail)
        assertEquals(SUPABASE_PASSWORD_RECOVERY_REDIRECT_URI, redirectUri)
        assertNull(sessionStore.read())
        assertNull(repository.getCurrentUserSync())
    }

    @Test
    fun verifiedRecoveryCredentialMustUpdatePasswordBeforePersistingTheSession() = runTest {
        var verifiedType: String? = null
        var verifiedTokenHash: String? = null
        var updateBearer: String? = null
        var updatedPassword: String? = null
        val api = object : SupabaseAuthApi by unusedApi() {
            override suspend fun verifyEmailOtp(
                publishableKey: String,
                request: SupabaseVerifyOtpRequest
            ): Response<SupabaseAuthResponse> {
                verifiedType = request.type
                verifiedTokenHash = request.tokenHash
                return Response.success(
                    SupabaseAuthResponse(
                        accessToken = "recovery-access",
                        refreshToken = "recovery-refresh",
                        expiresIn = 3_600L,
                        user = SupabaseAuthUser(
                            id = "recovery-user",
                            email = "owner@example.com"
                        )
                    )
                )
            }

            override suspend fun updatePassword(
                publishableKey: String,
                bearerToken: String,
                request: SupabaseUpdateUserRequest
            ): Response<SupabaseAuthUser> {
                updateBearer = bearerToken
                updatedPassword = request.password
                return Response.success(
                    SupabaseAuthUser(
                        id = "recovery-user",
                        email = "owner@example.com",
                        userMetadata = mapOf("display_name" to "Owner")
                    )
                )
            }
        }
        val repository = AuthRepositoryImpl(api, "publishable-test-key", sessionStore)

        val result = repository.completePasswordRecovery(
            credentials = PasswordRecoveryCredentials(tokenHash = "one-time-token-hash"),
            newPassword = "new-secure-password"
        )

        assertTrue(result.isSuccess)
        assertEquals("recovery", verifiedType)
        assertEquals("one-time-token-hash", verifiedTokenHash)
        assertEquals("Bearer recovery-access", updateBearer)
        assertEquals("new-secure-password", updatedPassword)
        assertEquals("recovery-user", repository.getCurrentUserSync()?.id)
        assertEquals("recovery-user", sessionStore.read()?.user?.id)
        assertEquals("recovery-refresh", sessionStore.read()?.refreshToken)
    }

    @Test
    fun expiredDirectRecoverySessionIsRejectedBeforePasswordUpdate() = runTest {
        var updateCalled = false
        val api = object : SupabaseAuthApi by unusedApi() {
            override suspend fun updatePassword(
                publishableKey: String,
                bearerToken: String,
                request: SupabaseUpdateUserRequest
            ): Response<SupabaseAuthUser> {
                updateCalled = true
                return Response.success(SupabaseAuthUser(id = "unexpected"))
            }
        }
        val repository = AuthRepositoryImpl(api, "publishable-test-key", sessionStore)

        val result = repository.completePasswordRecovery(
            credentials = PasswordRecoveryCredentials(
                accessToken = "expired-access",
                refreshToken = "expired-refresh",
                expiresAtEpochSeconds = 1L
            ),
            newPassword = "new-secure-password"
        )

        assertTrue(result.isFailure)
        assertTrue(!updateCalled)
        assertNull(sessionStore.read())
        assertNull(repository.getCurrentUserSync())
    }

    @Test
    fun authenticatedPasswordChangeRequiresTheMatchingUnexpiredSession() = runTest {
        sessionStore.write(
            SupabaseStoredSession(
                accessToken = "current-access",
                refreshToken = "current-refresh",
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3_600L,
                user = SupabaseAuthUser(id = "owner-user", email = "owner@example.com")
            )
        )
        var updateCalls = 0
        var bearerUsed: String? = null
        val api = object : SupabaseAuthApi by unusedApi() {
            override suspend fun updatePassword(
                publishableKey: String,
                bearerToken: String,
                request: SupabaseUpdateUserRequest
            ): Response<SupabaseAuthUser> {
                updateCalls += 1
                bearerUsed = bearerToken
                return Response.success(
                    SupabaseAuthUser(id = "owner-user", email = "owner@example.com")
                )
            }
        }
        val repository = AuthRepositoryImpl(api, "publishable-test-key", sessionStore)

        val mismatchedEmail = repository.resetPassword("other@example.com", "new-secure-password")
        assertTrue(mismatchedEmail.isFailure)
        assertEquals(0, updateCalls)

        val validChange = repository.resetPassword("owner@example.com", "new-secure-password")
        assertTrue(validChange.isSuccess)
        assertEquals(1, updateCalls)
        assertEquals("Bearer current-access", bearerUsed)
        assertEquals("owner-user", sessionStore.read()?.user?.id)
    }

    private fun unusedApi(): SupabaseAuthApi = object : SupabaseAuthApi {
        override suspend fun signUp(
            publishableKey: String,
            redirectTo: String?,
            request: SupabaseSignUpRequest
        ): Response<SupabaseAuthResponse> = throw IOException("Unexpected signup request")

        override suspend fun signInWithPassword(
            publishableKey: String,
            grantType: String,
            request: SupabasePasswordGrantRequest
        ): Response<SupabaseAuthResponse> = throw IOException("Unexpected sign-in request")

        override suspend fun refreshSession(
            publishableKey: String,
            grantType: String,
            request: SupabaseRefreshGrantRequest
        ): Response<SupabaseAuthResponse> = throw IOException("Unexpected refresh request")

        override suspend fun verifyEmailOtp(
            publishableKey: String,
            request: SupabaseVerifyOtpRequest
        ): Response<SupabaseAuthResponse> = throw IOException("Unexpected OTP request")

        override suspend fun requestPasswordRecovery(
            publishableKey: String,
            redirectTo: String?,
            request: SupabaseRecoveryRequest
        ): Response<Unit> = throw IOException("Unexpected recovery request")

        override suspend fun updatePassword(
            publishableKey: String,
            bearerToken: String,
            request: SupabaseUpdateUserRequest
        ): Response<SupabaseAuthUser> = throw IOException("Unexpected password update request")

        override suspend fun signOut(
            publishableKey: String,
            bearerToken: String
        ): Response<Unit> = throw IOException("Unexpected logout request")
    }
}
