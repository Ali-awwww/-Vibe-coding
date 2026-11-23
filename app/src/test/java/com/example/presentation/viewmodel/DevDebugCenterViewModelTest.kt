package com.example.presentation.viewmodel

import com.example.domain.model.UserAccount
import com.example.domain.model.Workspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevDebugCenterViewModelTest {

    @Test
    fun accountSwitchCancelsPreviousUsersWorkspaceSubscription() = runBlocking {
        val users = MutableStateFlow<UserAccount?>(UserAccount(id = "user-a"))
        val requestedUserIds = mutableListOf<String>()
        var previousSubscriptionCancelled = false
        val firstUserLoaded = CompletableDeferred<Unit>()
        val received = mutableListOf<AuthenticatedWorkspaceFeed>()

        val collector = launch {
            observeAuthenticatedWorkspaceFeed(users) { userId ->
                if (userId == "user-a") {
                    flow {
                        requestedUserIds += userId
                        emit(emptyList())
                        firstUserLoaded.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            previousSubscriptionCancelled = true
                        }
                    }
                } else {
                    flow {
                        requestedUserIds += userId
                        emit(listOf(Workspace(id = "workspace-b", ownerId = userId)))
                    }
                }
            }.take(4).toList(received)
        }

        firstUserLoaded.await()
        users.value = UserAccount(id = "user-b")
        collector.join()

        assertEquals(listOf("user-a", "user-b"), requestedUserIds)
        assertTrue(previousSubscriptionCancelled)
        val current = received.last() as AuthenticatedWorkspaceFeed.Loaded
        assertEquals("user-b", current.userId)
        assertEquals("workspace-b", current.workspaces.single().id)
    }

    @Test
    fun blankAuthenticatedIdentityNeverLoadsWorkspaceData() = runBlocking {
        val received = observeAuthenticatedWorkspaceFeed(
            currentUser = flowOf(UserAccount(id = "  ")),
            getWorkspaces = { error("Workspace lookup must not run without a valid identity.") }
        ).toList()

        assertEquals(listOf(AuthenticatedWorkspaceFeed.SignedOut), received)
    }
}
