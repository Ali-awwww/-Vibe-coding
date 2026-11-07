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
import com.example.data.remote.supabase.SupabaseSignUpRequest
import com.example.data.remote.supabase.SupabaseStoredSession
import com.example.data.remote.supabase.SupabaseUpdateUserRequest
import com.example.data.remote.supabase.SupabaseVerifyOtpRequest
import com.example.data.repository.AuthRepositoryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositorySignOutRaceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signOutClearsLocalIdentityBeforeAnInFlightRefreshCanPersist() = runTest(testDispatcher) {
        val sessionStore = SupabaseSessionStore(context)
        seedNearExpirySession(sessionStore)

        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        lateinit var repository: AuthRepositoryImpl
        var remoteRevocationSawClearedState = false

        val api = object : SupabaseAuthApi by unusedSupabaseAuthApi() {
            override suspend fun refreshSession(
                publishableKey: String,
                grantType: String,
                request: SupabaseRefreshGrantRequest
            ): Response<SupabaseAuthResponse> {
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                return Response.success(
                    SupabaseAuthResponse(
                        accessToken = "refreshed-access-token",
                        refreshToken = "refreshed-refresh-token",
                        expiresIn = 3600,
                        tokenType = "bearer",
                        user = SupabaseAuthUser(
                            id = "logout-user",
                            email = "logout@example.com"
                        )
                    )
                )
            }

            override suspend fun signOut(
                publishableKey: String,
                bearerToken: String
            ): Response<Unit> {
                remoteRevocationSawClearedState =
                    sessionStore.read() == null && repository.getCurrentUserSync() == null
                return Response.success(Unit)
            }
        }

        repository = AuthRepositoryImpl(
            api = api,
            sessionStore = sessionStore,
            publishableKey = "publishable-test-key"
        )

        assertEquals("logout-user", repository.getCurrentUserSync()?.id)

        val currentUser = async { repository.currentUser.first() }
        runCurrent()
        refreshStarted.await()

        val logout = launch { repository.signOut() }
        runCurrent()

        assertNull(sessionStore.read())
        assertNull(repository.getCurrentUserSync())
        assertFalse("Logout should still be waiting for remote serialization", logout.isCompleted)

        releaseRefresh.complete(Unit)
        advanceUntilIdle()
        logout.join()

        assertNull(sessionStore.read())
        assertNull(repository.getCurrentUserSync())
        assertNull(currentUser.await())
        assertTrue(remoteRevocationSawClearedState)
    }

    private fun seedNearExpirySession(sessionStore: SupabaseSessionStore) {
        sessionStore.clear()
        sessionStore.write(
            SupabaseStoredSession(
                accessToken = "near-expiry-access-token",
                refreshToken = "near-expiry-refresh-token",
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 30L,
                user = SupabaseAuthUser(
                    id = "logout-user",
                    email = "logout@example.com"
                )
            )
        )
    }

    private fun unusedSupabaseAuthApi() = object : SupabaseAuthApi {
        override suspend fun signUp(
            publishableKey: String,
            redirectTo: String?,
            request: SupabaseSignUpRequest
        ): Response<SupabaseAuthResponse> = throw IOException("Unexpected signup request")

        override suspend fun signInWithPassword(
            publishableKey: String,
            grantType: String,
            request: SupabasePasswordGrantRequest
        ): Response<SupabaseAuthResponse> = throw IOException("Unexpected signin request")

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
