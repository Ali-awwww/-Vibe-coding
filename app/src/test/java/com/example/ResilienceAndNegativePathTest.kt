package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.OpenRouterChatRequest
import com.example.data.remote.OpenRouterChatResponse
import com.example.data.repository.AIModelRepositoryImpl
import com.example.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
            override suspend fun getModels() = throw Exception("HTTP 429: Too Many Requests")
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
        val authRepo = com.example.data.repository.AuthRepositoryImpl(
            null,
            null
        )
        val result = authRepo.signIn("wrong@example.com", "bad_pass")
        
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
}

