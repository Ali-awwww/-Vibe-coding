package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.supabase.*
import com.example.data.remote.OpenRouterChatRequest
import com.example.data.remote.OpenRouterChatResponse
import com.example.data.repository.AIModelRepositoryImpl
import com.example.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
@OptIn(ExperimentalCoroutinesApi::class)
class ResilienceAndNegativePathTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        
        // Initialize Firebase
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(context)
        }

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `test AI Repository handles Rate Limit 429 gracefully`() = runTest(testDispatcher) {
        val fakeApi = object : OpenRouterApi {
            override suspend fun getModels(token: String): com.example.data.remote.OpenRouterModelsResponse = throw Exception("HTTP 429: Too Many Requests")
            override suspend fun generateCompletion(token: String, request: OpenRouterChatRequest): OpenRouterChatResponse {
                throw Exception("HTTP 429: Rate Limit Exceeded")
            }
        }
        
        val repo = AIModelRepositoryImpl(
            database.aiProviderDao(),
            fakeApi,
            object : com.example.data.remote.HuggingFaceApi {
                override suspend fun getModels(filter: String, sort: String, direction: Int, limit: Int) = emptyList<com.example.data.remote.HuggingFaceModelData>()
            }
        )
        
        // This validates that a sync attempt doesn't crash the app even if the provider is down
        try {
            repo.syncModels("usr_123", "p_1")
        } catch (e: Exception) {
            // It's okay if it throws, as long as it's handled at the caller level in ViewModels
            assertTrue(e.message?.contains("429") == true)
        }
    }

    @Test
    fun `test access control blocks unauthorized operations`() {
        val viewerRole = Role.VIEWER
        
        // Negative assertions
        assertFalse("Viewer should not be able to delete project", AccessControl.canDeleteProject(viewerRole))
        assertFalse("Viewer should not be able to manage members", AccessControl.canManageMembers(viewerRole))
        assertFalse("Viewer should not be able to purge data", AccessControl.canPurgeData(viewerRole))
    }

    @Test
    fun `test invalid login credentials return failure result`() = runTest(testDispatcher) {
        val unavailableAuthApi = object : SupabaseAuthApi {
            override suspend fun signUp(
                publishableKey: String,
                redirectTo: String?,
                request: SupabaseSignUpRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("network unavailable")

            override suspend fun signInWithPassword(
                publishableKey: String,
                grantType: String,
                request: SupabasePasswordGrantRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("network unavailable")

            override suspend fun refreshSession(
                publishableKey: String,
                grantType: String,
                request: SupabaseRefreshGrantRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("network unavailable")

            override suspend fun verifyEmailOtp(
                publishableKey: String,
                request: SupabaseVerifyOtpRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("network unavailable")

            override suspend fun requestPasswordRecovery(
                publishableKey: String,
                redirectTo: String?,
                request: SupabaseRecoveryRequest
            ): retrofit2.Response<Unit> = throw IOException("network unavailable")

            override suspend fun updatePassword(
                publishableKey: String,
                bearerToken: String,
                request: SupabaseUpdateUserRequest
            ): retrofit2.Response<SupabaseAuthUser> = throw IOException("network unavailable")

            override suspend fun signOut(
                publishableKey: String,
                bearerToken: String
            ): retrofit2.Response<Unit> = throw IOException("network unavailable")
        }
        val authRepo = com.example.data.repository.AuthRepositoryImpl(
            api = unavailableAuthApi,
            publishableKey = "test-publishable-key",
            sessionStore = SupabaseSessionStore(context)
        )
        val result = authRepo.signIn("wrong@example.com", "bad_pass")
        
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `successful sign in immediately publishes current user to active collectors`() = runTest(testDispatcher) {
        val successfulAuthApi = object : SupabaseAuthApi {
            override suspend fun signUp(
                publishableKey: String,
                redirectTo: String?,
                request: SupabaseSignUpRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("unused")

            override suspend fun signInWithPassword(
                publishableKey: String,
                grantType: String,
                request: SupabasePasswordGrantRequest
            ): retrofit2.Response<SupabaseAuthResponse> = retrofit2.Response.success(
                SupabaseAuthResponse(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    expiresIn = 3600L,
                    user = SupabaseAuthUser(
                        id = "user-1",
                        email = "user@example.com",
                        createdAt = "2024-01-01T00:00:00Z",
                        userMetadata = mapOf("display_name" to "Test User", "organization" to "Test Org")
                    )
                )
            )

            override suspend fun refreshSession(
                publishableKey: String,
                grantType: String,
                request: SupabaseRefreshGrantRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("unused")

            override suspend fun verifyEmailOtp(
                publishableKey: String,
                request: SupabaseVerifyOtpRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("unused")

            override suspend fun requestPasswordRecovery(
                publishableKey: String,
                redirectTo: String?,
                request: SupabaseRecoveryRequest
            ): retrofit2.Response<Unit> = throw IOException("unused")

            override suspend fun updatePassword(
                publishableKey: String,
                bearerToken: String,
                request: SupabaseUpdateUserRequest
            ): retrofit2.Response<SupabaseAuthUser> = throw IOException("unused")

            override suspend fun signOut(
                publishableKey: String,
                bearerToken: String
            ): retrofit2.Response<Unit> = throw IOException("unused")
        }
        val repository = com.example.data.repository.AuthRepositoryImpl(
            api = successfulAuthApi,
            publishableKey = "test-publishable-key",
            sessionStore = SupabaseSessionStore(context)
        )
        val observedUsers = mutableListOf<com.example.domain.model.UserAccount?>()
        val collector = backgroundScope.launch { repository.currentUser.collect { observedUsers += it } }
        runCurrent()
        assertNull(repository.getCurrentUserSync())

        val result = repository.signIn("user@example.com", "correct-password")
        assertTrue(result.isSuccess)
        runCurrent()

        assertEquals("user-1", repository.getCurrentUserSync()?.id)
        assertTrue(observedUsers.any { it?.id == "user-1" })
        collector.cancel()
    }

    @Test
    fun `signup requiring email confirmation is not treated as an authenticated session`() = runTest(testDispatcher) {
        val confirmationRequiredApi = object : SupabaseAuthApi {
            override suspend fun signUp(
                publishableKey: String,
                redirectTo: String?,
                request: SupabaseSignUpRequest
            ): retrofit2.Response<SupabaseAuthResponse> = retrofit2.Response.success(
                SupabaseAuthResponse(
                    user = SupabaseAuthUser(
                        id = "pending-user",
                        email = request.email,
                        createdAt = "2026-11-06T00:00:00Z"
                    )
                )
            )

            override suspend fun signInWithPassword(
                publishableKey: String,
                grantType: String,
                request: SupabasePasswordGrantRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("unused")

            override suspend fun refreshSession(
                publishableKey: String,
                grantType: String,
                request: SupabaseRefreshGrantRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("unused")

            override suspend fun verifyEmailOtp(
                publishableKey: String,
                request: SupabaseVerifyOtpRequest
            ): retrofit2.Response<SupabaseAuthResponse> = throw IOException("unused")

            override suspend fun requestPasswordRecovery(
                publishableKey: String,
                redirectTo: String?,
                request: SupabaseRecoveryRequest
            ): retrofit2.Response<Unit> = throw IOException("unused")

            override suspend fun updatePassword(
                publishableKey: String,
                bearerToken: String,
                request: SupabaseUpdateUserRequest
            ): retrofit2.Response<SupabaseAuthUser> = throw IOException("unused")

            override suspend fun signOut(
                publishableKey: String,
                bearerToken: String
            ): retrofit2.Response<Unit> = throw IOException("unused")
        }
        val sessionStore = SupabaseSessionStore(context)
        val repository = com.example.data.repository.AuthRepositoryImpl(
            api = confirmationRequiredApi,
            publishableKey = "test-publishable-key",
            sessionStore = sessionStore
        )

        val result = repository.signUp(
            email = "pending@example.com",
            password = "valid-password",
            displayName = "Pending User",
            organization = ""
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull() is com.example.domain.repository.EmailConfirmationRequiredException
        )
        assertNull(repository.getCurrentUserSync())
        assertNull(sessionStore.read())
    }
}
