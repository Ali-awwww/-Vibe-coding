package com.example.data.repository

import com.example.data.remote.supabase.SupabaseIdentityLinkStore
import com.example.domain.model.UserAccount
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DualSessionAuthRepositoryTest {

    @Test
    fun successfulGoogleSignInLinksDistinctProviderSubjectsAfterBothSessionsExist() = runTest {
        val firebase = FakeAuthRepository().apply {
            nextGoogleUser = user("firebase-uid", "Person@example.com")
        }
        val supabase = FakeAuthRepository().apply {
            nextGoogleUser = user("supabase-uid", "person@example.com")
        }
        val links = FakeIdentityLinkStore()
        val repository = DualSessionAuthRepository(firebase, supabase, links)

        val result = repository.signInWithGoogle("same-google-id-token")

        assertTrue(result.isSuccess)
        assertEquals("same-google-id-token", firebase.lastGoogleToken)
        assertEquals("same-google-id-token", supabase.lastGoogleToken)
        assertEquals("firebase-uid", repository.getCurrentUserSync()?.id)
        assertEquals("supabase-uid", repository.getSupabaseUserId("firebase-uid"))
        assertNull(repository.getSupabaseUserId("another-firebase-user"))
        assertEquals("person@example.com", links.email)
    }

    @Test
    fun secondaryProviderFailureKeepsRealFirebaseSessionButRemovesOldLink() = runTest {
        val firebase = FakeAuthRepository().apply {
            nextGoogleUser = user("firebase-uid", "person@example.com")
        }
        val supabase = FakeAuthRepository().apply {
            nextGoogleResult = Result.failure(IOException("secondary provider unavailable"))
            nextGoogleUser = user("old-supabase-user", "person@example.com")
        }
        val links = FakeIdentityLinkStore().apply {
            write("firebase-uid", "stale-supabase-user", "person@example.com")
        }
        val reported = mutableListOf<Throwable>()
        val repository = DualSessionAuthRepository(firebase, supabase, links, reported::add)

        val result = repository.signInWithGoogle("google-token")

        assertTrue(result.isSuccess)
        assertEquals("firebase-uid", repository.getCurrentUserSync()?.id)
        assertNull(repository.getSupabaseUserId("firebase-uid"))
        assertTrue(supabase.signOutCalls >= 2)
        assertEquals(1, reported.size)
    }

    @Test
    fun mismatchedProviderEmailsNeverCreateAnIdentityLink() = runTest {
        val firebase = FakeAuthRepository().apply {
            nextGoogleUser = user("firebase-uid", "owner@example.com")
        }
        val supabase = FakeAuthRepository().apply {
            nextGoogleUser = user("supabase-uid", "different@example.com")
        }
        val links = FakeIdentityLinkStore()
        val repository = DualSessionAuthRepository(firebase, supabase, links)

        val result = repository.signInWithGoogle("google-token")

        assertTrue(result.isSuccess)
        assertNull(repository.getSupabaseUserId("firebase-uid"))
        assertNull(supabase.getCurrentUserSync())
    }

    @Test
    fun firebaseFailureDoesNotAttemptSecondarySupabaseSignIn() = runTest {
        val firebase = FakeAuthRepository().apply {
            nextGoogleResult = Result.failure(IllegalStateException("Firebase rejected token"))
        }
        val supabase = FakeAuthRepository()
        val repository = DualSessionAuthRepository(firebase, supabase, FakeIdentityLinkStore())

        val result = repository.signInWithGoogle("google-token")

        assertTrue(result.isFailure)
        assertNull(supabase.lastGoogleToken)
        assertNull(repository.getSupabaseUserId("firebase-uid"))
    }

    @Test
    fun appIdentityRemainsHiddenUntilSecondarySessionExchangeFinishes() = runTest {
        val firebase = FakeAuthRepository().apply {
            nextGoogleUser = user("firebase-uid", "person@example.com")
        }
        val supabase = FakeAuthRepository().apply {
            nextGoogleUser = user("supabase-uid", "person@example.com")
        }
        val exchangeStarted = CompletableDeferred<Unit>()
        val finishExchange = CompletableDeferred<Unit>()
        supabase.googleSignInHandler = {
            exchangeStarted.complete(Unit)
            finishExchange.await()
            Result.success(Unit)
        }
        val repository = DualSessionAuthRepository(firebase, supabase, FakeIdentityLinkStore())

        val signIn = async { repository.signInWithGoogle("google-token") }
        runCurrent()
        exchangeStarted.await()

        assertNull(repository.getCurrentUserSync())
        assertNull(repository.currentUser.first())

        finishExchange.complete(Unit)
        advanceUntilIdle()

        assertTrue(signIn.await().isSuccess)
        assertEquals("firebase-uid", repository.getCurrentUserSync()?.id)
    }

    @Test
    fun signOutClearsTheLinkAndAttemptsBothProviderLogouts() = runTest {
        val firebase = FakeAuthRepository().apply {
            nextGoogleUser = user("firebase-uid", "person@example.com")
        }
        val supabase = FakeAuthRepository().apply {
            nextGoogleUser = user("supabase-uid", "person@example.com")
        }
        val links = FakeIdentityLinkStore()
        val repository = DualSessionAuthRepository(firebase, supabase, links)

        assertTrue(repository.signInWithGoogle("google-token").isSuccess)
        repository.signOut()

        assertNull(repository.getCurrentUserSync())
        assertNull(repository.getSupabaseUserId("firebase-uid"))
        assertTrue(firebase.signOutCalls >= 1)
        assertTrue(supabase.signOutCalls >= 2)
    }

    private class FakeAuthRepository : AuthRepository {
        private val state = MutableStateFlow<UserAccount?>(null)
        override val currentUser = state.asStateFlow()
        var nextGoogleUser: UserAccount? = null
        var nextGoogleResult: Result<Unit> = Result.success(Unit)
        var googleSignInHandler: (suspend (String) -> Result<Unit>)? = null
        var lastGoogleToken: String? = null
        var signOutCalls = 0

        override fun getCurrentUserSync(): UserAccount? = state.value

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String,
            organization: String
        ): Result<UserAccount> = Result.failure(UnsupportedOperationException())

        override suspend fun signIn(email: String, password: String): Result<UserAccount> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signOut() {
            signOutCalls += 1
            state.value = null
        }

        override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
            lastGoogleToken = idToken
            val result = googleSignInHandler?.invoke(idToken) ?: nextGoogleResult
            if (result.isSuccess) state.value = nextGoogleUser
            return result
        }
    }

    private class FakeIdentityLinkStore : SupabaseIdentityLinkStore {
        private var firebaseUserId: String? = null
        private var supabaseUserId: String? = null
        var email: String? = null

        override fun getSupabaseUserId(firebaseUserId: String): String? =
            if (this.firebaseUserId == firebaseUserId) supabaseUserId else null

        override fun write(firebaseUserId: String, supabaseUserId: String, email: String) {
            this.firebaseUserId = firebaseUserId
            this.supabaseUserId = supabaseUserId
            this.email = email
        }

        override fun clear() {
            firebaseUserId = null
            supabaseUserId = null
            email = null
        }
    }

    private fun user(id: String, email: String) = UserAccount(
        id = id,
        email = email,
        displayName = "Test User",
        organization = "",
        createdAt = 1L,
        lastLoginAt = 1L
    )
}
