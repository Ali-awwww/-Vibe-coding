package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.*
import com.example.domain.model.Application
import com.example.domain.model.RepositoryMapping
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class GitHubRepositoryImplTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() { database.close() }

    @Test
    fun mappingRejectsApplicationThatDoesNotExist() = runBlocking {
        val result = repository().linkRepositoryToApp(RepositoryMapping("missing-app", "project-1", "owner", "repo", "main"))
        assertTrue(result.isFailure)
        assertNull(database.crashDao().getRepositoryMapping("missing-app"))
    }

    @Test
    fun mappingRejectsApplicationOwnedByAnotherProject() = runBlocking {
        database.crashDao().insertApplication(Application("app-1", "project-actual", "App", "com.example.app"))
        val result = repository().linkRepositoryToApp(RepositoryMapping("app-1", "project-requested", "owner", "repo", "main"))
        assertTrue(result.isFailure)
        assertNull(database.crashDao().getRepositoryMapping("app-1"))
    }

    @Test
    fun mappingPersistsWhenApplicationBelongsToTheSelectedProject() = runBlocking {
        database.crashDao().insertApplication(Application("app-1", "project-1", "App", "com.example.app"))
        val mapping = RepositoryMapping("app-1", "project-1", "owner", "repo", "main")
        val result = repository().linkRepositoryToApp(mapping)
        assertTrue(result.isSuccess)
        assertEquals(mapping, database.crashDao().getRepositoryMapping("app-1"))
    }

    private fun repository() = GitHubRepositoryImpl(database.crashDao(), UnusedGitHubApi)
    private object UnusedGitHubApi : GitHubApi {
        override suspend fun getUser(token: String): GitHubUserResponse = error("Not used")
        override suspend fun getRepositories(token: String, sort: String): List<GitHubRepoResponse> = error("Not used")
        override suspend fun getBranches(token: String, owner: String, repo: String): List<GitHubBranchResponse> = error("Not used")
        override suspend fun getFileContent(token: String, owner: String, repo: String, path: String, ref: String?): GitHubContentResponse = error("Not used")
        override suspend fun searchCode(token: String, query: String): GitHubSearchResponse = error("Not used")
        override suspend fun getRef(token: String, owner: String, repo: String, ref: String): GitHubRefResponse = error("Not used")
        override suspend fun createRef(token: String, owner: String, repo: String, body: GitHubCreateRefRequest): GitHubRefResponse = error("Not used")
        override suspend fun updateFile(token: String, owner: String, repo: String, path: String, body: GitHubUpdateFileRequest): GitHubUpdateFileResponse = error("Not used")
        override suspend fun createPullRequest(token: String, owner: String, repo: String, body: GitHubCreatePrRequest): GitHubPrResponse = error("Not used")
    }
}
