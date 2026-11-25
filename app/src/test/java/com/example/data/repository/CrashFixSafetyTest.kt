package com.example.data.repository

import com.example.domain.model.SymbolicatedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashFixSafetyTest {
    @Test
    fun unknownOrUncorroboratedSourceLocationsAreRejected() {
        val stackTrace = "at com.example.ProfileFragment.onCreate(ProfileFragment.kt:74)"

        assertNull(resolveVerifiedCrashSourceLocation("Unknown", stackTrace, emptyList()))
        assertNull(resolveVerifiedCrashSourceLocation("ProfileFragment.kt:75", stackTrace, emptyList()))
        assertNull(resolveVerifiedCrashSourceLocation("../ProfileFragment.kt:74", stackTrace, emptyList()))
    }

    @Test
    fun sourceLocationCanBeGroundedByRawStackTraceOrSymbolicatedFrame() {
        val stackTrace = "at com.example.ProfileFragment.onCreate(ProfileFragment.kt:74)"
        val raw = resolveVerifiedCrashSourceLocation("ProfileFragment.kt:74", stackTrace, emptyList())
        val symbolicated = resolveVerifiedCrashSourceLocation(
            "src/main/java/com/example/ProfileFragment.kt:74",
            "at a.b.c(Unknown Source)",
            listOf(
                SymbolicatedFrame(
                    originalLine = "at a.b.c(Unknown Source)",
                    fileName = "src/main/java/com/example/ProfileFragment.kt",
                    methodName = "onCreate",
                    lineNumber = 74,
                    libraryName = null
                )
            )
        )

        assertEquals("ProfileFragment.kt", raw?.fileName)
        assertEquals(74, raw?.lineNumber)
        assertEquals("src/main/java/com/example/ProfileFragment.kt", symbolicated?.pathHint)
    }

    @Test
    fun ambiguousOrMismatchedRepositoryPathsAreNotSelected() {
        val location = VerifiedCrashSourceLocation(
            pathHint = "ProfileFragment.kt",
            fileName = "ProfileFragment.kt",
            lineNumber = 74
        )

        assertNull(
            selectUniqueCrashSourcePath(
                listOf(
                    "app/src/main/java/com/example/ProfileFragment.kt",
                    "feature/src/main/java/com/example/ProfileFragment.kt"
                ),
                location
            )
        )
        assertNull(selectUniqueCrashSourcePath(listOf("src/OtherFragment.kt"), location))
        assertEquals(
            "src/main/java/com/example/ProfileFragment.kt",
            selectUniqueCrashSourcePath(listOf("src/main/java/com/example/ProfileFragment.kt"), location)
        )
    }

    @Test
    fun partialCodeBlockIsRejectedBeforeItCanReplaceAWholeFile() {
        val response = FixResultResponse(
            suggestedCode = "fun onlyOneFragment() {}",
            explanation = "A partial patch",
            potentialSideEffects = "Unknown",
            fullFileContent = false
        )

        val result = runCatching { requireCompleteFixFile(response) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("ملفاً كاملاً"))
    }

    @Test
    fun emptyFullFileResponseIsRejected() {
        val response = FixResultResponse(
            suggestedCode = "   ",
            explanation = "No change",
            potentialSideEffects = "None",
            fullFileContent = true
        )

        assertTrue(runCatching { requireCompleteFixFile(response) }.isFailure)
    }
}
