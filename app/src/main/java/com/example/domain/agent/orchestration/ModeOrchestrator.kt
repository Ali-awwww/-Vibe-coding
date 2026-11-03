package com.example.domain.agent.orchestration

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ModeOrchestrator {
    suspend fun execute(
        userId: String,
        team: ModelTeam,
        initialExecution: TeamExecution,
        onUpdate: suspend (TeamExecution) -> Unit
    )
}
