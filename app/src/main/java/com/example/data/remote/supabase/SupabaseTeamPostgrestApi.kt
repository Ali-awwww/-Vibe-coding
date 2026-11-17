package com.example.data.remote.supabase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/** Authenticated PostgREST contract for model teams and persisted team executions. */
interface SupabaseTeamPostgrestApi {
    @GET("rest/v1/model_teams")
    suspend fun listTeams(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("owner_id") ownerIdFilter: String,
        @Query("select") select: String = "id,owner_id,name,description,goal,leader_member_id,members,collaboration_style,settings,governance,usage_limit_monthly,is_public,is_template,created_at_ms,updated_at_ms",
        @Query("order") order: String = "created_at_ms.desc"
    ): List<SupabaseModelTeamRow>

    @GET("rest/v1/model_teams")
    suspend fun getTeamById(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Query("owner_id") ownerIdFilter: String,
        @Query("select") select: String = "id,owner_id,name,description,goal,leader_member_id,members,collaboration_style,settings,governance,usage_limit_monthly,is_public,is_template,created_at_ms,updated_at_ms"
    ): List<SupabaseModelTeamRow>

    @POST("rest/v1/model_teams")
    suspend fun createTeam(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseModelTeamWrite
    ): List<SupabaseModelTeamRow>

    @PATCH("rest/v1/model_teams")
    suspend fun updateTeam(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Query("owner_id") ownerIdFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseModelTeamPatch
    ): List<SupabaseModelTeamRow>

    @DELETE("rest/v1/model_teams")
    suspend fun deleteTeam(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Query("owner_id") ownerIdFilter: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<SupabaseModelTeamRow>

    @GET("rest/v1/team_executions")
    suspend fun listTeamExecutions(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("team_id") teamIdFilter: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "id,team_id,user_id,project_id,prompt,status,steps,shared_memory,messages,final_result,error,total_tokens,total_cost,total_requests,member_usages,model_failovers,governance_enforced,free_only_active,pre_execution_estimation,attached_files,consensus_points,disagreement_points,started_at_ms,completed_at_ms",
        @Query("order") order: String = "started_at_ms.desc"
    ): List<SupabaseTeamExecutionRow>

    @GET("rest/v1/team_executions")
    suspend fun getTeamExecutionById(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "id,team_id,user_id,project_id,prompt,status,steps,shared_memory,messages,final_result,error,total_tokens,total_cost,total_requests,member_usages,model_failovers,governance_enforced,free_only_active,pre_execution_estimation,attached_files,consensus_points,disagreement_points,started_at_ms,completed_at_ms"
    ): List<SupabaseTeamExecutionRow>

    @POST("rest/v1/team_executions")
    suspend fun upsertTeamExecution(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("on_conflict") onConflict: String = "id",
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body request: SupabaseTeamExecutionWrite
    ): List<SupabaseTeamExecutionRow>
}

@JsonClass(generateAdapter = true)
data class SupabaseModelTeamRow(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    val description: String = "",
    val goal: String = "",
    @Json(name = "leader_member_id") val leaderMemberId: String? = null,
    val members: List<Map<String, Any?>> = emptyList(),
    @Json(name = "collaboration_style") val collaborationStyle: String = "ORCHESTRATED",
    val settings: Map<String, Any?> = emptyMap(),
    val governance: Map<String, Any?> = emptyMap(),
    @Json(name = "usage_limit_monthly") val usageLimitMonthly: Double = 10.0,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "is_template") val isTemplate: Boolean = false,
    @Json(name = "created_at_ms") val createdAtMs: Long = 0L,
    @Json(name = "updated_at_ms") val updatedAtMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class SupabaseModelTeamWrite(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    val description: String,
    val goal: String,
    @Json(name = "leader_member_id") val leaderMemberId: String?,
    val members: List<Map<String, Any?>>,
    @Json(name = "collaboration_style") val collaborationStyle: String,
    val settings: Map<String, Any?>,
    val governance: Map<String, Any?>,
    @Json(name = "usage_limit_monthly") val usageLimitMonthly: Double,
    @Json(name = "is_public") val isPublic: Boolean,
    @Json(name = "is_template") val isTemplate: Boolean,
    @Json(name = "created_at_ms") val createdAtMs: Long,
    @Json(name = "updated_at_ms") val updatedAtMs: Long
)

@JsonClass(generateAdapter = true)
data class SupabaseModelTeamPatch(
    val name: String,
    val description: String,
    val goal: String,
    @Json(name = "leader_member_id") val leaderMemberId: String?,
    val members: List<Map<String, Any?>>,
    @Json(name = "collaboration_style") val collaborationStyle: String,
    val settings: Map<String, Any?>,
    val governance: Map<String, Any?>,
    @Json(name = "usage_limit_monthly") val usageLimitMonthly: Double,
    @Json(name = "is_public") val isPublic: Boolean,
    @Json(name = "is_template") val isTemplate: Boolean,
    @Json(name = "updated_at_ms") val updatedAtMs: Long
)

@JsonClass(generateAdapter = true)
data class SupabaseTeamExecutionRow(
    val id: String,
    @Json(name = "team_id") val teamId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "project_id") val projectId: String? = null,
    val prompt: String = "",
    val status: String = "PENDING",
    val steps: List<Map<String, Any?>> = emptyList(),
    @Json(name = "shared_memory") val sharedMemory: Map<String, Any?> = emptyMap(),
    val messages: List<Map<String, Any?>> = emptyList(),
    @Json(name = "final_result") val finalResult: String? = null,
    val error: String? = null,
    @Json(name = "total_tokens") val totalTokens: Int = 0,
    @Json(name = "total_cost") val totalCost: Double = 0.0,
    @Json(name = "total_requests") val totalRequests: Int = 0,
    @Json(name = "member_usages") val memberUsages: List<Map<String, Any?>> = emptyList(),
    @Json(name = "model_failovers") val modelFailovers: List<Map<String, Any?>> = emptyList(),
    @Json(name = "governance_enforced") val governanceEnforced: Boolean = true,
    @Json(name = "free_only_active") val freeOnlyActive: Boolean = true,
    @Json(name = "pre_execution_estimation") val preExecutionEstimation: Map<String, Any?>? = null,
    @Json(name = "attached_files") val attachedFiles: List<Map<String, Any?>> = emptyList(),
    @Json(name = "consensus_points") val consensusPoints: List<String> = emptyList(),
    @Json(name = "disagreement_points") val disagreementPoints: List<String> = emptyList(),
    @Json(name = "started_at_ms") val startedAtMs: Long = 0L,
    @Json(name = "completed_at_ms") val completedAtMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseTeamExecutionWrite(
    val id: String,
    @Json(name = "team_id") val teamId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "project_id") val projectId: String?,
    val prompt: String,
    val status: String,
    val steps: List<Map<String, Any?>>,
    @Json(name = "shared_memory") val sharedMemory: Map<String, Any?>,
    val messages: List<Map<String, Any?>>,
    @Json(name = "final_result") val finalResult: String?,
    val error: String?,
    @Json(name = "total_tokens") val totalTokens: Int,
    @Json(name = "total_cost") val totalCost: Double,
    @Json(name = "total_requests") val totalRequests: Int,
    @Json(name = "member_usages") val memberUsages: List<Map<String, Any?>>,
    @Json(name = "model_failovers") val modelFailovers: List<Map<String, Any?>>,
    @Json(name = "governance_enforced") val governanceEnforced: Boolean,
    @Json(name = "free_only_active") val freeOnlyActive: Boolean,
    @Json(name = "pre_execution_estimation") val preExecutionEstimation: Map<String, Any?>?,
    @Json(name = "attached_files") val attachedFiles: List<Map<String, Any?>>,
    @Json(name = "consensus_points") val consensusPoints: List<String>,
    @Json(name = "disagreement_points") val disagreementPoints: List<String>,
    @Json(name = "started_at_ms") val startedAtMs: Long,
    @Json(name = "completed_at_ms") val completedAtMs: Long?
)
