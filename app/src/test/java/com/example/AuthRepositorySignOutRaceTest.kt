package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.FirebaseAuthRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositorySignOutRaceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signOutClearsCurrentUserAndCompletesSafely() = runTest(testDispatcher) {
        val firebaseAuth = FirebaseAuth.getInstance()
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { firebaseAuth }
        )

        repository.signOut()

        assertNull(repository.getCurrentUserSync())
        assertNull(repository.currentUser.first())
    }

    @Test
    fun signOutWithNullFirebaseAuthCompletesWithoutThrowing() = runTest(testDispatcher) {
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { null }
        )

        repository.signOut()

        assertNull(repository.getCurrentUserSync())
        assertNull(repository.currentUser.first())
    }
}
