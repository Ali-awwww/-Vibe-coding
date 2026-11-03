package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface BugsnagApi {
    @GET("projects/{project_id}/errors")
    suspend fun getErrors(
        @Header("Authorization") authToken: String,
        @Path("project_id") projectId: String,
        @Query("per_page") perPage: Int = 30
    ): List<BugsnagError>
    
    @GET("errors/{error_id}/events")
    suspend fun getErrorEvents(
        @Header("Authorization") authToken: String,
        @Path("error_id") errorId: String,
        @Query("per_page") perPage: Int = 1
    ): List<BugsnagEvent>
}

data class BugsnagError(
    val id: String,
    val error_class: String,
    val message: String,
    val context: String?,
    val last_message: String?,
    val first_seen: String,
    val last_seen: String,
    val events: Int,
    val users: Int,
    val status: String,
    val severity: String
)

data class BugsnagEvent(
    val id: String,
    val received_at: String,
    val exceptions: List<BugsnagException>?,
    val app: BugsnagApp?,
    val device: BugsnagDevice?
)

data class BugsnagApp(val version: String?)
data class BugsnagDevice(val model: String?, val osVersion: String?)

data class BugsnagException(
    val errorClass: String,
    val message: String?,
    val stacktrace: List<BugsnagStackframe>?
)

data class BugsnagStackframe(
    val file: String?,
    val lineNumber: Int?,
    val method: String?,
    val inProject: Boolean?
)
