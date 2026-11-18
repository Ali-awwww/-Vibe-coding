package com.example.data.agent

import com.example.domain.agent.TeamOrchestrator
import com.example.domain.agent.VibeAgentOrchestrator
import com.example.domain.agent.orchestration.*
import com.example.data.agent.orchestration.*
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.VibeStudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class TeamOrchestratorImpl(
    private val aiModelRepository: AIModelRepository,
    private val teamRepository: AIModelTeamRepository,
    private val vibeRepository: VibeStudioRepository,
    private val orchestratorProvider: () -> VibeAgentOrchestrator,
    private val taskPlanner: TaskPlanner,
    private val sharedContextStore: SharedContextStore,
    private val resultEvaluator: ResultEvaluator,
    private val responseComposer: ResponseComposer,
    private val crashRepository: com.example.domain.repository.CrashRepository? = null,
    private val crashDao: com.example.data.local.CrashDao? = null,
    private val governanceEngine: com.example.domain.governance.TeamGovernanceEngine? = null
) : TeamOrchestrator {

    private val gov: com.example.domain.governance.TeamGovernanceEngine by lazy {
        governanceEngine ?: com.example.data.governance.TeamGovernanceEngineImpl(teamRepository)
    }

    override suspend fun estimateTaskResources(
        userId: String,
        team: ModelTeam,
        prompt: String
    ): PreExecutionEstimation {
        val models = try {
            aiModelRepository.getAllAvailableModels(userId, false).first()
        } catch (_: Exception) {
            emptyList()
        }
        return gov.estimateResources(team, prompt, models)
    }

    enum class ControlState { RUNNING, PAUSED, STOPPED }

    private class ExecutionControl {
        val state = MutableStateFlow(ControlState.RUNNING)
        val mutex = Mutex()
        var latestExecution: TeamExecution? = null
    }

    private class ExecutionStoppedException : IllegalStateException("Team execution was stopped.")

    private val executionControls = java.util.concurrent.ConcurrentHashMap<String, ExecutionControl>()
    private val activeExecutionJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val executionRegistryLock = Any()

    private fun isTerminal(status: ExecutionStatus): Boolean =
        status == ExecutionStatus.COMPLETED ||
            status == ExecutionStatus.FAILED ||
            status == ExecutionStatus.CANCELLED

    override fun executeTask(
        userId: String,
        team: ModelTeam,
        prompt: String
    ): Flow<TeamExecution> = executeTaskWithContext(userId, team, prompt, emptyList(), null)

    override fun executeTaskWithContext(
        userId: String,
        team: ModelTeam,
        prompt: String,
        attachedFiles: List<AttachedFileContext>,
        projectId: String?
    ): Flow<TeamExecution> = channelFlow {
        val executionId = UUID.randomUUID().toString()
        val control = ExecutionControl()
        var lastExecution: TeamExecution? = null
        val activeJob = currentCoroutineContext()[Job]
        synchronized(executionRegistryLock) {
            executionControls[executionId] = control
            activeJob?.let { activeExecutionJobs[executionId] = it }
        }

        try {
            val availableModels = try {
                aiModelRepository.getAllAvailableModels(userId, false).first()
            } catch (_: Exception) {
                emptyList()
            }
            val estimation = gov.estimateResources(team, prompt, availableModels)

            val filesContext = if (attachedFiles.isNotEmpty()) {
                "\n\n--- Attached Files & Code Context ---\n" + attachedFiles.joinToString("\n\n") { file ->
                    "[File: ${file.name} | Path: ${file.path}]\n${file.content}"
                }
            } else ""

            val initialUserMessage = AgentMessage(
                id = UUID.randomUUID().toString(),
                fromMemberId = "USER",
                toMemberId = team.leaderMemberId ?: "TEAM",
                content = prompt,
                type = MessageType.USER_PROMPT,
                timestamp = System.currentTimeMillis()
            )

            var currentExecution = TeamExecution(
                id = executionId,
                teamId = team.id,
                userId = userId,
                prompt = gov.maskSecrets(prompt),
                status = ExecutionStatus.EXECUTING,
                governanceEnforced = true,
                freeOnlyActive = team.governance.freeOnly,
                preExecutionEstimation = estimation,
                attachedFiles = attachedFiles,
                projectId = projectId,
                messages = listOf(initialUserMessage),
                startedAt = System.currentTimeMillis()
            )

                suspend fun publish(updatedExecution: TeamExecution) {
                    val published = control.mutex.withLock {
                        val snapshot = when (control.state.value) {
                            ControlState.STOPPED -> updatedExecution.copy(
                                status = ExecutionStatus.CANCELLED,
                                completedAt = System.currentTimeMillis()
                            )
                            ControlState.PAUSED -> if (isTerminal(updatedExecution.status)) {
                                updatedExecution
                            } else {
                                updatedExecution.copy(status = ExecutionStatus.PAUSED)
                            }
                            ControlState.RUNNING -> updatedExecution
                        }
                        teamRepository.logExecution(snapshot).getOrThrow()
                        control.latestExecution = snapshot
                        currentExecution = snapshot
                        lastExecution = snapshot
                        send(snapshot)
                        snapshot
                    }
        
                    when (control.state.value) {
                        ControlState.STOPPED -> throw ExecutionStoppedException()
                        ControlState.PAUSED -> if (!isTerminal(published.status)) {
                            val nextState = control.state.first { it != ControlState.PAUSED }
                            if (nextState == ControlState.STOPPED) throw ExecutionStoppedException()
                        }
                        ControlState.RUNNING -> Unit
                    }
                }
        
                publish(currentExecution)

            // Validate execution start against Free Only policy and cost limits
            val startValidation = gov.validateExecutionStart(team, currentExecution, availableModels)
            if (startValidation.isFailure) {
                val errMessage = startValidation.exceptionOrNull()?.message ?: "Governance policy check failed"
                currentExecution = currentExecution.copy(
                    status = ExecutionStatus.FAILED,
                    error = errMessage,
                    completedAt = System.currentTimeMillis()
                )
                publish(currentExecution)
                return@channelFlow
            }

            try {
                val orchestrator = getOrchestratorForStyle(team.collaborationStyle)
                orchestrator.execute(userId, team, currentExecution) { updatedExec ->
                    publish(updatedExec)
                }

        } catch (stopped: ExecutionStoppedException) {
            throw stopped
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            currentExecution = currentExecution.copy(
                status = ExecutionStatus.FAILED,
                error = e.message,
                completedAt = System.currentTimeMillis()
            )
            publish(currentExecution)
        }
        } catch (stopped: ExecutionStoppedException) {
            val cancelledExecution = control.mutex.withLock {
                val latest = control.latestExecution ?: lastExecution
                if (latest == null) {
                    null
                } else if (latest.status == ExecutionStatus.CANCELLED) {
                    latest
                } else {
                    latest.copy(
                        status = ExecutionStatus.CANCELLED,
                        completedAt = System.currentTimeMillis()
                    ).also { cancelled ->
                        teamRepository.logExecution(cancelled).getOrThrow()
                        control.latestExecution = cancelled
                        lastExecution = cancelled
                    }
                }
            }
            cancelledExecution?.let { send(it) }
        } finally {
            activeJob?.let { activeExecutionJobs.remove(executionId, it) }
            executionControls.remove(executionId, control)
        }
    }

    override suspend fun pauseExecution(executionId: String) {
        val (control, activeJob) = synchronized(executionRegistryLock) {
            executionControls[executionId] to activeExecutionJobs[executionId]
        }
        if (control == null || activeJob?.isActive != true) {
            val existing = teamRepository.getExecutionById(executionId) ?: return
            if (existing.status == ExecutionStatus.EXECUTING) {
                throw IllegalStateException("Cannot pause an execution without its active worker.")
            }
            return
        }

        control.state.value = ControlState.PAUSED
        try {
            control.mutex.withLock {
                val latest = control.latestExecution ?: teamRepository.getExecutionById(executionId)
                if (latest == null || isTerminal(latest.status)) {
                    control.state.value = ControlState.RUNNING
                    return@withLock
                }
                if (latest.status != ExecutionStatus.PAUSED) {
                    val paused = latest.copy(status = ExecutionStatus.PAUSED)
                    teamRepository.logExecution(paused).getOrThrow()
                    control.latestExecution = paused
                }
            }
        } catch (error: Exception) {
            control.state.value = ControlState.RUNNING
            throw error
        }
    }

    override suspend fun stopExecution(executionId: String) {
        val control = synchronized(executionRegistryLock) { executionControls[executionId] }
        if (control == null) {
            val existing = teamRepository.getExecutionById(executionId) ?: return
            if (!isTerminal(existing.status)) {
                teamRepository.logExecution(
                    existing.copy(
                        status = ExecutionStatus.CANCELLED,
                        completedAt = System.currentTimeMillis()
                    )
                ).getOrThrow()
            }
            return
        }

        control.state.value = ControlState.STOPPED
        try {
            control.mutex.withLock {
                val latest = control.latestExecution ?: teamRepository.getExecutionById(executionId)
                if (latest == null || isTerminal(latest.status)) {
                    control.state.value = ControlState.RUNNING
                    return@withLock
                }
                val cancelled = latest.copy(
                    status = ExecutionStatus.CANCELLED,
                    completedAt = System.currentTimeMillis()
                )
                teamRepository.logExecution(cancelled).getOrThrow()
                control.latestExecution = cancelled
            }
        } catch (error: Exception) {
            control.state.value = ControlState.RUNNING
            throw error
        }
    }

    override suspend fun resumeExecution(
        executionId: String,
        userId: String,
        team: ModelTeam
    ): Flow<TeamExecution> = channelFlow {
        val existing = teamRepository.getExecutionById(executionId) ?: return@channelFlow
        check(existing.userId == userId && existing.teamId == team.id && team.ownerId == userId) {
            "Execution is not owned by the authenticated user and selected team."
        }

        suspend fun resumeActiveWorker(control: ExecutionControl) {
            val resumed = control.mutex.withLock {
                check(activeExecutionJobs[executionId]?.isActive == true) {
                    "The execution has no active worker."
                }
                val latest = control.latestExecution ?: teamRepository.getExecutionById(executionId)
                    ?: return@withLock null
                check(latest.userId == userId && latest.teamId == team.id) {
                    "Execution is not owned by the authenticated user and selected team."
                }
                if (isTerminal(latest.status)) {
                    latest
                } else {
                    when (control.state.value) {
                        ControlState.PAUSED -> latest.copy(status = ExecutionStatus.EXECUTING).also { updated ->
                            teamRepository.logExecution(updated).getOrThrow()
                            control.latestExecution = updated
                            control.state.value = ControlState.RUNNING
                        }
                        ControlState.RUNNING -> latest
                        ControlState.STOPPED -> throw IllegalStateException(
                            "A stopped execution cannot be resumed."
                        )
                    }
                }
            }
            resumed?.let { send(it) }
        }

        if (isTerminal(existing.status)) {
            send(existing)
            return@channelFlow
        }

        val active = synchronized(executionRegistryLock) {
            executionControls[executionId] to activeExecutionJobs[executionId]
        }
        if (active.first != null && active.second?.isActive == true) {
            resumeActiveWorker(active.first!!)
            return@channelFlow
        }

        if (existing.status != ExecutionStatus.PAUSED && existing.status != ExecutionStatus.EXECUTING) {
            send(existing)
            return@channelFlow
        }

        check(
            team.collaborationStyle == CollaborationStyle.SEQUENTIAL ||
                team.collaborationStyle == CollaborationStyle.PARALLEL ||
                team.collaborationStyle == CollaborationStyle.ORCHESTRATED
        ) {
            "Process recovery is currently supported only for sequential, parallel and DAG orchestrations."
        }

        val workerJob = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("A recovery worker could not be registered.")
        val control = ExecutionControl().apply { latestExecution = existing }
        val claimed = synchronized(executionRegistryLock) {
            val currentJob = activeExecutionJobs[executionId]
            if (currentJob?.isActive == true) {
                false
            } else {
                executionControls[executionId] = control
                activeExecutionJobs[executionId] = workerJob
                true
            }
        }
        if (!claimed) {
            val latestActive = synchronized(executionRegistryLock) {
                executionControls[executionId] to activeExecutionJobs[executionId]
            }
            if (latestActive.first != null && latestActive.second?.isActive == true) {
                resumeActiveWorker(latestActive.first!!)
                return@channelFlow
            }
            throw IllegalStateException("Another worker is already recovering this execution.")
        }

        var currentExecution = existing
        var lastExecution: TeamExecution? = existing
        try {
            val interruptedCount = existing.steps.count { it.status == StepStatus.IN_PROGRESS }
            val recoveryMessage = if (interruptedCount > 0) {
                "Process recovery restarted " + interruptedCount +
                    " interrupted step(s). Completed steps were preserved; an interrupted provider call may be repeated if its response was not persisted."
            } else {
                "Process recovery resumed from the last persisted execution checkpoint."
            }
            currentExecution = existing.copy(
                status = ExecutionStatus.EXECUTING,
                error = null,
                completedAt = null,
                messages = existing.messages + AgentMessage(
                    id = UUID.randomUUID().toString(),
                    fromMemberId = "SYSTEM",
                    toMemberId = "TEAM",
                    content = recoveryMessage,
                    type = MessageType.SYSTEM,
                    timestamp = System.currentTimeMillis()
                )
            )
            teamRepository.logExecution(currentExecution).getOrThrow()
            control.latestExecution = currentExecution
            lastExecution = currentExecution
            send(currentExecution)

            suspend fun publish(updatedExecution: TeamExecution) {
                val published = control.mutex.withLock {
                    val snapshot = when (control.state.value) {
                        ControlState.STOPPED -> updatedExecution.copy(
                            status = ExecutionStatus.CANCELLED,
                            completedAt = System.currentTimeMillis()
                        )
                        ControlState.PAUSED -> if (isTerminal(updatedExecution.status)) {
                            updatedExecution
                        } else {
                            updatedExecution.copy(status = ExecutionStatus.PAUSED)
                        }
                        ControlState.RUNNING -> updatedExecution
                    }
                    teamRepository.logExecution(snapshot).getOrThrow()
                    control.latestExecution = snapshot
                    currentExecution = snapshot
                    lastExecution = snapshot
                    send(snapshot)
                    snapshot
                }

                when (control.state.value) {
                    ControlState.STOPPED -> throw ExecutionStoppedException()
                    ControlState.PAUSED -> if (!isTerminal(published.status)) {
                        val nextState = control.state.first { it != ControlState.PAUSED }
                        if (nextState == ControlState.STOPPED) throw ExecutionStoppedException()
                    }
                    ControlState.RUNNING -> Unit
                }
            }

            try {
                getOrchestratorForStyle(team.collaborationStyle).execute(
                    userId,
                    team,
                    currentExecution
                ) { updated ->
                    publish(updated)
                }
            } catch (stopped: ExecutionStoppedException) {
                throw stopped
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentExecution = currentExecution.copy(
                    status = ExecutionStatus.FAILED,
                    error = error.message,
                    completedAt = System.currentTimeMillis()
                )
                publish(currentExecution)
            }
        } catch (stopped: ExecutionStoppedException) {
            val cancelledExecution = control.mutex.withLock {
                val latest = control.latestExecution ?: lastExecution
                if (latest == null) {
                    null
                } else if (latest.status == ExecutionStatus.CANCELLED) {
                    latest
                } else {
                    latest.copy(
                        status = ExecutionStatus.CANCELLED,
                        completedAt = System.currentTimeMillis()
                    ).also { cancelled ->
                        teamRepository.logExecution(cancelled).getOrThrow()
                        control.latestExecution = cancelled
                        lastExecution = cancelled
                    }
                }
            }
            cancelledExecution?.let { send(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failedExecution = control.mutex.withLock {
                val latest = control.latestExecution ?: currentExecution
                latest.copy(
                    status = ExecutionStatus.FAILED,
                    error = error.message,
                    completedAt = System.currentTimeMillis()
                ).also { failed ->
                    teamRepository.logExecution(failed).getOrThrow()
                    control.latestExecution = failed
                    currentExecution = failed
                    lastExecution = failed
                }
            }
            send(failedExecution)
        } finally {
            synchronized(executionRegistryLock) {
                if (executionControls[executionId] === control) {
                    executionControls.remove(executionId)
                }
                if (activeExecutionJobs[executionId] === workerJob) {
                    activeExecutionJobs.remove(executionId)
                }
            }
        }
    }

    override suspend fun retryFailedSteps(
        executionId: String,
        userId: String,
        team: ModelTeam
    ): Flow<TeamExecution> = channelFlow {
        val existingExec = teamRepository.getExecutionById(executionId) ?: return@channelFlow
        check(existingExec.userId == userId && existingExec.teamId == team.id) {
            "Execution is not owned by the authenticated user and selected team."
        }

        val control = ExecutionControl()
        var lastExecution: TeamExecution? = null
        val activeJob = currentCoroutineContext()[Job]
        synchronized(executionRegistryLock) {
            executionControls[executionId] = control
            activeJob?.let { activeExecutionJobs[executionId] = it }
        }

        try {
            val resetSteps = existingExec.steps.map { step ->
                if (step.status == StepStatus.FAILED) {
                    step.copy(
                        status = StepStatus.PENDING,
                        error = null,
                        waitReason = null,
                        retryCount = step.retryCount + 1
                    )
                } else step
            }

            var currentExecution = existingExec.copy(
                steps = resetSteps,
                status = ExecutionStatus.EXECUTING,
                error = null
            )

            suspend fun publish(updatedExecution: TeamExecution) {
                val published = control.mutex.withLock {
                    val snapshot = when (control.state.value) {
                        ControlState.STOPPED -> updatedExecution.copy(
                            status = ExecutionStatus.CANCELLED,
                            completedAt = System.currentTimeMillis()
                        )
                        ControlState.PAUSED -> if (isTerminal(updatedExecution.status)) {
                            updatedExecution
                        } else {
                            updatedExecution.copy(status = ExecutionStatus.PAUSED)
                        }
                        ControlState.RUNNING -> updatedExecution
                    }
                    teamRepository.logExecution(snapshot).getOrThrow()
                    control.latestExecution = snapshot
                    currentExecution = snapshot
                    lastExecution = snapshot
                    send(snapshot)
                    snapshot
                }
    
                when (control.state.value) {
                    ControlState.STOPPED -> throw ExecutionStoppedException()
                    ControlState.PAUSED -> if (!isTerminal(published.status)) {
                        val nextState = control.state.first { it != ControlState.PAUSED }
                        if (nextState == ControlState.STOPPED) throw ExecutionStoppedException()
                    }
                    ControlState.RUNNING -> Unit
                }
            }
    
                publish(currentExecution)

            try {
                val orchestrator = getOrchestratorForStyle(team.collaborationStyle)
                orchestrator.execute(userId, team, currentExecution) { updated ->
                    publish(updated)
                }
            } catch (stopped: ExecutionStoppedException) {
                throw stopped
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentExecution = currentExecution.copy(
                    status = ExecutionStatus.FAILED,
                    error = error.message,
                    completedAt = System.currentTimeMillis()
                )
                publish(currentExecution)
            }
        } catch (stopped: ExecutionStoppedException) {
            val cancelledExecution = control.mutex.withLock {
                val latest = control.latestExecution ?: lastExecution
                if (latest == null) {
                    null
                } else if (latest.status == ExecutionStatus.CANCELLED) {
                    latest
                } else {
                    latest.copy(
                        status = ExecutionStatus.CANCELLED,
                        completedAt = System.currentTimeMillis()
                    ).also { cancelled ->
                        teamRepository.logExecution(cancelled).getOrThrow()
                        control.latestExecution = cancelled
                        lastExecution = cancelled
                    }
                }
            }
            cancelledExecution?.let { send(it) }
        } finally {
            activeJob?.let { activeExecutionJobs.remove(executionId, it) }
            executionControls.remove(executionId, control)
        }
    }

    override suspend fun assignDirectTask(
        executionId: String,
        userId: String,
        team: ModelTeam,
        memberId: String,
        taskPrompt: String
    ): Flow<TeamExecution> = flow {
        val existingExec = teamRepository.getExecutionById(executionId) 
            ?: TeamExecution(id = executionId, teamId = team.id, userId = userId, prompt = taskPrompt)
        val member = team.members.find { it.id == memberId } ?: team.members.firstOrNull()
        val memberName = member?.name ?: "Agent"
        val memberRole = member?.role?.labelAr ?: "Developer"

        val stepId = UUID.randomUUID().toString()
        val newStep = ExecutionStep(
            id = stepId,
            memberId = memberId,
            memberName = memberName,
            role = memberRole,
            task = taskPrompt,
            status = StepStatus.IN_PROGRESS,
            startedAt = System.currentTimeMillis()
        )

        val userMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            fromMemberId = "USER",
            toMemberId = memberId,
            content = "مهمة مباشرة إلى $memberName: $taskPrompt",
            type = MessageType.INSTRUCTION,
            relatedStepId = stepId,
            timestamp = System.currentTimeMillis()
        )

        var currentExecution = existingExec.copy(
            steps = existingExec.steps + newStep,
            messages = existingExec.messages + userMsg,
            status = ExecutionStatus.EXECUTING
        )
        emit(currentExecution)
        teamRepository.logExecution(currentExecution)
        teamRepository.updateMemberStatus(team.id, memberId, MemberStatus.WORKING, taskPrompt)

        try {
            val response = aiModelRepository.generateResponse(
                userId = userId,
                modelId = member?.modelId ?: "",
                prompt = "Task assigned directly by user:\nGoal: ${team.goal}\nTask: $taskPrompt",
                taskType = AITaskType.AGENT_TEAM_TASK
            )

            val output = response.getOrThrow()
            val completedStep = newStep.copy(
                status = StepStatus.COMPLETED,
                output = output,
                completedAt = System.currentTimeMillis()
            )

            val agentReply = AgentMessage(
                id = UUID.randomUUID().toString(),
                fromMemberId = memberId,
                toMemberId = "USER",
                content = output,
                type = MessageType.REGULAR,
                relatedStepId = stepId,
                timestamp = System.currentTimeMillis()
            )

            currentExecution = currentExecution.copy(
                steps = currentExecution.steps.map { if (it.id == stepId) completedStep else it },
                messages = currentExecution.messages + agentReply,
                status = ExecutionStatus.COMPLETED
            )
            emit(currentExecution)
            teamRepository.logExecution(currentExecution)
            teamRepository.updateMemberStatus(team.id, memberId, MemberStatus.COMPLETED, taskPrompt, output.take(150))
        } catch (e: Exception) {
            val failedStep = newStep.copy(
                status = StepStatus.FAILED,
                error = e.message,
                waitReason = "فشل في التنفيذ: ${e.message}",
                completedAt = System.currentTimeMillis()
            )
            currentExecution = currentExecution.copy(
                steps = currentExecution.steps.map { if (it.id == stepId) failedStep else it },
                error = e.message,
                status = ExecutionStatus.FAILED
            )
            emit(currentExecution)
            teamRepository.logExecution(currentExecution)
            teamRepository.updateMemberStatus(team.id, memberId, MemberStatus.FAILED, taskPrompt, "Error: ${e.message}")
        }
    }

    override suspend fun sendInstruction(
        executionId: String,
        userId: String,
        team: ModelTeam,
        instruction: TeamInstruction
    ): Flow<TeamExecution> = flow {
        val existingExec = teamRepository.getExecutionById(executionId) ?: return@flow

        when (instruction) {
            is TeamInstruction.ToLeader -> {
                val leader = team.members.find { it.id == team.leaderMemberId } ?: team.members.first()
                val msg = AgentMessage(
                    id = UUID.randomUUID().toString(),
                    fromMemberId = "USER",
                    toMemberId = leader.id,
                    content = "تعليمات للقائد: ${instruction.text}",
                    type = MessageType.INSTRUCTION,
                    timestamp = System.currentTimeMillis()
                )
                var updated = existingExec.copy(messages = existingExec.messages + msg)
                emit(updated)
                teamRepository.logExecution(updated)

                teamRepository.updateMemberStatus(team.id, leader.id, MemberStatus.WORKING, "معالجة تعليمات المستخدم")
                try {
                    val prompt = "User has provided direct guidance to you as Team Leader:\nInstruction: ${instruction.text}\nCurrent Plan & Progress:\n${existingExec.steps.joinToString("\n") { "${it.memberName} (${it.role}): ${it.status}" }}\nProvide your strategic plan adjustment and response."
                    val reply = aiModelRepository.generateResponse(
                        userId = userId,
                        modelId = leader.modelId,
                        prompt = prompt,
                        taskType = AITaskType.AGENT_TEAM_TASK
                    ).getOrThrow()
                    val leaderReply = AgentMessage(
                        id = UUID.randomUUID().toString(),
                        fromMemberId = leader.id,
                        toMemberId = "USER",
                        content = reply,
                        type = MessageType.INSTRUCTION,
                        timestamp = System.currentTimeMillis()
                    )
                    updated = updated.copy(messages = updated.messages + leaderReply)
                    emit(updated)
                    teamRepository.logExecution(updated)
                    teamRepository.updateMemberStatus(team.id, leader.id, MemberStatus.COMPLETED, "توجيه استراتيجي", reply.take(150))
                } catch (e: Exception) {
                    // Log failure
                }
            }

            is TeamInstruction.ToMember -> {
                assignDirectTask(executionId, userId, team, instruction.memberId, instruction.text).collect {
                    emit(it)
                }
            }

            is TeamInstruction.BroadcastAll -> {
                val broadcastMsg = AgentMessage(
                    id = UUID.randomUUID().toString(),
                    fromMemberId = "USER",
                    toMemberId = "ALL",
                    content = "ملاحظة للفريق كاملًا: ${instruction.text}",
                    type = MessageType.INSTRUCTION,
                    timestamp = System.currentTimeMillis()
                )
                val newConstraints = existingExec.sharedMemory.technicalConstraints + instruction.text
                val updated = existingExec.copy(
                    messages = existingExec.messages + broadcastMsg,
                    sharedMemory = existingExec.sharedMemory.copy(technicalConstraints = newConstraints)
                )
                emit(updated)
                teamRepository.logExecution(updated)
            }

            is TeamInstruction.RequestReview -> {
                val reviewer = team.members.find { it.role == TeamRole.CODE_REVIEWER || it.role == TeamRole.SECURITY_AUDITOR || it.role == TeamRole.QA_ENGINEER } 
                    ?: team.members.find { it.id != team.leaderMemberId } ?: team.members.first()
                
                val reqMsg = AgentMessage(
                    id = UUID.randomUUID().toString(),
                    fromMemberId = "USER",
                    toMemberId = reviewer.id,
                    content = "طلب مراجعة جديدة: ${instruction.critiqueFocus}",
                    type = MessageType.REVIEW,
                    timestamp = System.currentTimeMillis()
                )
                var updated = existingExec.copy(messages = existingExec.messages + reqMsg)
                emit(updated)
                teamRepository.logExecution(updated)

                teamRepository.updateMemberStatus(team.id, reviewer.id, MemberStatus.WORKING, "إجراء مراجعة نقدية")
                try {
                    val workToReview = existingExec.steps.filter { it.output != null }
                        .joinToString("\n\n") { "Step: ${it.task} (${it.memberName}):\n${it.output}" }
                    val prompt = "You are reviewing the team's work as ${reviewer.role.labelEn}.\nFocus: ${instruction.critiqueFocus}\nWork to review:\n$workToReview\nProvide thorough critique, potential bugs, edge cases, and verdict."
                    val reviewResult = aiModelRepository.generateResponse(
                        userId = userId,
                        modelId = reviewer.modelId,
                        prompt = prompt,
                        taskType = AITaskType.AGENT_TEAM_TASK
                    ).getOrThrow()
                    val reviewReply = AgentMessage(
                        id = UUID.randomUUID().toString(),
                        fromMemberId = reviewer.id,
                        toMemberId = "TEAM",
                        content = reviewResult,
                        type = MessageType.REVIEW,
                        timestamp = System.currentTimeMillis()
                    )
                    updated = updated.copy(messages = updated.messages + reviewReply)
                    emit(updated)
                    teamRepository.logExecution(updated)
                    teamRepository.updateMemberStatus(team.id, reviewer.id, MemberStatus.COMPLETED, "اكتملت المراجعة", reviewResult.take(150))
                } catch (e: Exception) {
                    // Log failure
                }
            }

            is TeamInstruction.RetryStep -> {
                val step = existingExec.steps.find { it.id == instruction.stepId } ?: return@flow
                val resetSteps = existingExec.steps.map {
                    if (it.id == step.id) it.copy(status = StepStatus.PENDING, error = null, retryCount = it.retryCount + 1) else it
                }
                var updated = existingExec.copy(steps = resetSteps, status = ExecutionStatus.EXECUTING)
                emit(updated)
                teamRepository.logExecution(updated)

                val member = team.members.find { it.id == step.memberId } ?: team.members.first()
                teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.WORKING, "إعادة تنفيذ: ${step.task}")
                try {
                    val res = aiModelRepository.generateResponse(
                        userId = userId,
                        modelId = member.modelId,
                        prompt = "Retry Task:\n${step.task}\nInput:\n${step.input}",
                        taskType = AITaskType.AGENT_TEAM_TASK
                    ).getOrThrow()
                    val completedStep = step.copy(status = StepStatus.COMPLETED, output = res, completedAt = System.currentTimeMillis())
                    updated = updated.copy(
                        steps = updated.steps.map { if (it.id == step.id) completedStep else it }
                    )
                    emit(updated)
                    teamRepository.logExecution(updated)
                    teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.COMPLETED, step.task, res.take(150))
                } catch (e: Exception) {
                    val failedStep = step.copy(status = StepStatus.FAILED, error = e.message, completedAt = System.currentTimeMillis())
                    updated = updated.copy(steps = updated.steps.map { if (it.id == step.id) failedStep else it })
                    emit(updated)
                    teamRepository.logExecution(updated)
                    teamRepository.updateMemberStatus(team.id, member.id, MemberStatus.FAILED, step.task, e.message)
                }
            }
        }
    }

    override suspend fun changeMemberAgent(teamId: String, memberId: String, newModelId: String): Result<Unit> = runCatching {
        val team = teamRepository.getTeamById(teamId) ?: throw Exception("Team not found")
        val updatedMembers = team.members.map {
            if (it.id == memberId) it.copy(modelId = newModelId) else it
        }
        teamRepository.updateTeam(team.copy(members = updatedMembers)).getOrThrow()
    }


    private fun getOrchestratorForStyle(style: CollaborationStyle): ModeOrchestrator {
        return when (style) {
            CollaborationStyle.SEQUENTIAL, 
            CollaborationStyle.PARALLEL, 
            CollaborationStyle.ORCHESTRATED -> DagOrchestrator(
                aiModelRepository, teamRepository, taskPlanner, resultEvaluator, sharedContextStore, responseComposer, gov
            )
            CollaborationStyle.DEBATE -> DebateOrchestrator(aiModelRepository, teamRepository)
            CollaborationStyle.VOTING -> VotingOrchestrator(aiModelRepository, teamRepository)
            CollaborationStyle.REVIEW_CRITIQUE -> CritiqueOrchestrator(aiModelRepository, teamRepository)
            CollaborationStyle.HIERARCHICAL -> HierarchicalOrchestrator(
                aiModelRepository, teamRepository, taskPlanner, responseComposer, sharedContextStore
            )
            CollaborationStyle.VIBE_DEVELOPMENT -> VibeDevelopmentOrchestrator(
                aiModelRepository, teamRepository, vibeRepository, sharedContextStore, responseComposer
            )
            CollaborationStyle.CRASH_INVESTIGATION -> CrashInvestigationOrchestrator(
                aiModelRepository, teamRepository, crashRepository, vibeRepository, crashDao, sharedContextStore, responseComposer
            )
            CollaborationStyle.ADAPTIVE -> AdaptiveOrchestrator(aiModelRepository, teamRepository) { style ->
                getOrchestratorForStyle(style)
            }
        }
    }

    private suspend fun updateStepStatus(
        execution: TeamExecution,
        stepId: String,
        status: StepStatus,
        retryCount: Int? = null,
        error: String? = null,
        update: suspend (TeamExecution) -> Unit
    ) {
        val updatedSteps = execution.steps.map {
            if (it.id == stepId) it.copy(
                status = status, 
                retryCount = retryCount ?: it.retryCount,
                error = error ?: it.error
            ) else it
        }
        update(execution.copy(steps = updatedSteps))
    }

    private suspend fun updateStepResult(
        execution: TeamExecution,
        stepId: String,
        status: StepStatus,
        output: String,
        update: suspend (TeamExecution) -> Unit
    ) {
        val updatedSteps = execution.steps.map {
            if (it.id == stepId) it.copy(status = status, output = output, completedAt = System.currentTimeMillis()) else it
        }
        update(execution.copy(steps = updatedSteps))
    }

    override suspend fun planAndDistribute(
        team: ModelTeam,
        prompt: String
    ): Result<List<ExecutionStep>> = runCatching {
        taskPlanner.plan(team.ownerId, team, prompt, "").getOrThrow()
    }
}
