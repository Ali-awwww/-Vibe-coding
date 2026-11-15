package com.example.data.remote.supabase

import com.example.domain.model.AgentConnectionStatus
import com.example.domain.model.Application
import com.example.domain.model.Project
import com.example.domain.model.SdkSetupStatus
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Authenticated PostgREST data source for projects and applications.
 * Every request uses the current Supabase user's access token; RLS remains authoritative.
 */
class SupabaseProjectDataSource(
    private val api: SupabasePostgrestApi,
    private val authRepository: AuthRepository,
    private val sessionStore: SupabaseSessionStore,
    private val publishableKey: String,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    suspend fun getProjects(): List<Project> {
        val auth = requireAuth()
        return api.listProjects(publishableKey, auth.bearerToken).map(SupabaseProjectRow::toDomain)
    }

    suspend fun getProjectsForUser(userId: String): List<Project> {
        val auth = requireAuth()
        if (userId != auth.userId) throw SecurityException("Cannot query another user's projects.")
        return api.listProjects(publishableKey, auth.bearerToken).map(SupabaseProjectRow::toDomain)
    }

    suspend fun getProjectById(projectId: String): Project? {
        val auth = requireAuth()
        return api.getProjectById(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq.$projectId"
        ).firstOrNull()?.toDomain()
    }

    suspend fun createProject(project: Project): Project {
        val auth = requireAuth()
        val request = SupabaseProjectWrite(
            id = project.id.ifBlank { UUID.randomUUID().toString() },
            name = project.name,
            description = project.description,
            ownerId = auth.userId,
            dataRetentionDays = project.dataRetentionDays,
            piiScrubbingEnabled = project.piiScrubbingEnabled,
            createdAtMs = project.createdAt
        )
        return api.createProject(publishableKey, auth.bearerToken, request = request)
            .firstOrNull()?.toDomain()
            ?: throw IllegalStateException("Supabase accepted no project representation.")
    }

    suspend fun updateProjectSettings(projectId: String, retentionDays: Int, piiScrubbingEnabled: Boolean): Project {
        require(retentionDays in 1..3650) { "Data retention must be between 1 and 3650 days." }
        val auth = requireAuth()
        return api.updateProjectSettings(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq.$projectId",
            request = SupabaseProjectSettingsPatch(retentionDays, piiScrubbingEnabled)
        ).firstOrNull()?.toDomain()
            ?: throw NoSuchElementException("Project not found or not authorized for update.")
    }

    suspend fun deleteProject(projectId: String) {
        val auth = requireAuth()
        api.deleteProject(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq.$projectId"
        )
    }

    suspend fun getApplications(projectId: String): List<Application> {
        val auth = requireAuth()
        return api.listApplications(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            projectIdFilter = "eq.$projectId"
        ).map(SupabaseApplicationRow::toDomain)
    }

    suspend fun getAllApplications(): List<Application> {
        val auth = requireAuth()
        return api.listAllApplications(publishableKey, auth.bearerToken).map(SupabaseApplicationRow::toDomain)
    }

    suspend fun createApplication(application: Application): Application {
        val auth = requireAuth()
        val request = SupabaseApplicationWrite(
            id = application.id.ifBlank { UUID.randomUUID().toString() },
            projectId = application.projectId,
            name = application.name,
            platform = application.platform,
            packageName = application.packageName,
            ownerId = auth.userId,
            sdkSetupStatus = application.sdkSetupStatus.name,
            agentConnectionStatus = application.agentConnectionStatus.name,
            createdAtMs = application.createdAt
        )
        return api.createApplication(publishableKey, auth.bearerToken, request = request)
            .firstOrNull()?.toDomain()
            ?: throw IllegalStateException("Supabase accepted no application representation.")
    }

    suspend fun updateApplicationStatuses(
        applicationId: String,
        sdkStatus: SdkSetupStatus? = null,
        agentStatus: AgentConnectionStatus? = null
    ): Application {
        require(sdkStatus != null || agentStatus != null) { "At least one application status is required." }
        val auth = requireAuth()
        return api.updateApplicationStatuses(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq.$applicationId",
            request = SupabaseApplicationStatusPatch(
                sdkSetupStatus = sdkStatus?.name,
                agentConnectionStatus = agentStatus?.name
            )
        ).firstOrNull()?.toDomain()
            ?: throw NoSuchElementException("Application not found or not authorized for update.")
    }

    suspend fun deleteApplication(applicationId: String) {
        val auth = requireAuth()
        api.deleteApplication(
            publishableKey = publishableKey,
            bearerToken = auth.bearerToken,
            idFilter = "eq.$applicationId"
        )
    }

    private suspend fun requireAuth(): SupabaseAuthHeaders {
        val currentUser = authRepository.currentUser.first()
            ?: throw IllegalStateException("Sign in is required for Supabase data access.")
        val session = sessionStore.read()
            ?: throw IllegalStateException("No persisted Supabase session is available.")
        if (session.user.id != currentUser.id) {
            throw IllegalStateException("The stored Supabase session does not match the signed-in user.")
        }
        if (session.accessToken.isBlank() || session.expiresAtEpochSeconds <= nowEpochSeconds()) {
            throw IllegalStateException("The Supabase session is expired; sign in again before accessing data.")
        }
        if (publishableKey.isBlank()) {
            throw IllegalStateException("Supabase publishable key is not configured.")
        }
        return SupabaseAuthHeaders(
            userId = currentUser.id,
            bearerToken = "Bearer ${session.accessToken}"
        )
    }
}

private data class SupabaseAuthHeaders(val userId: String, val bearerToken: String)

private fun SupabaseProjectRow.toDomain() = Project(
    id = id,
    name = name,
    description = description,
    ownerId = ownerId,
    dataRetentionDays = dataRetentionDays,
    piiScrubbingEnabled = piiScrubbingEnabled,
    createdAt = createdAtMs
)

private fun SupabaseApplicationRow.toDomain() = Application(
    id = id,
    projectId = projectId,
    name = name,
    platform = platform,
    packageName = packageName,
    ownerId = ownerId,
    sdkSetupStatus = enumValueOrDefault(sdkSetupStatus, SdkSetupStatus.NOT_CONFIGURED),
    agentConnectionStatus = enumValueOrDefault(agentConnectionStatus, AgentConnectionStatus.OFFLINE),
    createdAt = createdAtMs
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
