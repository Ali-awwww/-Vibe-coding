package com.example.data.remote

import com.example.domain.model.*
import retrofit2.http.*

interface GitHubApi {
    @POST("https://github.com/login/oauth/access_token")
    @Headers("Accept: application/json")
    suspend fun getAccessToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") client_secret: String,
        @Query("code") code: String
    ): GitHubTokenResponse

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") token: String
    ): GitHubUserResponse

    @GET("user/repos")
    suspend fun getRepositories(
        @Header("Authorization") token: String,
        @Query("sort") sort: String = "updated"
    ): List<GitHubRepoResponse>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubBranchResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String?
    ): GitHubContentResponse

    @GET("search/code")
    suspend fun searchCode(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): GitHubSearchResponse

    @GET("repos/{owner}/{repo}/git/ref/{ref}")
    suspend fun getRef(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref", encoded = true) ref: String
    ): GitHubRefResponse

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GitHubCreateRefRequest
    ): GitHubRefResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: GitHubUpdateFileRequest
    ): GitHubUpdateFileResponse

    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GitHubCreatePrRequest
    ): GitHubPrResponse
}

data class GitHubTokenResponse(
    val access_token: String,
    val token_type: String,
    val scope: String
)

data class GitHubUserResponse(
    val login: String,
    val avatar_url: String
)

data class GitHubRepoResponse(
    val id: Long,
    val name: String,
    val full_name: String,
    val owner: GitHubUserResponse,
    val description: String?,
    val default_branch: String
)

data class GitHubBranchResponse(
    val name: String,
    val protected: Boolean
)

data class GitHubContentResponse(
    val path: String,
    val content: String?, // Base64 encoded
    val encoding: String?,
    val sha: String
)

data class GitHubSearchResponse(
    val total_count: Int,
    val items: List<GitHubSearchItem>
)

data class GitHubSearchItem(
    val path: String,
    val repository: GitHubRepoResponse
)

data class GitHubCreateRefRequest(
    val ref: String,
    val sha: String
)

data class GitHubRefResponse(
    val ref: String,
    val `object`: GitHubRefObject
)

data class GitHubRefObject(
    val sha: String,
    val type: String,
    val url: String
)

data class GitHubUpdateFileRequest(
    val message: String,
    val content: String, // Base64
    val sha: String,
    val branch: String
)

data class GitHubUpdateFileResponse(
    val content: GitHubContentResponse?,
    val commit: GitHubCommitResponse
)

data class GitHubCommitResponse(
    val sha: String,
    val html_url: String
)

data class GitHubCreatePrRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String
)

data class GitHubPrResponse(
    val id: Long,
    val html_url: String,
    val number: Int,
    val state: String
)
