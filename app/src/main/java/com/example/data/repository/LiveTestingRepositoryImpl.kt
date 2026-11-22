package com.example.data.repository

import com.example.data.local.CrashDao
import com.example.domain.model.*
import com.example.domain.repository.LiveTestingRepository
import kotlinx.coroutines.flow.Flow

class LiveTestingRepositoryImpl(
    private val crashDao: CrashDao
) : LiveTestingRepository {

    // No authenticated Android Agent transport is implemented in this build.
    override val isAgentTransportAvailable: Boolean = false

    override fun getTestSessions(projectId: String): Flow<List<TestSession>> =
        crashDao.getTestSessions(projectId)

    override fun getTestSessionById(sessionId: String): Flow<TestSession?> =
        crashDao.getTestSessionById(sessionId)

    override suspend fun startTestSession(session: TestSession): Result<Unit> =
        Result.failure(UnsupportedOperationException(
            "الاختبار المباشر غير متاح: لم يتم تنفيذ اتصال Agent حقيقي بعد. لم يتم تشغيل محاكاة أو إنشاء نتائج اختبار وهمية."
        ))

    override suspend fun endTestSession(sessionId: String, status: TestSessionStatus): Result<Unit> =
        Result.failure(UnsupportedOperationException(
            "Live sessions cannot be ended without a verified Android Agent transport."
        ))

    override fun getTestEvents(sessionId: String): Flow<List<TestEvent>> =
        crashDao.getTestEvents(sessionId)

    override suspend fun addTestEvent(event: TestEvent): Result<Unit> =
        Result.failure(UnsupportedOperationException(
            "Live test events cannot be persisted without a verified Android Agent transport."
        ))

    override suspend fun updateApplicationSdkStatus(applicationId: String, status: SdkSetupStatus) {
        crashDao.updateApplicationSdkStatus(applicationId, status)
    }

    override suspend fun updateApplicationAgentStatus(applicationId: String, status: AgentConnectionStatus) {
        if (!isAgentTransportAvailable && status != AgentConnectionStatus.OFFLINE) {
            throw UnsupportedOperationException(
                "Agent status cannot be marked online or busy without a verified transport."
            )
        }
        crashDao.updateApplicationAgentStatus(applicationId, status)
    }

    override suspend fun connectToAgent(applicationId: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException(
            "تعذر ربط Agent: لا يوجد بروتوكول اتصال فعلي مهيأ في هذا الإصدار."
        ))

    override suspend fun sendCommandToAgent(sessionId: String, command: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(
            "إرسال الأوامر غير متاح قبل تنفيذ اتصال Agent حقيقي."
        ))

}
