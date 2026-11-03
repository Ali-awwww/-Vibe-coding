package com.example.data.repository

import com.example.data.local.CrashDao
import com.example.domain.model.*
import com.example.domain.repository.LiveTestingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class LiveTestingRepositoryImpl(
    private val crashDao: CrashDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : LiveTestingRepository {

    override fun getTestSessions(projectId: String): Flow<List<TestSession>> =
        crashDao.getTestSessions(projectId)

    override fun getTestSessionById(sessionId: String): Flow<TestSession?> =
        crashDao.getTestSessionById(sessionId)

    override suspend fun startTestSession(session: TestSession): Result<Unit> = runCatching {
        val newSession = if (session.id.isEmpty()) session.copy(id = UUID.randomUUID().toString()) else session
        crashDao.insertTestSession(newSession)
        
        // Simulate Agent Activity
        simulateAgent(newSession)
    }

    override suspend fun endTestSession(sessionId: String, status: TestSessionStatus): Result<Unit> = runCatching {
        crashDao.updateTestSessionStatus(sessionId, status, System.currentTimeMillis(), null)
    }

    override fun getTestEvents(sessionId: String): Flow<List<TestEvent>> =
        crashDao.getTestEvents(sessionId)

    override suspend fun addTestEvent(event: TestEvent): Result<Unit> = runCatching {
        val newEvent = if (event.id.isEmpty()) event.copy(id = UUID.randomUUID().toString()) else event
        crashDao.insertTestEvent(newEvent)
    }

    override suspend fun updateApplicationSdkStatus(applicationId: String, status: SdkSetupStatus) {
        crashDao.updateApplicationSdkStatus(applicationId, status)
    }

    override suspend fun updateApplicationAgentStatus(applicationId: String, status: AgentConnectionStatus) {
        crashDao.updateApplicationAgentStatus(applicationId, status)
    }

    override suspend fun connectToAgent(applicationId: String): Result<Boolean> = runCatching {
        // Simulate connecting to a local agent
        delay(1500)
        crashDao.updateApplicationAgentStatus(applicationId, AgentConnectionStatus.ONLINE)
        true
    }

    override suspend fun sendCommandToAgent(sessionId: String, command: String): Result<Unit> = runCatching {
        // Log the command as an event
        val event = TestEvent(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            type = TestEventType.SDK_EVENT,
            details = "Command sent: $command"
        )
        crashDao.insertTestEvent(event)
    }

    private fun simulateAgent(session: TestSession) {
        scope.launch {
            delay(1000)
            crashDao.updateTestSessionStatus(session.id, TestSessionStatus.CONNECTED, null, null)
            crashDao.insertTestEvent(TestEvent(UUID.randomUUID().toString(), session.id, TestEventType.SDK_EVENT, "Agent Connected to Device: ${session.deviceName}"))
            
            delay(1500)
            crashDao.updateTestSessionStatus(session.id, TestSessionStatus.APP_RUNNING, null, null)
            crashDao.insertTestEvent(TestEvent(UUID.randomUUID().toString(), session.id, TestEventType.APP_START, "Application ${session.applicationId} Started"))
            
            delay(2000)
            crashDao.insertTestEvent(TestEvent(UUID.randomUUID().toString(), session.id, TestEventType.SCREEN_TRANSITION, "Navigated to MainScreen"))
            
            delay(3000)
            crashDao.insertTestEvent(TestEvent(UUID.randomUUID().toString(), session.id, TestEventType.NETWORK_REQUEST, "GET /api/v1/config - 200 OK"))
            
            delay(4000)
            crashDao.insertTestEvent(TestEvent(UUID.randomUUID().toString(), session.id, TestEventType.SDK_EVENT, "User action: Click Login"))
            
            // Randomly trigger a crash in simulation for demonstration if buildId starts with "crash"
            if (session.buildId.contains("crash", ignoreCase = true)) {
                delay(2000)
                val crashId = UUID.randomUUID().toString()
                val crash = CrashReport(
                    id = crashId,
                    applicationId = session.applicationId,
                    projectId = session.projectId,
                    exceptionType = "java.lang.NullPointerException",
                    message = "Attempt to invoke virtual method 'void java.lang.String.trim()' on a null object reference",
                    stackTrace = "at com.example.app.LoginActivity.onLoginClick(LoginActivity.kt:42)\nat android.view.View.performClick(View.java:7448)",
                    deviceModel = session.deviceName,
                    osVersion = session.os,
                    appVersion = session.version,
                    source = IntegrationType.CUSTOM
                )
                crashDao.insertCrash(crash)
                crashDao.updateTestSessionStatus(session.id, TestSessionStatus.CRASH_DETECTED, System.currentTimeMillis(), crashId)
                crashDao.insertTestEvent(TestEvent(UUID.randomUUID().toString(), session.id, TestEventType.CRASH, "FATAL: NullPointerException at LoginActivity.kt:42"))
            } else {
                delay(10000)
                crashDao.updateTestSessionStatus(session.id, TestSessionStatus.COMPLETED, System.currentTimeMillis(), null)
                crashDao.insertTestEvent(TestEvent(UUID.randomUUID().toString(), session.id, TestEventType.SDK_EVENT, "Session Completed Successfully"))
            }
        }
    }
}
