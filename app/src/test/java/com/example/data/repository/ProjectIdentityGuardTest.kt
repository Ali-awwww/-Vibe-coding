package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityGuardTest {
    @Test(expected = IllegalStateException::class)
    fun missingSupabaseIdentityIsRejected() {
        ProjectIdentityGuard.requireSupabaseUserId(currentUserId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankRequestedIdentityIsRejected() {
        ProjectIdentityGuard.requireSupabaseUserId(
            currentUserId = "user-a",
            requestedUserId = " "
        )
    }

    @Test(expected = SecurityException::class)
    fun anotherAccountsProjectScopeIsRejected() {
        ProjectIdentityGuard.requireSupabaseUserId(
            currentUserId = "user-a",
            requestedUserId = "user-b"
        )
    }

    @Test
    fun activeAccountScopeIsReturned() {
        assertEquals(
            "user-a",
            ProjectIdentityGuard.requireSupabaseUserId(
                currentUserId = "user-a",
                requestedUserId = "user-a"
            )
        )
    }

    @Test(expected = IllegalStateException::class)
    fun missingFirebaseIdentityCannotAuthorizeLegacyFirestore() {
        ProjectIdentityGuard.requireLegacyFirebaseIdentityMatch(
            supabaseUserId = "user-a",
            firebaseUserId = null
        )
    }

    @Test(expected = IllegalStateException::class)
    fun differentFirebaseIdentityCannotAuthorizeLegacyFirestore() {
        ProjectIdentityGuard.requireLegacyFirebaseIdentityMatch(
            supabaseUserId = "supabase-user",
            firebaseUserId = "firebase-user"
        )
    }

    @Test
    fun matchingFirebaseIdentityAllowsLegacyFirestoreBoundary() {
        ProjectIdentityGuard.requireLegacyFirebaseIdentityMatch(
            supabaseUserId = "user-a",
            firebaseUserId = "user-a"
        )
    }
}
