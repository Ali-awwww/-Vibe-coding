package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityGuardTest {
    @Test(expected = IllegalStateException::class)
    fun missingAuthenticatedIdentityIsRejected() {
        ProjectIdentityGuard.requireAuthenticatedUserId(currentUserId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankRequestedIdentityIsRejected() {
        ProjectIdentityGuard.requireAuthenticatedUserId(
            currentUserId = "user-a",
            requestedUserId = " "
        )
    }

    @Test(expected = SecurityException::class)
    fun anotherAccountsProjectScopeIsRejected() {
        ProjectIdentityGuard.requireAuthenticatedUserId(
            currentUserId = "user-a",
            requestedUserId = "user-b"
        )
    }

    @Test
    fun activeAccountScopeIsReturned() {
        assertEquals(
            "user-a",
            ProjectIdentityGuard.requireAuthenticatedUserId(
                currentUserId = "user-a",
                requestedUserId = "user-a"
            )
        )
    }

    @Test(expected = SecurityException::class)
    fun mismatchedFirebaseIdentityCannotAuthorizeFirestore() {
        ProjectIdentityGuard.requireAuthenticatedUserId(
            currentUserId = "user-a",
            firebaseUserId = "user-b"
        )
    }

    @Test
    fun matchingFirebaseIdentityAllowsFirestoreBoundary() {
        assertEquals(
            "user-a",
            ProjectIdentityGuard.requireAuthenticatedUserId(
                currentUserId = "user-a",
                firebaseUserId = "user-a"
            )
        )
    }
}
