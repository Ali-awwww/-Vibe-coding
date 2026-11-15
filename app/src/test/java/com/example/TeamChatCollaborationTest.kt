package com.example

import com.example.domain.agent.TeamOrchestrator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import com.example.presentation.viewmodel.TeamChatTab
import com.example.presentation.viewmodel.TeamChatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config
@OptIn(ExperimentalCoroutinesApi::class)
class TeamChatCollaborationTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fakeTeam = ModelTeam(
        id = "team_alpha",
        name = "Alpha Engineering Team",
        description = "Collaborative team for bug diagnosis and repair",
        goal = "Analyze stack traces and produce robust code fixes",
        ownerId = "usr_test",
        leaderMemberId = "mem_leader",
        members = listOf(
            TeamMember(
                id = "mem_leader",
                name = "Architect Leader",
                modelId = "gemini-2.0-flash",
                role = TeamRole.TEAM_LEADER,
                status = MemberStatus.IDLE
            ),
            TeamMember(
                id = "mem_investigator",
                name = "Crash Investigator",
                modelId = "gemini-2.0-flash",
                role = TeamRole.SOURCE_CODE_INVESTIGATOR,
                status = MemberStatus.IDLE
            ),
            TeamMember(
                id = "mem_reviewer",
                name = "Security Reviewer",
                modelId = "gemini-2.0-flash",
                role = TeamRole.SECURITY_AUDITOR,
                status = MemberStatus.IDLE
            )
        ),
        collaborationStyle = CollaborationStyle.ORCHESTRATED,
        governance = TeamGovernanceSettings(freeOnly = true)
    )

    private val fakeProject = Project(
        id = "proj_01",
        name = "E-Commerce App",
        description = "Production Android application"
    )

    @Test
    fun testInitialStateLoadsTeamAndProjects() = runTest(testDispatcher) {
        val mockTeamRepo = createMockTeamRepo(listOf(fakeTeam))
        val mockOrchestrator = createMockOrchestrator()
        val mockCrashRepo = createMockCrashRepo(listOf(fakeProject))
        val mockAiRepo = createMockAiRepo()
        val mockAuthRepo = createMockAuthRepo()

        val viewModel = TeamChatViewModel(
            initialTeamId = fakeTeam.id,
            initialProjectId = fakeProject.id,
            teamRepository = mockTeamRepo,
            teamOrchestrator = mockOrchestrator,
            crashRepository = mockCrashRepo,
            aiModelRepository = mockAiRepo,
            authRepository = mockAuthRepo
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("team_alpha", state.selectedTeam?.id)
        assertEquals("proj_01", state.selectedProject?.id)
        assertEquals(TeamChatTab.CHAT_INPUT, state.activeTab)
    }

    @Test
    fun testAttachFileValidationAndRemoval() = runTest(testDispatcher) {
        val mockTeamRepo = createMockTeamRepo(listOf(fakeTeam))
        val mockOrchestrator = createMockOrchestrator()
        val mockCrashRepo = createMockCrashRepo(listOf(fakeProject))
        val mockAiRepo = createMockAiRepo()
        val mockAuthRepo = createMockAuthRepo()

        val viewModel = TeamChatViewModel(
            initialTeamId = fakeTeam.id,
            initialProjectId = null,
            teamRepository = mockTeamRepo,
            teamOrchestrator = mockOrchestrator,
            crashRepository = mockCrashRepo,
            aiModelRepository = mockAiRepo,
            authRepository = mockAuthRepo
        )

        advanceUntilIdle()

        viewModel.addAttachedFile(
            name = "CrashLog.txt",
            path = "/logs/crash.txt",
            content = "NullPointerException at line 42",
            sizeBytes = 28
        )

        assertEquals(1, viewModel.uiState.value.attachedFiles.size)
        assertEquals("CrashLog.txt", viewModel.uiState.value.attachedFiles[0].name)

        viewModel.removeAttachedFile(0)
        assertEquals(0, viewModel.uiState.value.attachedFiles.size)
    }

    @Test
    fun testSendTaskAndLiveExecutionUpdates() = runTest(testDispatcher) {
        val mockTeamRepo = createMockTeamRepo(listOf(fakeTeam))
        val mockOrchestrator = createMockOrchestrator()
        val mockCrashRepo = createMockCrashRepo(listOf(fakeProject))
        val mockAiRepo = createMockAiRepo()
        val mockAuthRepo = createMockAuthRepo()

        val viewModel = TeamChatViewModel(
            initialTeamId = fakeTeam.id,
            initialProjectId = fakeProject.id,
            teamRepository = mockTeamRepo,
            teamOrchestrator = mockOrchestrator,
            crashRepository = mockCrashRepo,
            aiModelRepository = mockAiRepo,
            authRepository = mockAuthRepo
        )

        advanceUntilIdle()

        viewModel.setTaskPrompt("Investigate fatal memory leak in BackgroundSyncService")
        viewModel.sendTask(skipEstimationCheck = true)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.currentExecution)
        assertEquals(ExecutionStatus.COMPLETED, state.currentExecution?.status)
        assertEquals(TeamChatTab.LIVE_COLLABORATION, state.activeTab)
        assertTrue(state.currentExecution?.finalResult?.isNotEmpty() == true)
    }

    @Test
    fun testPauseAndResumeTeamExecutionControls() = runTest(testDispatcher) {
        val mockTeamRepo = createMockTeamRepo(listOf(fakeTeam))
        val mockOrchestrator = createMockOrchestrator()
        val mockCrashRepo = createMockCrashRepo(listOf(fakeProject))
        val mockAiRepo = createMockAiRepo()
        val mockAuthRepo = createMockAuthRepo()

        val viewModel = TeamChatViewModel(
            initialTeamId = fakeTeam.id,
            initialProjectId = fakeProject.id,
            teamRepository = mockTeamRepo,
            teamOrchestrator = mockOrchestrator,
            crashRepository = mockCrashRepo,
            aiModelRepository = mockAiRepo,
            authRepository = mockAuthRepo
        )

        advanceUntilIdle()

        viewModel.setTaskPrompt("Generate comprehensive unit tests for PaymentProcessor")
        viewModel.sendTask(skipEstimationCheck = true)
        advanceUntilIdle()

        // Pause
        viewModel.pauseTeam()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPaused)

        // Resume
        viewModel.resumeTeam()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPaused)
    }

    @Test
    fun testSendInstructionsToLeaderAndMember() = runTest(testDispatcher) {
        val mockTeamRepo = createMockTeamRepo(listOf(fakeTeam))
        val mockOrchestrator = createMockOrchestrator()
        val mockCrashRepo = createMockCrashRepo(listOf(fakeProject))
        val mockAiRepo = createMockAiRepo()
        val mockAuthRepo = createMockAuthRepo()

        val viewModel = TeamChatViewModel(
            initialTeamId = fakeTeam.id,
            initialProjectId = fakeProject.id,
            teamRepository = mockTeamRepo,
            teamOrchestrator = mockOrchestrator,
            crashRepository = mockCrashRepo,
            aiModelRepository = mockAiRepo,
            authRepository = mockAuthRepo
        )

        advanceUntilIdle()

        viewModel.setTaskPrompt("Debug network timeout")
        viewModel.sendTask(skipEstimationCheck = true)
        advanceUntilIdle()

        // Send instruction to leader
        viewModel.sendInstruction(TeamInstruction.ToLeader("Prioritize caching before re-fetching"))
        advanceUntilIdle()

        val messages = viewModel.uiState.value.currentExecution?.messages ?: emptyList()
        assertTrue(messages.any { it.content.contains("Prioritize caching") })
    }

    // --- Mock Helpers ---

    private fun createMockTeamRepo(teams: List<ModelTeam>): AIModelTeamRepository {
        var teamList = teams
        val executions = mutableListOf<TeamExecution>()

        return object : AIModelTeamRepository {
            override fun getTeams(userId: String) = flowOf(teamList)
            override suspend fun getTeamById(teamId: String) = teamList.find { it.id == teamId }
            override suspend fun createTeam(team: ModelTeam): Result<String> {
                teamList = teamList + team
                return Result.success(team.id)
            }
            override suspend fun updateTeam(team: ModelTeam): Result<Unit> {
                teamList = teamList.map { if (it.id == team.id) team else it }
                return Result.success(Unit)
            }
            override suspend fun deleteTeam(teamId: String): Result<Unit> = Result.success(Unit)
            override fun getTeamExecutions(teamId: String) = flowOf(executions.filter { it.teamId == teamId })
            override suspend fun getExecutionById(executionId: String) = executions.find { it.id == executionId }
            override suspend fun logExecution(execution: TeamExecution): Result<Unit> {
                executions.removeAll { it.id == execution.id }
                executions.add(execution)
                return Result.success(Unit)
            }
            override suspend fun addTeamMember(teamId: String, member: TeamMember) = Result.success(Unit)
            override suspend fun removeTeamMember(teamId: String, memberId: String) = Result.success(Unit)
            override suspend fun updateMemberStatus(
                teamId: String,
                memberId: String,
                status: MemberStatus,
                currentTask: String?,
                lastResult: String?
            ) = Result.success(Unit)
            override fun getTeamUsageRecords(teamId: String) = flowOf(emptyList<TeamUsageRecord>())
            override fun getAllTeamUsageRecords(userId: String) = flowOf(emptyList<TeamUsageRecord>())
            override suspend fun logTeamUsage(record: TeamUsageRecord) = Result.success(Unit)
        }
    }

    private fun createMockOrchestrator(): TeamOrchestrator {
        return object : TeamOrchestrator {
            override fun executeTask(userId: String, team: ModelTeam, prompt: String) =
                executeTaskWithContext(userId, team, prompt)

            override fun executeTaskWithContext(
                userId: String,
                team: ModelTeam,
                prompt: String,
                attachedFiles: List<AttachedFileContext>,
                projectId: String?
            ): Flow<TeamExecution> {
                val step1 = ExecutionStep(
                    id = "step_01",
                    memberId = "mem_leader",
                    memberName = "Architect Leader",
                    role = "Team Leader",
                    task = "Decompose root cause analysis",
                    status = StepStatus.COMPLETED,
                    output = "Plan decomposed into 2 subtasks."
                )
                val step2 = ExecutionStep(
                    id = "step_02",
                    memberId = "mem_investigator",
                    memberName = "Crash Investigator",
                    role = "Source Code Investigator",
                    task = "Inspect call site in BackgroundSyncService",
                    status = StepStatus.COMPLETED,
                    output = "Found NPE: missing null check on syncToken."
                )
                val execution = TeamExecution(
                    id = UUID.randomUUID().toString(),
                    teamId = team.id,
                    userId = userId,
                    prompt = prompt,
                    status = ExecutionStatus.COMPLETED,
                    steps = listOf(step1, step2),
                    finalResult = "All subtasks completed successfully with fix verified.",
                    attachedFiles = attachedFiles,
                    projectId = projectId
                )
                return flowOf(execution)
            }

            override suspend fun pauseExecution(executionId: String) {}
            override suspend fun resumeExecution(executionId: String, userId: String, team: ModelTeam) =
                flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.COMPLETED))
            override suspend fun stopExecution(executionId: String) {}
            override suspend fun retryFailedSteps(executionId: String, userId: String, team: ModelTeam) =
                flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.COMPLETED))
            override suspend fun assignDirectTask(
                executionId: String,
                userId: String,
                team: ModelTeam,
                memberId: String,
                taskPrompt: String
            ) = flowOf(TeamExecution(id = executionId, teamId = team.id, status = ExecutionStatus.COMPLETED))
            override suspend fun sendInstruction(
                executionId: String,
                userId: String,
                team: ModelTeam,
                instruction: TeamInstruction
            ): Flow<TeamExecution> {
                val text = when (instruction) {
                    is TeamInstruction.ToLeader -> instruction.text
                    is TeamInstruction.ToMember -> instruction.text
                    is TeamInstruction.BroadcastAll -> instruction.text
                    is TeamInstruction.RequestReview -> instruction.critiqueFocus
                    is TeamInstruction.RetryStep -> instruction.stepId
                }
                val msg = AgentMessage(
                    id = UUID.randomUUID().toString(),
                    fromMemberId = "USER",
                    toMemberId = "mem_leader",
                    content = text,
                    type = MessageType.INSTRUCTION
                )
                return flowOf(TeamExecution(id = executionId, teamId = team.id, messages = listOf(msg)))
            }
            override suspend fun changeMemberAgent(teamId: String, memberId: String, newModelId: String) = Result.success(Unit)
            override suspend fun planAndDistribute(team: ModelTeam, prompt: String) = Result.success(emptyList<ExecutionStep>())
            override suspend fun estimateTaskResources(userId: String, team: ModelTeam, prompt: String) =
                PreExecutionEstimation(estimatedTasksCount = 2, isFreeOnly = true)
        }
    }

    private fun createMockCrashRepo(projects: List<Project>): CrashRepository {
        return object : CrashRepository {
            override fun getProjects() = flowOf(projects)
            override fun getProjectsForUser(userId: String) = flowOf(projects)
            override suspend fun getProjectById(projectId: String) = projects.find { it.id == projectId }
            override suspend fun addProject(project: Project) {}
            override suspend fun deleteProject(projectId: String, callerUserId: String) = Result.success(Unit)
            override fun getApplications(projectId: String) = flowOf(emptyList<Application>())
            override fun getAllApplications() = flowOf(emptyList<Application>())
            override suspend fun addApplication(application: Application) {}
            override suspend fun deleteApplication(applicationId: String) {}
            override fun getLatestCrashes(projectId: String?, applicationId: String?) = flowOf(emptyList<CrashReport>())
            override fun getAllCrashes() = flowOf(emptyList<CrashReport>())
            override fun getCrashDetails(crashId: String) = flowOf(null)
            override suspend fun addCrash(crash: CrashReport) {}
            override suspend fun updateCrashStatus(crashId: String, isResolved: Boolean) {}
            override suspend fun getAIDiagnosis(crashReport: CrashReport): AIDiagnosis = throw NotImplementedError()
            override fun getIntegrations(projectId: String) = flowOf(emptyList<Integration>())
            override suspend fun addIntegration(integration: Integration) {}
            override suspend fun deleteIntegration(integrationId: String, projectId: String, callerUserId: String) = Result.success(Unit)
            override suspend fun validateWebhook(projectId: String, integrationId: String, payload: String, signatureHeader: String?) = Result.success(true)
            override fun getIssues(projectId: String) = flowOf(emptyList<Issue>())
            override fun getAllIssues() = flowOf(emptyList<Issue>())
            override fun getIssueDetails(issueId: String) = flowOf(null)
            override suspend fun addIssue(issue: Issue) {}
            override suspend fun updateIssueStatus(issueId: String, status: IssueStatus) {}
            override suspend fun getVersionStats(projectId: String, version: String): VersionStats = throw NotImplementedError()
            override suspend fun syncCrashes(projectId: String) = Result.success(Unit)

            override fun getProjectMembers(projectId: String): Flow<List<ProjectMember>> = flowOf(emptyList())
            override suspend fun getUserRoleInProject(projectId: String, userId: String): Role? = Role.ADMIN
            override suspend fun addProjectMember(projectId: String, email: String, role: Role, callerUserId: String): Result<Unit> = Result.success(Unit)
            override suspend fun updateMemberRole(projectId: String, userId: String, newRole: Role, callerUserId: String): Result<Unit> = Result.success(Unit)
            override suspend fun removeProjectMember(projectId: String, userId: String, callerUserId: String): Result<Unit> = Result.success(Unit)
            override suspend fun updateProjectSecurityPolicy(projectId: String, retentionDays: Int, piiScrubbing: Boolean, callerUserId: String): Result<Unit> = Result.success(Unit)
            override suspend fun purgeOldCrashes(projectId: String, callerUserId: String): Result<Int> = Result.success(0)
            override fun getAuditLogsForProject(projectId: String): Flow<List<AuditLog>> = flowOf(emptyList())
            override fun getRecentAuditLogs(): Flow<List<AuditLog>> = flowOf(emptyList())
            override suspend fun logAuditEvent(auditLog: AuditLog) {}
            override suspend fun addRepositoryMapping(mapping: RepositoryMapping) {}
            override suspend fun saveGitHubConnection(connection: GitHubConnection) {}
            override fun getFixSuggestions(crashId: String): Flow<List<FixSuggestion>> = flowOf(emptyList())
            override suspend fun generateFixSuggestion(crashReport: CrashReport, diagnosis: AIDiagnosis): Result<FixSuggestion> = throw NotImplementedError()
            override suspend fun applyFixAndCreatePR(fixId: String, userId: String): Result<String> = Result.success("https://github.com/pr/1")
            override fun getAllVerifications(): Flow<List<CrashVerificationRecord>> = flowOf(emptyList())
            override fun getVerificationsByProject(projectId: String): Flow<List<CrashVerificationRecord>> = flowOf(emptyList())
            override fun getVerificationsByCrash(crashId: String): Flow<List<CrashVerificationRecord>> = flowOf(emptyList())
            override suspend fun getVerificationById(id: String): CrashVerificationRecord? = null
            override suspend fun verifyFix(
                crashId: String,
                fixSuggestionId: String?,
                newVersion: String,
                buildId: String,
                scenarioName: String,
                simulatedOutcome: VerificationStatus?
            ): Result<CrashVerificationRecord> = throw NotImplementedError()
            override suspend fun addVerificationRecord(record: CrashVerificationRecord) {}
            override fun getNotifications(): Flow<List<SystemNotification>> = flowOf(emptyList())
            override fun getNotificationsForProject(projectId: String): Flow<List<SystemNotification>> = flowOf(emptyList())
            override fun getUnreadNotificationCount(): Flow<Int> = flowOf(0)
            override suspend fun addNotification(notification: SystemNotification) {}
            override suspend fun markNotificationAsRead(id: String) {}
            override suspend fun markAllNotificationsAsRead() {}
            override suspend fun clearNotifications() {}
        }
    }

    private fun createMockAiRepo(): AIModelRepository {
        return object : AIModelRepository {
            override fun getProviders(userId: String): Flow<List<AIProvider>> = flowOf(emptyList())
            override suspend fun getProviderById(userId: String, id: String): AIProvider? = null
            override suspend fun addProvider(provider: AIProvider) {}
            override suspend fun deleteProvider(userId: String, id: String) {}
            override suspend fun updateProviderStatus(userId: String, id: String, status: AIProviderStatus, syncTime: Long) {}
            override fun getModelsByProvider(userId: String, providerId: String): Flow<List<AIModel>> = flowOf(emptyList())
            override fun getAllAvailableModels(userId: String, freeOnly: Boolean): Flow<List<AIModel>> = flowOf(
                listOf(
                    AIModel(id = "gemini-2.0-flash", providerId = "google", name = "Gemini 2.0 Flash", isFree = true),
                    AIModel(id = "deepseek-coder", providerId = "openrouter", name = "DeepSeek Coder Free", isFree = true)
                )
            )
            override suspend fun getModelById(userId: String, id: String): AIModel? =
                AIModel(id = id, providerId = "google", name = id, isFree = true)
            override suspend fun syncModels(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
            override suspend fun testConnection(userId: String, providerId: String): Result<Unit> = Result.success(Unit)
            override fun getRecentUsageLogs(userId: String): Flow<List<AIUsageLog>> = flowOf(emptyList())
            override fun getTotalCost(userId: String, since: Long): Flow<Double?> = flowOf(0.0)
            override suspend fun logUsage(log: AIUsageLog) {}
            override fun getUserSettings(userId: String): Flow<AIUserSettings?> = flowOf(null)
            override suspend fun updateUserSettings(settings: AIUserSettings) {}
            override suspend fun setDefaultModel(userId: String, modelId: String) {}
            override suspend fun getDefaultModel(userId: String): AIModel? = null
            override suspend fun generateResponse(
                userId: String,
                modelId: String,
                prompt: String,
                systemInstruction: String?,
                taskId: String?,
                taskType: AITaskType
            ): Result<String> = Result.success("Real generated response from model $modelId")
        }
    }

    private fun createMockAuthRepo(): AuthRepository {
        val user = UserAccount(id = "usr_test", email = "engineer@example.com", displayName = "Staff Engineer")
        return object : AuthRepository {
            override val currentUser: Flow<UserAccount?> = flowOf(user)
            override fun getCurrentUserSync(): UserAccount? = user
            override suspend fun signUp(email: String, password: String, displayName: String, organization: String): Result<UserAccount> = Result.success(user)
            override suspend fun signIn(email: String, password: String): Result<UserAccount> = Result.success(user)
            override suspend fun signOut() {}
            override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> = Result.success(Unit)
            override suspend fun signInWithGoogle(idToken: String): Result<Unit> = Result.success(Unit)
        }
    }
}
