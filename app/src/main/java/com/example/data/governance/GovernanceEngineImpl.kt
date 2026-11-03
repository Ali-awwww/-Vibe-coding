package com.example.data.governance

import com.example.data.local.VibeStudioDao
import com.example.domain.governance.GovernanceEngine
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class GovernanceEngineImpl(
    private val vibeStudioDao: VibeStudioDao
) : GovernanceEngine {

    private val secretPatterns = listOf(
        Regex("(?i)api[_-]?key[:=]\\s*['\"]?([a-zA-Z0-0_-]{20,})['\"]?"),
        Regex("(?i)secret[:=]\\s*['\"]?([a-zA-Z0-0_-]{20,})['\"]?"),
        Regex("(?i)password[:=]\\s*['\"]?([^'\"\\s]{8,})['\"]?"),
        Regex("(?i)token[:=]\\s*['\"]?([a-zA-Z0-0._-]{30,})['\"]?")
    )

    override suspend fun checkPermission(workspaceId: String, requestedLevel: AgentPermissionLevel): Boolean {
        val workspace = vibeStudioDao.getWorkspaceById(workspaceId) ?: return false
        
        return when (requestedLevel) {
            AgentPermissionLevel.READ -> true // Everyone can read if they have access to workspace
            AgentPermissionLevel.EDIT -> workspace.permissionLevel == AgentPermissionLevel.EDIT || 
                                        workspace.permissionLevel == AgentPermissionLevel.EXECUTE
            AgentPermissionLevel.EXECUTE -> workspace.permissionLevel == AgentPermissionLevel.EXECUTE
        }
    }

    override suspend fun logActivity(log: AgentActivityLog) {
        val sanitizedDetails = maskSecrets(log.details)
        vibeStudioDao.insertActivityLog(log.copy(details = sanitizedDetails))
    }

    override suspend fun requestApproval(approval: PendingApproval): String {
        val id = UUID.randomUUID().toString()
        val approvalWithId = approval.copy(id = id, status = ApprovalStatus.PENDING)
        vibeStudioDao.insertPendingApproval(approvalWithId)
        return id
    }

    override fun getApprovalStatus(approvalId: String): Flow<ApprovalStatus> {
        return vibeStudioDao.getPendingApprovalById(approvalId).map { it?.status ?: ApprovalStatus.REJECTED }
    }

    override fun maskSecrets(input: String): String {
        var masked = input
        secretPatterns.forEach { regex ->
            masked = regex.replace(masked) { matchResult ->
                val key = matchResult.groupValues[1]
                val maskedKey = key.take(4) + "****" + key.takeLast(4)
                matchResult.value.replace(key, maskedKey)
            }
        }
        return masked
    }

    override fun isWithinBoundary(workspaceId: String, path: String): Boolean {
        // Prevent path traversal and access outside project files
        val normalizedPath = path.replace("\\", "/").trimStart('/')
        
        // Block absolute paths or those attempting to go up levels
        if (path.startsWith("/") || path.contains("..")) return false
        
        // In a real sandbox, we'd check against the workspace root directory.
        // Here we assume any relative path that doesn't use ".." is within the boundary.
        return true
    }
}
