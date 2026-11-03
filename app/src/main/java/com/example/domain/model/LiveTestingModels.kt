package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "test_sessions")
data class TestSession(
    @PrimaryKey val id: String = "",
    val projectId: String = "",
    val applicationId: String = "",
    val buildId: String = "",
    val version: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val os: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: TestSessionStatus = TestSessionStatus.PREPARING,
    val crashId: String? = null // Linked crash report if any
)

enum class TestSessionStatus {
    PREPARING, CONNECTED, APP_RUNNING, CRASH_DETECTED, WAITING_FOR_REPORT, COMPLETED, FAILED
}

@Entity(tableName = "test_events")
data class TestEvent(
    @PrimaryKey val id: String = "",
    val sessionId: String = "",
    val type: TestEventType = TestEventType.APP_START,
    val details: String = "", // JSON or description
    val timestamp: Long = System.currentTimeMillis()
)

enum class TestEventType {
    APP_START, SCREEN_TRANSITION, SDK_EVENT, NON_FATAL_ERROR, NETWORK_REQUEST, APP_STATE_CHANGE, ANR, CRASH
}

enum class SdkSetupStatus {
    NOT_CONFIGURED, PENDING, CONFIGURED, ERROR
}

enum class AgentConnectionStatus {
    OFFLINE, ONLINE, BUSY
}

class LiveTestingConverters {
    @TypeConverter
    fun fromTestSessionStatus(value: TestSessionStatus): String = value.name

    @TypeConverter
    fun toTestSessionStatus(value: String): TestSessionStatus = try {
        TestSessionStatus.valueOf(value)
    } catch (e: Exception) {
        TestSessionStatus.FAILED
    }

    @TypeConverter
    fun fromTestEventType(value: TestEventType): String = value.name

    @TypeConverter
    fun toTestEventType(value: String): TestEventType = try {
        TestEventType.valueOf(value)
    } catch (e: Exception) {
        TestEventType.SDK_EVENT
    }

    @TypeConverter
    fun fromSdkSetupStatus(value: SdkSetupStatus): String = value.name

    @TypeConverter
    fun toSdkSetupStatus(value: String): SdkSetupStatus = try {
        SdkSetupStatus.valueOf(value)
    } catch (e: Exception) {
        SdkSetupStatus.NOT_CONFIGURED
    }

    @TypeConverter
    fun fromAgentConnectionStatus(value: AgentConnectionStatus): String = value.name

    @TypeConverter
    fun toAgentConnectionStatus(value: String): AgentConnectionStatus = try {
        AgentConnectionStatus.valueOf(value)
    } catch (e: Exception) {
        AgentConnectionStatus.OFFLINE
    }
}
