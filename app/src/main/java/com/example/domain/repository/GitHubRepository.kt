package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface GitHubRepository {
    suspend fun connectGitHub(userId: String, code: String): Result<Unit>
    suspend fun connectGitHubWithToken(userId: String, token: String): Result<Unit>
    suspend fun disconnectGitHub(userId: String): Result<Unit>
    suspend fun getGitHubConnection(userId: String): Result<GitHubConnection?>
    
    suspend fun fetchRepositories(userId: String): Result<List<GitHubRepo>>
    suspend fun fetchBranches(userId: String, owner: String, repo: String): Result<List<GitHubBranch>>
    
    suspend fun linkRepositoryToApp(mapping: RepositoryMapping): Result<Unit>
    fun getMappingsForProject(projectId: String): Flow<List<RepositoryMapping>>
    suspend fun getMappingForApp(applicationId: String): RepositoryMapping?
    
    suspend fun fetchFileContent(userId: String, owner: String, repo: String, path: String, ref: String?): Result<String>
    suspend fun searchCode(userId: String, owner: String, repo: String, query: String, ref: String?): Result<List<String>>

    suspend fun createBranch(userId: String, owner: String, repo: String, branchName: String, fromRef: String): Result<String> // returns sha
    suspend fun commitChanges(userId: String, owner: String, repo: String, branchName: String, path: String, content: String, message: String): Result<Unit>
    suspend fun createPullRequest(userId: String, owner: String, repo: String, title: String, head: String, base: String, body: String): Result<String> // returns PR URL
}
