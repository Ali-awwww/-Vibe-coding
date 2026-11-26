package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.OpenRouterApi
import com.example.data.remote.OpenRouterChatRequest
import com.example.data.remote.OpenRouterChatResponse
import com.example.data.repository.AIModelRepositoryImpl
import com.example.data.repository.FirebaseAuthRepository
import com.example.domain.model.AccessControl
import com.example.domain.model.Role
import com.example.domain.repository.FirebaseAuthNotConfiguredException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
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
            override suspend fun getModels(token: String): com.example.data.remote.OpenRouterModelsResponse =
                throw Exception("HTTP 429: Too Many Requests")

            override suspend fun generateCompletion(
                token: String,
                request: OpenRouterChatRequest
            ): OpenRouterChatResponse {
                throw Exception("HTTP 429: Rate Limit Exceeded")
            }
        }

        val repo = AIModelRepositoryImpl(
            database.aiProviderDao(),
            fakeApi,
            object : com.example.data.remote.HuggingFaceApi {
                override suspend fun getModels(
                    filter: String,
                    sort: String,
                    direction: Int,
                    limit: Int
                ) = emptyList<com.example.data.remote.HuggingFaceModelData>()
            }
        )

        try {
            repo.syncModels("usr_123", "p_1")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("429") == true)
        }
    }

    @Test
    fun `test access control blocks unauthorized operations`() {
        val viewerRole = Role.VIEWER

        assertFalse("Viewer should not be able to delete project", AccessControl.canDeleteProject(viewerRole))
        assertFalse("Viewer should not be able to manage members", AccessControl.canManageMembers(viewerRole))
        assertFalse("Viewer should not be able to purge data", AccessControl.canPurgeData(viewerRole))
    }

    @Test
    fun `unconfigured FirebaseAuth returns failure result on sign in and sign up`() = runTest(testDispatcher) {
        val authRepo = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { null }
        )

        val signInResult = authRepo.signIn("wrong@example.com", "bad_pass")
        val signUpResult = authRepo.signUp("wrong@example.com", "valid_pass_123", "Dev", "Org")

        assertTrue(signInResult.isFailure)
        assertTrue(signInResult.exceptionOrNull() is FirebaseAuthNotConfiguredException)
        assertTrue(signUpResult.isFailure)
        assertTrue(signUpResult.exceptionOrNull() is FirebaseAuthNotConfiguredException)
    }

    @Test
    fun `blank credentials and short passwords fail closed before Firebase call`() = runTest(testDispatcher) {
        val authRepo = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { FirebaseAuth.getInstance() }
        )

        val blankSignIn = authRepo.signIn("   ", "password")
        val shortSignUp = authRepo.signUp("dev@example.com", "1234", "Dev", "")

        assertTrue(blankSignIn.isFailure)
        assertNotNull(blankSignIn.exceptionOrNull())
        assertTrue(shortSignUp.isFailure)
        assertNotNull(shortSignUp.exceptionOrNull())
    }

    @Test
    fun `signOut emits null session cleanly`() = runTest(testDispatcher) {
        val authRepo = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { FirebaseAuth.getInstance() }
        )

        authRepo.signOut()

        assertNull(authRepo.getCurrentUserSync())
        assertNull(authRepo.currentUser.first())
    }
}
