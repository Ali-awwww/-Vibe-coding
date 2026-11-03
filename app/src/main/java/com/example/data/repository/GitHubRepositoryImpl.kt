package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.CrashDao
import com.example.data.remote.GitHubApi
import com.example.domain.model.*
import com.example.domain.repository.GitHubRepository
import com.example.util.SecurityUtils
import kotlinx.coroutines.flow.Flow
import android.util.Base64

class GitHubRepositoryImpl(
    private val crashDao: CrashDao,
    private val githubApi: GitHubApi
) : GitHubRepository {

    private val clientId = BuildConfig.GITHUB_CLIENT_ID
    private val clientSecret = BuildConfig.GITHUB_CLIENT_SECRET

    override suspend fun connectGitHub(userId: String, code: String): Result<Unit> = runCatching {
        val tokenResponse = githubApi.getAccessToken(clientId, clientSecret, code)
        val authHeader = "Bearer ${tokenResponse.access_token}"
        val userResponse = githubApi.getUser(authHeader)

        val encryptedToken = SecurityUtils.encryptSecret(tokenResponse.access_token)
        
        val connection = GitHubConnection(
            userId = userId,
            accessToken = encryptedToken,
            username = userResponse.login,
            avatarUrl = userResponse.avatar_url
        )
        crashDao.insertGitHubConnection(connection)
    }

    override suspend fun connectGitHubWithToken(userId: String, token: String): Result<Unit> = runCatching {
        val cleanToken = token.trim()
        val authHeader = if (cleanToken.startsWith("Bearer ") || cleanToken.startsWith("token ")) cleanToken else "Bearer $cleanToken"
        val userResponse = githubApi.getUser(authHeader)

        val rawToken = if (cleanToken.startsWith("Bearer ") || cleanToken.startsWith("token ")) cleanToken.substringAfter(" ") else cleanToken
        val encryptedToken = SecurityUtils.encryptSecret(rawToken)
        
        val connection = GitHubConnection(
            userId = userId,
            accessToken = encryptedToken,
            username = userResponse.login,
            avatarUrl = userResponse.avatar_url
        )
        crashDao.insertGitHubConnection(connection)
    }

    override suspend fun disconnectGitHub(userId: String): Result<Unit> = runCatching {
        crashDao.deleteGitHubConnection(userId)
    }

    override suspend fun getGitHubConnection(userId: String): Result<GitHubConnection?> = runCatching {
        crashDao.getGitHubConnection(userId)
    }

    override suspend fun fetchRepositories(userId: String): Result<List<GitHubRepo>> = runCatching {
        val authHeader = getAuthHeader(userId)
        val responses = githubApi.getRepositories(authHeader)
        responses.map { 
            GitHubRepo(
                id = it.id,
                name = it.name,
                fullName = it.full_name,
                owner = GitHubOwner(it.owner.login, it.owner.avatar_url),
                description = it.description,
                defaultBranch = it.default_branch
            )
        }
    }

    override suspend fun fetchBranches(userId: String, owner: String, repo: String): Result<List<GitHubBranch>> = runCatching {
        val authHeader = getAuthHeader(userId)
        val responses = githubApi.getBranches(authHeader, owner, repo)
        responses.map { GitHubBranch(it.name, it.protected) }
    }

    override suspend fun linkRepositoryToApp(mapping: RepositoryMapping): Result<Unit> = runCatching {
        crashDao.insertRepositoryMapping(mapping)
    }

    override fun getMappingsForProject(projectId: String): Flow<List<RepositoryMapping>> =
        crashDao.getRepositoryMappingsForProject(projectId)

    override suspend fun getMappingForApp(applicationId: String): RepositoryMapping? =
        crashDao.getRepositoryMapping(applicationId)

    override suspend fun fetchFileContent(
        userId: String,
        owner: String,
        repo: String,
        path: String,
        ref: String?
    ): Result<String> = runCatching {
        val authHeader = getAuthHeader(userId)
        val response = githubApi.getFileContent(authHeader, owner, repo, path, ref)
        val decodedBytes = Base64.decode(response.content?.replace("\n", ""), Base64.DEFAULT)
        String(decodedBytes)
    }

    override suspend fun searchCode(
        userId: String,
        owner: String,
        repo: String,
        query: String,
        ref: String?
    ): Result<List<String>> = runCatching {
        val authHeader = getAuthHeader(userId)
        val q = "$query repo:$owner/$repo"
        val response = githubApi.searchCode(authHeader, q)
        response.items.map { it.path }
    }

    override suspend fun createBranch(
        userId: String,
        owner: String,
        repo: String,
        branchName: String,
        fromRef: String
    ): Result<String> = runCatching {
        val authHeader = getAuthHeader(userId)
        val fromRefResponse = githubApi.getRef(authHeader, owner, repo, "heads/$fromRef")
        val sha = fromRefResponse.`object`.sha
        
        githubApi.createRef(
            authHeader,
            owner,
            repo,
            com.example.data.remote.GitHubCreateRefRequest(
                ref = "refs/heads/$branchName",
                sha = sha
            )
        )
        sha
    }

    override suspend fun commitChanges(
        userId: String,
        owner: String,
        repo: String,
        branchName: String,
        path: String,
        content: String,
        message: String
    ): Result<Unit> = runCatching {
        val authHeader = getAuthHeader(userId)
        val currentFile = githubApi.getFileContent(authHeader, owner, repo, path, branchName)
        val base64Content = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        
        githubApi.updateFile(
            authHeader,
            owner,
            repo,
            path,
            com.example.data.remote.GitHubUpdateFileRequest(
                message = message,
                content = base64Content,
                sha = currentFile.sha,
                branch = branchName
            )
        )
    }

    override suspend fun createPullRequest(
        userId: String,
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String
    ): Result<String> = runCatching {
        val authHeader = getAuthHeader(userId)
        val response = githubApi.createPullRequest(
            authHeader,
            owner,
            repo,
            com.example.data.remote.GitHubCreatePrRequest(
                title = title,
                head = head,
                base = base,
                body = body
            )
        )
        response.html_url
    }

    private suspend fun getAuthHeader(userId: String): String {
        val connection = crashDao.getGitHubConnection(userId)
            ?: throw IllegalStateException("GitHub account not linked.")
        val token = SecurityUtils.decryptSecret(connection.accessToken)
        return "Bearer $token"
    }
}
