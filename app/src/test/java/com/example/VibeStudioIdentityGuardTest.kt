package com.example

import com.example.domain.model.UserAccount
import com.example.presentation.viewmodel.authenticatedUserIdOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VibeStudioIdentityGuardTest {

    @Test
    fun unauthenticatedUserHasNoIdentity() {
        assertNull(authenticatedUserIdOrNull(null))
    }

    @Test
    fun blankOrWhitespaceIdentityIsRejected() {
        assertNull(authenticatedUserIdOrNull(UserAccount(id = "")))
        assertNull(authenticatedUserIdOrNull(UserAccount(id = "   ")))
    }

    @Test
    fun authenticatedIdentityIsPreservedExactly() {
        assertEquals("auth-user-42", authenticatedUserIdOrNull(UserAccount(id = "auth-user-42")))
    }
}
