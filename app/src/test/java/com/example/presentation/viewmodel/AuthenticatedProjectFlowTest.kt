package com.example.presentation.viewmodel

import com.example.domain.model.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedProjectFlowTest {
    @Test
    fun signedOutUserReceivesNoProjectsWithoutCallingRepository() = runBlocking {
        var repositoryCalled = false

        val projects = projectsForAuthenticatedUser(null) {
            repositoryCalled = true
            flowOf(listOf(Project(id = "private-project")))
        }.first()

        assertFalse(repositoryCalled)
        assertTrue(projects.isEmpty())
    }

    @Test
    fun blankUserIdReceivesNoProjectsWithoutCallingRepository() = runBlocking {
        var repositoryCalled = false

        val projects = projectsForAuthenticatedUser("  ") {
            repositoryCalled = true
            flowOf(listOf(Project(id = "private-project")))
        }.first()

        assertFalse(repositoryCalled)
        assertTrue(projects.isEmpty())
    }

    @Test
    fun authenticatedUserLoadsOnlyTheRequestedAccountScope() = runBlocking {
        val projects = projectsForAuthenticatedUser("user-a") { userId ->
            assertEquals("user-a", userId)
            flowOf(listOf(Project(id = "project-a", ownerId = userId)))
        }.first()

        assertEquals(listOf("project-a"), projects.map { it.id })
    }
}
