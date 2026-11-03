package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface LiveTestingRepository {
    fun getTestSessions(projectId: String): Flow<List<TestSession>>
    fun getTestSessionById(sessionId: String): Flow<TestSession?>
    suspend fun startTestSession(session: TestSession): Result<Unit>
    suspend fun endTestSession(sessionId: String, status: TestSessionStatus): Result<Unit>
    
    fun getTestEvents(sessionId: String): Flow<List<TestEvent>>
    suspend fun addTestEvent(event: TestEvent): Result<Unit>
    
    suspend fun updateApplicationSdkStatus(applicationId: String, status: SdkSetupStatus)
    suspend fun updateApplicationAgentStatus(applicationId: String, status: AgentConnectionStatus)
    
    suspend fun connectToAgent(applicationId: String): Result<Boolean>
    suspend fun sendCommandToAgent(sessionId: String, command: String): Result<Unit>
}
