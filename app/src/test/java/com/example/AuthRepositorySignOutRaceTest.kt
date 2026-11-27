package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.FirebaseAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config
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
    fun signOutClearsCurrentUserAndCompletesSafely() = runTest(testDispatcher) {
        var credentialCleanupCalls = 0
        var firebaseSignOutCalls = 0
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { null },
            credentialStateCleaner = { credentialCleanupCalls++ },
            firebaseSignOutAction = { firebaseSignOutCalls++ }
        )

        repository.signOut()

        assertEquals(1, credentialCleanupCalls)
        assertEquals(1, firebaseSignOutCalls)
        assertNull(repository.getCurrentUserSync())
        assertNull(repository.currentUser.first())
    }

    @Test
    fun credentialCleanupTimeoutDoesNotPreventFirebaseSignOut() = runTest(testDispatcher) {
        var firebaseSignOutCalls = 0
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { null },
            credentialStateCleaner = { awaitCancellation() },
            firebaseSignOutAction = { firebaseSignOutCalls++ }
        )

        repository.signOut()

        assertEquals(1, firebaseSignOutCalls)
        assertNull(repository.getCurrentUserSync())
    }
}
