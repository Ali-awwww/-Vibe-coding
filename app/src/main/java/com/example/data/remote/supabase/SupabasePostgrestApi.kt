package com.example.data.remote.supabase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Query

/** PostgREST endpoints for the first migrated feature slice: projects and applications. */
interface SupabasePostgrestApi {
    @GET("rest/v1/projects")
    suspend fun listProjects(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "id,name,description,owner_id,data_retention_days,pii_scrubbing_enabled,created_at_ms",
        @Query("order") order: String = "created_at_ms.desc"
    ): List<SupabaseProjectRow>

    @GET("rest/v1/projects")
    suspend fun getProjectById(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "id,name,description,owner_id,data_retention_days,pii_scrubbing_enabled,created_at_ms"
    ): List<SupabaseProjectRow>

    @POST("rest/v1/projects")
    suspend fun createProject(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseProjectWrite
    ): List<SupabaseProjectRow>

    @PATCH("rest/v1/projects")
    suspend fun updateProjectSettings(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseProjectSettingsPatch
    ): List<SupabaseProjectRow>

    @DELETE("rest/v1/projects")
    suspend fun deleteProject(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=minimal"
    )

    @GET("rest/v1/applications")
    suspend fun listApplications(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("project_id") projectIdFilter: String,
        @Query("select") select: String = "id,project_id,name,platform,package_name,owner_id,sdk_setup_status,agent_connection_status,created_at_ms",
        @Query("order") order: String = "created_at_ms.desc"
    ): List<SupabaseApplicationRow>

    @GET("rest/v1/applications")
    suspend fun listAllApplications(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "id,project_id,name,platform,package_name,owner_id,sdk_setup_status,agent_connection_status,created_at_ms",
        @Query("order") order: String = "created_at_ms.desc"
    ): List<SupabaseApplicationRow>

    @POST("rest/v1/applications")
    suspend fun createApplication(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseApplicationWrite
    ): List<SupabaseApplicationRow>

    @PATCH("rest/v1/applications")
    suspend fun updateApplicationStatuses(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseApplicationStatusPatch
    ): List<SupabaseApplicationRow>

    @DELETE("rest/v1/applications")
    suspend fun deleteApplication(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=minimal"
    )
}

@JsonClass(generateAdapter = true)
data class SupabaseProjectRow(
    val id: String,
    val name: String,
    val description: String = "",
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "data_retention_days") val dataRetentionDays: Int = 90,
    @Json(name = "pii_scrubbing_enabled") val piiScrubbingEnabled: Boolean = true,
    @Json(name = "created_at_ms") val createdAtMs: Long
)

@JsonClass(generateAdapter = true)
data class SupabaseProjectWrite(
    val id: String,
    val name: String,
    val description: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "data_retention_days") val dataRetentionDays: Int,
    @Json(name = "pii_scrubbing_enabled") val piiScrubbingEnabled: Boolean,
    @Json(name = "created_at_ms") val createdAtMs: Long
)

@JsonClass(generateAdapter = true)
data class SupabaseProjectSettingsPatch(
    @Json(name = "data_retention_days") val dataRetentionDays: Int,
    @Json(name = "pii_scrubbing_enabled") val piiScrubbingEnabled: Boolean
)

@JsonClass(generateAdapter = true)
data class SupabaseApplicationRow(
    val id: String,
    @Json(name = "project_id") val projectId: String,
    val name: String,
    val platform: String = "Android",
    @Json(name = "package_name") val packageName: String = "",
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "sdk_setup_status") val sdkSetupStatus: String = "NOT_CONFIGURED",
    @Json(name = "agent_connection_status") val agentConnectionStatus: String = "OFFLINE",
    @Json(name = "created_at_ms") val createdAtMs: Long
)

@JsonClass(generateAdapter = true)
data class SupabaseApplicationWrite(
    val id: String,
    @Json(name = "project_id") val projectId: String,
    val name: String,
    val platform: String,
    @Json(name = "package_name") val packageName: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "sdk_setup_status") val sdkSetupStatus: String,
    @Json(name = "agent_connection_status") val agentConnectionStatus: String,
    @Json(name = "created_at_ms") val createdAtMs: Long
)

@JsonClass(generateAdapter = true)
data class SupabaseApplicationStatusPatch(
    @Json(name = "sdk_setup_status") val sdkSetupStatus: String? = null,
    @Json(name = "agent_connection_status") val agentConnectionStatus: String? = null
)
