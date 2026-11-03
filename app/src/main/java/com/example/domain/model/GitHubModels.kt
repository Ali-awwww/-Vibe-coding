package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "github_connections")
@Serializable
data class GitHubConnection(
    @PrimaryKey val userId: String = "",
    val accessToken: String = "", // Encrypted
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val username: String = "",
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "repository_mappings")
@Serializable
data class RepositoryMapping(
    @PrimaryKey val applicationId: String = "",
    val projectId: String = "",
    val repoOwner: String = "",
    val repoName: String = "",
    val defaultBranch: String = "main",
    val lastSyncSha: String? = null,
    val lastSyncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: GitHubOwner,
    val description: String?,
    val defaultBranch: String
)

@Serializable
data class GitHubOwner(
    val login: String,
    val avatarUrl: String
)

@Serializable
data class GitHubBranch(
    val name: String,
    val isProtected: Boolean
)

@Serializable
data class GitHubFileContent(
    val path: String,
    val content: String, // Base64 encoded or raw if small
    val encoding: String = "utf-8",
    val sha: String
)
