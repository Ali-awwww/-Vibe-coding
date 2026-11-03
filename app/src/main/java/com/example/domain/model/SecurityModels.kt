package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class Role {
    OWNER,      // Full control: manage project, team members, integrations, purge/delete data
    ADMIN,      // Manage team members, integrations, resolve crashes, purge data
    DEVELOPER,  // View crashes, AI diagnosis, resolve crashes, view integrations
    VIEWER      // Read-only access to dashboards, crashes, and analytics
}

@Entity(tableName = "users")
data class UserAccount(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val email: String = "",
    val passwordHash: String = "",
    val salt: String = "",
    val displayName: String = "",
    val organization: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "project_members", primaryKeys = ["projectId", "userId"])
data class ProjectMember(
    val projectId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val displayName: String = "",
    val role: Role = Role.DEVELOPER,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val action: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object AccessControl {
    fun canManageProject(role: Role?): Boolean = role == Role.OWNER || role == Role.ADMIN
    fun canManageMembers(role: Role?): Boolean = role == Role.OWNER || role == Role.ADMIN
    fun canManageIntegrations(role: Role?): Boolean = role == Role.OWNER || role == Role.ADMIN
    fun canResolveCrashes(role: Role?): Boolean = role == Role.OWNER || role == Role.ADMIN || role == Role.DEVELOPER
    fun canPurgeData(role: Role?): Boolean = role == Role.OWNER || role == Role.ADMIN
    fun canDeleteProject(role: Role?): Boolean = role == Role.OWNER
}
