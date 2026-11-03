package com.example.domain.governance

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * GovernanceEngine is responsible for enforcing security, permissions, and agent governance
 * within the AI Vibe Studio environment.
 */
interface GovernanceEngine {
    
    /**
     * Checks if the agent has the required permission level for a specific action.
     * @param workspaceId The ID of the workspace.
     * @param requestedLevel The required permission level.
     * @return True if permitted, false otherwise.
     */
    suspend fun checkPermission(workspaceId: String, requestedLevel: AgentPermissionLevel): Boolean

    /**
     * Logs an agent activity for audit purposes.
     */
    suspend fun logActivity(log: AgentActivityLog)

    /**
     * Requests user approval for a sensitive action.
     */
    suspend fun requestApproval(approval: PendingApproval): String

    /**
     * Gets the status of a pending approval.
     */
    fun getApprovalStatus(approvalId: String): Flow<ApprovalStatus>

    /**
     * Masks sensitive information (like API keys) in strings.
     */
    fun maskSecrets(input: String): String

    /**
     * Verifies if a command or file path is within the allowed workspace boundary.
     * Mitigates Prompt Injection and unauthorized access.
     */
    fun isWithinBoundary(workspaceId: String, path: String): Boolean
}
