package com.example.util

import com.example.domain.model.IntegrationType
import com.example.domain.model.TestCaseResult
import com.example.domain.model.Workspace
import org.junit.Assert.assertEquals
import org.junit.Test

class VibeQualityTestingEngineTest {

    @Test
    fun generatedWorkspaceFailureUsesInternalQaSource() {
        val report = VibeQualityTestingEngine.generateCrashReportFromFailure(
            workspace = Workspace(
                id = "workspace-1",
                name = "Sample workspace",
                techStack = "Android Kotlin",
                ownerId = "user-1"
            ),
            testCase = TestCaseResult(
                suiteName = "Unit tests",
                name = "assertion failure",
                errorMessage = "Expected true"
            )
        )

        assertEquals(IntegrationType.INTERNAL_QA, report.source)
    }
}
