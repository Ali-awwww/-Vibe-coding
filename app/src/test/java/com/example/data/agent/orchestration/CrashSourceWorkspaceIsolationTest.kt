package com.example.data.agent.orchestration

import com.example.domain.model.CrashReport
import com.example.domain.model.IntegrationType
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashSourceWorkspaceIsolationTest {
    @Test
    fun externalProviderProjectIdIsNeverUsedAsWorkspaceId() = runTest {
        var workspaceLookupCalled = false
        var fileLookupCalled = false
        val result = resolveCrashSourceContext(
            crashReport = CrashReport(
                id = "sentry-event",
                projectId = "provider-project-17",
                source = IntegrationType.SENTRY,
                exceptionType = "IllegalStateException",
                message = "Remote request failed"
            ),
            userId = "user-1",
            workspaceLookup = {
                workspaceLookupCalled = true
                null
            },
            fileLookup = { _, _ ->
                fileLookupCalled = true
                emptyList()
            }
        )

        assertFalse(workspaceLookupCalled)
        assertFalse(fileLookupCalled)
        assertTrue(result.contains("لم يتم البحث في أي مساحة عمل"))
    }

    @Test
    fun internalQaUsesOnlyItsOwnedWorkspaceAndReturnsMatchingFiles() = runTest {
        val workspaceId = "workspace-qa-1"
        val requestedWorkspaceIds = mutableListOf<String>()
        var requestedQuery: String? = null
        val file = WorkspaceFile(
            id = "source-1",
            workspaceId = workspaceId,
            path = "src/main/CrashHandler.kt",
            content = "fun handleCrash() { validateState() }"
        )

        val result = resolveCrashSourceContext(
            crashReport = CrashReport(
                id = "qa-crash",
                projectId = workspaceId,
                source = IntegrationType.INTERNAL_QA,
                exceptionType = "IllegalStateException",
                message = "Invalid state"
            ),
            userId = "user-1",
            workspaceLookup = { id ->
                requestedWorkspaceIds += id
                Workspace(id = id, ownerId = "user-1")
            },
            fileLookup = { id, query ->
                requestedWorkspaceIds += id
                requestedQuery = query
                listOf(file)
            }
        )

        assertEquals(listOf(workspaceId, workspaceId), requestedWorkspaceIds)
        assertEquals("IllegalStateException Invalid state", requestedQuery)
        assertTrue(result.contains("src/main/CrashHandler.kt"))
        assertTrue(result.contains("fun handleCrash()"))
    }

    @Test
    fun internalQaRejectsAnotherUsersWorkspaceBeforeReadingFiles() = runTest {
        var fileLookupCalled = false
        val result = resolveCrashSourceContext(
            crashReport = CrashReport(
                id = "foreign-qa-crash",
                projectId = "workspace-user-2",
                source = IntegrationType.INTERNAL_QA
            ),
            userId = "user-1",
            workspaceLookup = { id -> Workspace(id = id, ownerId = "user-2") },
            fileLookup = { _, _ ->
                fileLookupCalled = true
                listOf(
                    WorkspaceFile(
                        id = "secret",
                        workspaceId = "workspace-user-2",
                        path = "private/Secret.kt",
                        content = "private user source"
                    )
                )
            }
        )

        assertFalse(fileLookupCalled)
        assertFalse(result.contains("private user source"))
        assertTrue(result.contains("لا تتوفر مساحة عمل مخوّلة"))
    }

    @Test
    fun internalQaFiltersAnyFileReturnedForADifferentWorkspace() = runTest {
        val result = resolveCrashSourceContext(
            crashReport = CrashReport(
                id = "qa-crash",
                projectId = "workspace-user-1",
                source = IntegrationType.INTERNAL_QA,
                exceptionType = "AssertionError"
            ),
            userId = "user-1",
            workspaceLookup = { id -> Workspace(id = id, ownerId = "user-1") },
            fileLookup = { _, _ ->
                listOf(
                    WorkspaceFile(
                        id = "foreign",
                        workspaceId = "workspace-user-2",
                        path = "other/Secret.kt",
                        content = "must not be included"
                    )
                )
            }
        )

        assertFalse(result.contains("must not be included"))
        assertTrue(result.contains("لم يتم العثور على ملفات مصدر"))
    }
}
