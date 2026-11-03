package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AIModelTeamRepository {
    fun getTeams(userId: String): Flow<List<ModelTeam>>
    suspend fun getTeamById(teamId: String): ModelTeam?
    suspend fun createTeam(team: ModelTeam): Result<String>
    suspend fun updateTeam(team: ModelTeam): Result<Unit>
    suspend fun deleteTeam(teamId: String): Result<Unit>
    
    fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>>
    suspend fun getExecutionById(executionId: String): TeamExecution?
    suspend fun logExecution(execution: TeamExecution): Result<Unit>
    
    suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit>
    suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit>
    suspend fun updateMemberStatus(teamId: String, memberId: String, status: MemberStatus, currentTask: String? = null, lastResult: String? = null): Result<Unit>
    
    fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>>
    fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>>
    suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit>
}
