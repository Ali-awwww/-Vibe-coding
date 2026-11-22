package com.example.presentation.viewmodel

import com.example.R
import com.example.domain.model.*
import com.example.domain.repository.GitHubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun signedOutUserDoesNotReadStoredGitHubConnection() {
        val repository = FakeGitHubRepository()
        val viewModel = GitHubViewModel(repository, " ")
        assertEquals(R.string.github_sign_in_required, viewModel.uiState.value.errorResId)
        assertEquals(0, repository.getConnectionCalls)
    }

    @Test
    fun failedRepositoryLoadIsNotPresentedAsAnEmptySuccessfulList() {
        val repository = FakeGitHubRepository().apply {
            connectionResult = Result.success(GitHubConnection(userId = "user-1", username = "developer", accessToken = "encrypted"))
            repositoriesResult = Result.failure(IllegalStateException("private provider detail"))
        }
        val viewModel = GitHubViewModel(repository, "user-1")
        assertEquals(emptyList<GitHubRepo>(), viewModel.uiState.value.repositories)
        assertEquals(R.string.github_error_load_repositories, viewModel.uiState.value.errorResId)
        assertFalse(viewModel.uiState.value.toString().contains("private provider detail"))
    }

    @Test
    fun disconnectFailureKeepsTheStoredConnectionVisible() {
        val connection = GitHubConnection(userId = "user-1", username = "developer", accessToken = "encrypted")
        val repository = FakeGitHubRepository().apply {
            connectionResult = Result.success(connection)
            disconnectResult = Result.failure(IllegalStateException("database failure"))
        }
        val viewModel = GitHubViewModel(repository, "user-1")
        viewModel.disconnect()
        assertEquals(connection, viewModel.uiState.value.connection)
        assertEquals(R.string.github_error_disconnect, viewModel.uiState.value.errorResId)
        assertFalse(viewModel.uiState.value.toString().contains("database failure"))
    }

    @Test
    fun failedRepositoryMappingDoesNotReportCompletion() {
        val repository = FakeGitHubRepository().apply {
            mappingResult = Result.failure(IllegalStateException("mapping write failed"))
        }
        val viewModel = GitHubViewModel(repository, "user-1")
        var completed: Boolean? = null
        val repo = GitHubRepo(7L, "sample", "owner/sample", GitHubOwner("owner", ""), null, "main")
        viewModel.linkRepo("app-1", "project-1", repo, "main") { completed = it }
        assertEquals(false, completed)
        assertEquals(R.string.github_error_link_repository, viewModel.uiState.value.errorResId)
        assertFalse(viewModel.uiState.value.isLinkingRepository)
    }

    @Test
    fun blankTokenIsRejectedBeforeRepositoryCall() {
        val repository = FakeGitHubRepository()
        val viewModel = GitHubViewModel(repository, "user-1")
        viewModel.connectWithToken("   ")
        assertEquals(R.string.github_token_required, viewModel.uiState.value.errorResId)
        assertEquals(0, repository.connectTokenCalls)
    }

    private class FakeGitHubRepository : GitHubRepository {
        var connectionResult: Result<GitHubConnection?> = Result.success(null)
        var repositoriesResult: Result<List<GitHubRepo>> = Result.success(emptyList())
        var disconnectResult: Result<Unit> = Result.success(Unit)
        var mappingResult: Result<Unit> = Result.success(Unit)
        var getConnectionCalls = 0
        var connectTokenCalls = 0
        override suspend fun connectGitHub(userId: String, code: String): Result<Unit> =
            Result.failure(UnsupportedOperationException("OAuth is not configured"))
        override suspend fun connectGitHubWithToken(userId: String, token: String): Result<Unit> { connectTokenCalls++; return Result.success(Unit) }
        override suspend fun disconnectGitHub(userId: String): Result<Unit> = disconnectResult
        override suspend fun getGitHubConnection(userId: String): Result<GitHubConnection?> { getConnectionCalls++; return connectionResult }
        override suspend fun fetchRepositories(userId: String): Result<List<GitHubRepo>> = repositoriesResult
        override suspend fun fetchBranches(userId: String, owner: String, repo: String): Result<List<GitHubBranch>> = Result.success(emptyList())
        override suspend fun linkRepositoryToApp(mapping: RepositoryMapping): Result<Unit> = mappingResult
        override fun getMappingsForProject(projectId: String): Flow<List<RepositoryMapping>> = flowOf(emptyList())
        override suspend fun getMappingForApp(applicationId: String): RepositoryMapping? = null
        override suspend fun fetchFileContent(userId: String, owner: String, repo: String, path: String, ref: String?): Result<String> = Result.success("")
        override suspend fun searchCode(userId: String, owner: String, repo: String, query: String, ref: String?): Result<List<String>> = Result.success(emptyList())
        override suspend fun createBranch(userId: String, owner: String, repo: String, branchName: String, fromRef: String): Result<String> = Result.success("sha")
        override suspend fun commitChanges(userId: String, owner: String, repo: String, branchName: String, path: String, content: String, message: String): Result<Unit> = Result.success(Unit)
        override suspend fun createPullRequest(userId: String, owner: String, repo: String, title: String, head: String, base: String, body: String): Result<String> = Result.success("https://github.com/owner/repo/pull/1")
    }
}
