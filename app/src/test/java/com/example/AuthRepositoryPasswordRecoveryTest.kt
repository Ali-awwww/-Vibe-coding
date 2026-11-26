package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.FirebaseAuthRepository
import com.example.domain.repository.FirebaseAuthNotConfiguredException
import com.example.domain.repository.PasswordRecoveryCredentials
import com.example.presentation.auth.PasswordRecoveryLinkResult
import com.example.presentation.auth.parsePasswordRecoveryLink
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryPasswordRecoveryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    @Test
    fun firebaseOobCodeRecoveryLinkParsesIntoReadyCredentials() {
        val parsed = parsePasswordRecoveryLink(
            "com.aistudio.crashintelligence.vyfrip://auth/recovery?mode=resetPassword&oobCode=firebase-oob-code"
        )

        assertTrue(parsed is PasswordRecoveryLinkResult.Ready)
        assertEquals(
            "firebase-oob-code",
            (parsed as PasswordRecoveryLinkResult.Ready).credentials.oobCode
        )
    }

    @Test
    fun blankGoogleIdTokenIsRejectedBeforeCallingFirebaseAuth() = runTest {
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { FirebaseAuth.getInstance() }
        )

        val result = repository.signInWithGoogle("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun unconfiguredFirebaseAuthReturnsExplicitConfigurationFailure() = runTest {
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { null }
        )

        val signInResult = repository.signIn("owner@example.com", "password123")
        val recoveryResult = repository.requestPasswordRecovery("owner@example.com")
        val googleResult = repository.signInWithGoogle("valid-token")

        assertTrue(signInResult.exceptionOrNull() is FirebaseAuthNotConfiguredException)
        assertTrue(recoveryResult.exceptionOrNull() is FirebaseAuthNotConfiguredException)
        assertTrue(googleResult.exceptionOrNull() is FirebaseAuthNotConfiguredException)
    }

    @Test
    fun completePasswordRecoveryRejectsMissingOobCodeAndShortPassword() = runTest {
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { FirebaseAuth.getInstance() }
        )

        val missingOob = repository.completePasswordRecovery(
            credentials = PasswordRecoveryCredentials(oobCode = " "),
            newPassword = "new-secure-password"
        )
        assertTrue(missingOob.isFailure)
        assertTrue(missingOob.exceptionOrNull() is IllegalArgumentException)

        val shortPassword = repository.completePasswordRecovery(
            credentials = PasswordRecoveryCredentials(oobCode = "valid-oob"),
            newPassword = "short"
        )
        assertTrue(shortPassword.isFailure)
        assertTrue(shortPassword.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun requestPasswordRecoveryRejectsBlankEmail() = runTest {
        val repository = FirebaseAuthRepository(
            context = context,
            firestoreProvider = { null },
            firebaseAuthProvider = { FirebaseAuth.getInstance() }
        )

        val result = repository.requestPasswordRecovery("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
