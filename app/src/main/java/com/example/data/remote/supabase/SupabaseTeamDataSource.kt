package com.example.data.remote.supabase

import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Authenticated PostgREST access for team definitions and execution history.
 * Owner/user filters are defense in depth; Supabase RLS remains authoritative.
 */
class SupabaseTeamDataSource(
    private val api: SupabaseTeamPostgrestApi,
    private val authRepository: AuthRepository,
    private val sessionStore: SupabaseSessionStore,
    private val publishableKey: String,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val teamAdapter = moshi.adapter(ModelTeam::class.java)
    private val memberAdapter = moshi.adapter(TeamMember::class.java)
    private val settingsAdapter = moshi.adapter(TeamSettings::class.java)
    private val governanceAdapter = moshi.adapter(TeamGovernanceSettings::class.java)
    private val executionAdapter = moshi.adapter(TeamExecution::class.java)
    private val stepAdapter = moshi.adapter(ExecutionStep::class.java)
    private val sharedMemoryAdapter = moshi.adapter(SharedMemory::class.java)
    private val messageAdapter = moshi.adapter(AgentMessage::class.java)
    private val memberUsageAdapter = moshi.adapter(MemberUsageSummary::class.java)
    private val failoverAdapter = moshi.adapter(ModelFailoverEvent::class.java)
    private val estimationAdapter = moshi.adapter(PreExecutionEstimation::class.java)
    private val attachedFileAdapter = moshi.adapter(AttachedFileContext::class.java)

    /**
     * Returns false only when no Supabase session is stored. A mismatched or unusable
     * stored session is an error, not permission to fall back to another account.
     */
    suspend fun isAuthenticatedAs(userId: String): Boolean {
        if (sessionStore.read() == null) return false
        val currentUser = authRepository.currentUser.first()
            ?: throw IllegalStateException("A stored Supabase session has no active authenticated user.")
        val session = sessionStore.read()
            ?: throw IllegalStateException("The Supabase session disappeared while it was being refreshed.")
        validateSession(currentUser.id, session, expectedUserId = userId)
        return true
    }

    suspend fun getTeams(userId: String): List<ModelTeam> {
        val auth = requireAuth(expectedUserId = userId)
        return api.listTeams(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            ownerIdFilter = "eq." + auth.userId
        ).map { row ->
            if (row.ownerId != auth.userId) {
                throw SecurityException("Supabase returned a team outside the active account.")
            }
            row.toDomain()
        }
    }

    suspend fun getTeamById(teamId: String): ModelTeam? {
        val auth = requireAuth()
        val row = api.getTeamById(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq." + teamId,
            ownerIdFilter = "eq." + auth.userId
        ).firstOrNull() ?: return null
        if (row.ownerId != auth.userId) {
            throw SecurityException("Supabase returned a team outside the active account.")
        }
        return row.toDomain()
    }

    suspend fun createTeam(team: ModelTeam): ModelTeam {
        val auth = requireAuth()
        if (team.ownerId.isNotBlank() && team.ownerId != auth.userId) {
            throw SecurityException("A team cannot be created for another account.")
        }
        val id = team.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val prepared = team.copy(
            id = id,
            ownerId = auth.userId,
            createdAt = if (team.createdAt > 0L) team.createdAt else now,
            updatedAt = now
        )
        val row = api.createTeam(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            request = prepared.toWrite(auth.userId)
        ).firstOrNull() ?: throw IllegalStateException("Supabase returned no saved team representation.")
        if (row.ownerId != auth.userId) {
            throw SecurityException("Supabase saved a team outside the active account.")
        }
        return row.toDomain()
    }

    suspend fun updateTeam(team: ModelTeam): ModelTeam {
        val auth = requireAuth()
        if (team.id.isBlank()) throw IllegalArgumentException("A team ID is required for updates.")
        if (team.ownerId.isNotBlank() && team.ownerId != auth.userId) {
            throw SecurityException("A team cannot be updated for another account.")
        }
        val updated = team.copy(ownerId = auth.userId, updatedAt = System.currentTimeMillis())
        val row = api.updateTeam(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq." + updated.id,
            ownerIdFilter = "eq." + auth.userId,
            request = updated.toPatch()
        ).firstOrNull() ?: throw NoSuchElementException("Team not found or not authorized for update.")
        if (row.ownerId != auth.userId) {
            throw SecurityException("Supabase returned a team outside the active account.")
        }
        return row.toDomain()
    }

    suspend fun deleteTeam(teamId: String) {
        val auth = requireAuth()
        val deleted = api.deleteTeam(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq." + teamId,
            ownerIdFilter = "eq." + auth.userId
        )
        if (deleted.isEmpty()) {
            throw NoSuchElementException("Team not found or not authorized for deletion.")
        }
        if (deleted.any { it.ownerId != auth.userId }) {
            throw SecurityException("Supabase returned a deleted team outside the active account.")
        }
    }

    suspend fun getTeamExecutions(teamId: String): List<TeamExecution> {
        val auth = requireAuth()
        requireOwnedTeam(teamId, auth)
        return api.listTeamExecutions(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            teamIdFilter = "eq." + teamId,
            userIdFilter = "eq." + auth.userId
        ).map { row ->
            if (row.userId != auth.userId) {
                throw SecurityException("Supabase returned an execution outside the active account.")
            }
            if (row.teamId != teamId) {
                throw SecurityException("Supabase returned an execution outside the requested team.")
            }
            row.toDomain()
        }
    }

    suspend fun getExecutionById(executionId: String): TeamExecution? {
        val auth = requireAuth()
        val row = api.getTeamExecutionById(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq." + executionId,
            userIdFilter = "eq." + auth.userId
        ).firstOrNull() ?: return null
        if (row.userId != auth.userId) {
            throw SecurityException("Supabase returned an execution outside the active account.")
        }
        requireOwnedTeam(row.teamId, auth)
        return row.toDomain()
    }

    suspend fun logExecution(execution: TeamExecution): TeamExecution {
        val auth = requireAuth()
        if (execution.userId.isNotBlank() && execution.userId != auth.userId) {
            throw SecurityException("An execution cannot be saved for another account.")
        }
        if (execution.teamId.isBlank()) {
            throw IllegalArgumentException("A team ID is required for execution persistence.")
        }
        val prepared = execution.copy(
            id = execution.id.ifBlank { UUID.randomUUID().toString() },
            userId = auth.userId
        )
        requireOwnedTeam(prepared.teamId, auth)
        val row = api.upsertTeamExecution(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            request = prepared.toWrite()
        ).firstOrNull() ?: throw IllegalStateException("Supabase returned no saved execution representation.")
        if (row.userId != auth.userId) {
            throw SecurityException("Supabase saved an execution outside the active account.")
        }
        if (row.teamId != prepared.teamId) {
            throw SecurityException("Supabase saved an execution outside the requested team.")
        }
        return row.toDomain()
    }

    private suspend fun requireOwnedTeam(teamId: String, auth: SupabaseTeamAuth) {
        if (teamId.isBlank()) {
            throw IllegalArgumentException("A team ID is required for execution access.")
        }
        val team = api.getTeamById(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq." + teamId,
            ownerIdFilter = "eq." + auth.userId
        ).firstOrNull() ?: throw NoSuchElementException(
            "Team not found or not authorized for execution access."
        )
        if (team.ownerId != auth.userId) {
            throw SecurityException("Execution access is restricted to the active team owner.")
        }
    }

    private suspend fun requireAuth(expectedUserId: String? = null): SupabaseTeamAuth {
        val currentUser = authRepository.currentUser.first()
            ?: throw IllegalStateException("Sign in with Supabase is required for team data access.")
        val session = sessionStore.read()
            ?: throw IllegalStateException("No persisted Supabase session is available.")
        validateSession(currentUser.id, session, expectedUserId)
        return SupabaseTeamAuth(
            userId = currentUser.id,
            bearerToken = "Bearer " + session.accessToken
        )
    }

    private fun validateSession(
        currentUserId: String,
        session: SupabaseStoredSession,
        expectedUserId: String?
    ) {
        if (session.user.id != currentUserId) {
            throw SecurityException("The stored Supabase session does not match the active account.")
        }
        if (expectedUserId != null && expectedUserId != currentUserId) {
            throw SecurityException("Team data can only be accessed for the active account.")
        }
        if (session.accessToken.isBlank() || session.expiresAtEpochSeconds <= nowEpochSeconds() + 60L) {
            throw IllegalStateException("The Supabase session is expired; sign in again before accessing team data.")
        }
        if (publishableKey.isBlank()) {
            throw IllegalStateException("Supabase publishable key is not configured.")
        }
    }

    private fun SupabaseModelTeamRow.toDomain(): ModelTeam {
        val payload = linkedMapOf<String, Any?>(
            "id" to id,
            "name" to name,
            "description" to description,
            "goal" to goal,
            "ownerId" to ownerId,
            "leaderMemberId" to leaderMemberId,
            "members" to members,
            "collaborationStyle" to enumNameOrDefault(collaborationStyle, CollaborationStyle.ORCHESTRATED),
            "settings" to settings,
            "governance" to governance,
            "usageLimitMonthly" to usageLimitMonthly,
            "isPublic" to isPublic,
            "isTemplate" to isTemplate,
            "createdAt" to createdAtMs,
            "updatedAt" to updatedAtMs
        )
        return fromJsonMap(teamAdapter, payload)
    }

    private fun ModelTeam.toWrite(ownerId: String) = SupabaseModelTeamWrite(
        id = id,
        ownerId = ownerId,
        name = name,
        description = description,
        goal = goal,
        leaderMemberId = leaderMemberId,
        members = members.map { toJsonMap(memberAdapter, it) },
        collaborationStyle = collaborationStyle.name,
        settings = toJsonMap(settingsAdapter, settings),
        governance = toJsonMap(governanceAdapter, governance),
        usageLimitMonthly = usageLimitMonthly,
        isPublic = isPublic,
        isTemplate = isTemplate,
        createdAtMs = createdAt,
        updatedAtMs = updatedAt
    )

    private fun ModelTeam.toPatch() = SupabaseModelTeamPatch(
        name = name,
        description = description,
        goal = goal,
        leaderMemberId = leaderMemberId,
        members = members.map { toJsonMap(memberAdapter, it) },
        collaborationStyle = collaborationStyle.name,
        settings = toJsonMap(settingsAdapter, settings),
        governance = toJsonMap(governanceAdapter, governance),
        usageLimitMonthly = usageLimitMonthly,
        isPublic = isPublic,
        isTemplate = isTemplate,
        updatedAtMs = updatedAt
    )

    private fun SupabaseTeamExecutionRow.toDomain(): TeamExecution {
        val payload = linkedMapOf<String, Any?>(
            "id" to id,
            "teamId" to teamId,
            "userId" to userId,
            "prompt" to prompt,
            "status" to enumNameOrDefault(status, ExecutionStatus.PENDING),
            "steps" to steps,
            "sharedMemory" to sharedMemory,
            "messages" to messages,
            "finalResult" to finalResult,
            "error" to error,
            "totalTokens" to totalTokens,
            "totalCost" to totalCost,
            "totalRequests" to totalRequests,
            "memberUsages" to memberUsages,
            "modelFailovers" to modelFailovers,
            "governanceEnforced" to governanceEnforced,
            "freeOnlyActive" to freeOnlyActive,
            "preExecutionEstimation" to preExecutionEstimation,
            "attachedFiles" to attachedFiles,
            "consensusPoints" to consensusPoints,
            "disagreementPoints" to disagreementPoints,
            "projectId" to projectId,
            "startedAt" to startedAtMs,
            "completedAt" to completedAtMs
        )
        return fromJsonMap(executionAdapter, payload)
    }

    private fun TeamExecution.toWrite() = SupabaseTeamExecutionWrite(
        id = id,
        teamId = teamId,
        userId = userId,
        projectId = projectId,
        prompt = prompt,
        status = status.name,
        steps = steps.map { toJsonMap(stepAdapter, it) },
        sharedMemory = toJsonMap(sharedMemoryAdapter, sharedMemory),
        messages = messages.map { toJsonMap(messageAdapter, it) },
        finalResult = finalResult,
        error = error,
        totalTokens = totalTokens,
        totalCost = totalCost,
        totalRequests = totalRequests,
        memberUsages = memberUsages.map { toJsonMap(memberUsageAdapter, it) },
        modelFailovers = modelFailovers.map { toJsonMap(failoverAdapter, it) },
        governanceEnforced = governanceEnforced,
        freeOnlyActive = freeOnlyActive,
        preExecutionEstimation = preExecutionEstimation?.let { toJsonMap(estimationAdapter, it) },
        attachedFiles = attachedFiles.map { toJsonMap(attachedFileAdapter, it) },
        consensusPoints = consensusPoints,
        disagreementPoints = disagreementPoints,
        startedAtMs = startedAt,
        completedAtMs = completedAt
    )

    private fun <T> toJsonMap(adapter: JsonAdapter<T>, value: T): Map<String, Any?> {
        val raw = adapter.toJsonValue(value) as? Map<*, *>
            ?: throw IllegalStateException("A team JSON value could not be serialized as an object.")
        return raw.entries.associate { (key, item) ->
            (key as? String ?: throw IllegalStateException("A team JSON object contains an invalid key.")) to item
        }
    }

    private fun <T> fromJsonMap(adapter: JsonAdapter<T>, value: Map<String, Any?>): T =
        adapter.fromJsonValue(value)
            ?: throw IllegalStateException("A team JSON object could not be decoded.")

    private inline fun <reified T : Enum<T>> enumNameOrDefault(value: String, default: T): String =
        enumValues<T>().firstOrNull { it.name == value }?.name ?: default.name

    private data class SupabaseTeamAuth(val userId: String, val bearerToken: String)
}
