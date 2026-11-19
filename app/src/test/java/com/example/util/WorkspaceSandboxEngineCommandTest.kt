package com.example.util

import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSandboxEngineCommandTest {
    private val workspace = Workspace(id = "workspace-commands", name = "Command Test")

    @Test
    fun processCommandsFailClosedInsteadOfReturningFabricatedSuccess() {
        listOf(
            "npm test",
            "npm install",
            "pip install requests",
            "python main.py",
            "gradle :app:assembleDebug",
            "flutter test",
            "git status",
            "adb devices"
        ).forEach { command ->
            val output = WorkspaceSandboxEngine.executeSandboxCommand(workspace, emptyList(), command)
            assertFalse("Command unexpectedly succeeded: $command", output.result.isSuccess)
            assertEquals(126, output.result.exitCode)
            assertTrue(output.result.stderr.contains("No verified isolated process runner"))
            assertTrue(output.createdFiles.isEmpty())
            assertTrue(output.modifiedFiles.isEmpty())
        }
    }

    @Test
    fun unknownCommandsDoNotReportSuccess() {
        val output = WorkspaceSandboxEngine.executeSandboxCommand(workspace, emptyList(), "made-up-command")
        assertFalse(output.result.isSuccess)
        assertEquals(127, output.result.exitCode)
        assertTrue(output.result.stderr.contains("COMMAND_UNSUPPORTED"))
    }

    @Test
    fun touchCreatesAStoredWorkspaceFileWithoutClaimingProcessExecution() {
        val output = WorkspaceSandboxEngine.executeSandboxCommand(workspace, emptyList(), "touch src/Main.kt")
        assertTrue(output.result.isSuccess)
        assertEquals(1, output.createdFiles.size)
        assertEquals("src/Main.kt", output.createdFiles.single().path)
        assertTrue(output.modifiedFiles.isEmpty())
    }

    @Test
    fun dangerousHostCommandsRemainBlocked() {
        val output = WorkspaceSandboxEngine.executeSandboxCommand(workspace, emptyList(), "sudo rm -rf /")
        assertFalse(output.result.isSuccess)
        assertEquals(126, output.result.exitCode)
        assertTrue(output.result.stderr.contains("SECURITY_VIOLATION"))
    }

    @Test
    fun processInspectionDoesNotInventPortsOrProcesses() {
        listOf("ports", "ps", "kill 1024", "env").forEach { command ->
            val output = WorkspaceSandboxEngine.executeSandboxCommand(workspace, emptyList(), command)
            assertFalse(output.result.isSuccess)
            assertEquals(126, output.result.exitCode)
            assertTrue(output.result.stderr.contains("PROCESS_INSPECTION_UNAVAILABLE"))
        }
    }
}
