package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "team_usage_records")
data class TeamUsageRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val executionId: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val roleName: String = "",
    val modelId: String = "",
    val modelName: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val specificApiKeyId: String? = null,
    val taskId: String = "",
    val taskDescription: String = "",
    val projectId: String? = null,
    val tokensInput: Int = 0,
    val tokensOutput: Int = 0,
    val totalTokens: Int = 0,
    val costUsd: Double = 0.0,
    val isFree: Boolean = true,
    val isCostConfirmed: Boolean = true,
    val status: String = "SUCCESS", // "SUCCESS", "RATE_LIMITED", "BLOCKED_PAID", "FAILED"
    val errorMessage: String? = null,
    val quotaRemaining: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
