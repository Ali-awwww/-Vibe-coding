package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface SentryApi {
    @GET("api/0/projects/{org_slug}/{project_slug}/issues/")
    suspend fun getIssues(
        @Header("Authorization") authToken: String,
        @Path("org_slug") orgSlug: String,
        @Path("project_slug") projectSlug: String,
        @Query("query") query: String? = null
    ): List<SentryIssue>
    
    @GET("api/0/issues/{issue_id}/events/")
    suspend fun getIssueEvents(
        @Header("Authorization") authToken: String,
        @Path("issue_id") issueId: String
    ): List<SentryEvent>
}

data class SentryIssue(
    val id: String,
    val title: String,
    val culprit: String,
    val level: String,
    val status: String,
    val firstSeen: String,
    val lastSeen: String,
    val count: String,
    val userCount: Int,
    val permalink: String?,
    val metadata: SentryMetadata?
)

data class SentryMetadata(
    val type: String?,
    val value: String?
)

data class SentryEvent(
    val id: String,
    val eventID: String,
    val title: String,
    val message: String,
    val dateCreated: String,
    val release: String?,
    val contexts: SentryContexts?,
    val entries: List<SentryEntry>?
)

data class SentryContexts(
    val os: SentryOs?,
    val device: SentryDevice?
)

data class SentryOs(val name: String?, val version: String?)
data class SentryDevice(val model: String?, val arch: String?)

data class SentryEntry(
    val type: String,
    val data: SentryEntryData?
)

data class SentryEntryData(
    val values: List<SentryStacktraceValue>?
)

data class SentryStacktraceValue(
    val stacktrace: SentryStacktrace?
)

data class SentryStacktrace(
    val frames: List<SentryFrame>?
)

data class SentryFrame(
    val function: String?,
    val filename: String?,
    val lineNo: Int?,
    val colNo: Int?,
    val context: List<List<Any>>?
)
