package com.example.data.agent

import com.example.domain.agent.*
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.VibeStudioRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.*

class VibeAgentOrchestratorImpl(
    private val aiModelRepository: AIModelRepository,
    private val vibeRepositoryProvider: () -> VibeStudioRepository,
    private val toolAdapter: AgentToolAdapter
) : VibeAgentOrchestrator {
    
    private val vibeRepository: VibeStudioRepository get() = vibeRepositoryProvider()

    private val maxTurns = 5

    override suspend fun processInstruction(
        userId: String,
        workspaceId: String,
        instruction: String,
        mode: AgentExecutionMode
    ): Result<AgentExecutionResult> = runCatching {
        val workspace = vibeRepository.getWorkspaceById(workspaceId) ?: throw Exception("Workspace not found")
        val context = vibeRepository.getProjectContext(workspaceId).firstOrNull()
        
        // 1. Context Selection (RAG + Project Context)
        val relevantFiles = vibeRepository.retrieveRelevantFiles(workspaceId, instruction, maxFiles = 6)
        val filesOverview = relevantFiles.joinToString("\n\n") { "--- ${it.path} ---\n${it.content}" }
        
        val projectInfo = """
            Goals: ${context?.goals}
            Architecture: ${context?.architecturalDecisions}
            Tech Stack Details: ${context?.techStackDetails}
            Recently Completed: ${context?.completedTasks?.takeLast(500)}
            Known Issues: ${context?.knownIssues}
        """.trimIndent()
        
        // 2. Task Type Mapping
        val taskType = when (mode) {
            AgentExecutionMode.PLAN -> AITaskType.VIBE_AGENT_PLANNING
            AgentExecutionMode.AGENT -> AITaskType.VIBE_AGENT_CODING
            AgentExecutionMode.REVIEW -> AITaskType.VIBE_AGENT_REVIEW
        }
        
        // 3. System Instruction Construction
        val tools = toolAdapter.getAvailableTools(taskType)
        val systemPrompt = """
            You are a senior AI Vibe Coding Agent for 'Crash Intelligence AI'.
            
            PROJECT: ${workspace.name} (${workspace.type})
            TECH STACK: ${workspace.techStack}
            
            CONTEXT:
            $projectInfo
            
            RELEVANT FILES:
            $filesOverview
            
            AVAILABLE TOOLS:
            ${tools.joinToString("\n") { "- ${it.name}: ${it.description}. Params: ${it.parameters}" }}
            
            GOVERNANCE:
            - You are running in a sandbox.
            - You cannot access files outside the workspace.
            - You must ask for permission before deleting multiple files or pushing to GitHub.
            
            INSTRUCTIONS:
            1. Analyze the user request against the current context.
            2. Use tools to gather more info or execute changes.
            3. For models without tool-calling support, use JSON: {"tool": "name", "args": {}}.
            4. Verify your work (e.g. build logs or tests).
        """.trimIndent()
        
        var currentPrompt = instruction
        var turnCount = 0
        var lastResponse = ""
        
        // 4. Execution Loop
        while (turnCount < maxTurns) {
            val response = aiModelRepository.generateResponse(
                userId = userId,
                modelId = "auto", // Intelligent routing will pick best free model
                prompt = currentPrompt,
                systemInstruction = systemPrompt,
                taskId = workspaceId,
                taskType = taskType
            ).getOrThrow()
            
            lastResponse = response
            val toolCalls = toolAdapter.parseToolCalls(response)
            
            if (toolCalls.isEmpty()) {
                // No more tools needed, we have a final answer or explanation
                break
            }
            
            // Execute tools and collect results
            val results = mutableListOf<ToolCallResult>()
            for (call in toolCalls) {
                val result = toolAdapter.executeTool(workspaceId, call)
                results.add(result)
            }
            
            // Feedback results to the model
            currentPrompt += "\n\n" + toolAdapter.formatResultsForModel(results)
            turnCount++
        }
        
        // 5. Final Result Mapping (Simplification for now)
        // In a real implementation, we'd extract specific entities like ExecutionPlan or Summary from the lastResponse
        AgentExecutionResult(
            mode = mode,
            responseMessage = lastResponse
        )
    }

    override suspend fun generateContextSummary(workspaceId: String): String {
        val workspace = vibeRepository.getWorkspaceById(workspaceId) ?: return "N/A"
        val context = vibeRepository.getProjectContext(workspaceId).firstOrNull()
        val files = vibeRepository.getFiles(workspaceId).first()
        
        return """
            Workspace: ${workspace.name}
            Stack: ${workspace.techStack}
            Current Progress: ${context?.completedTasks ?: "Initial stage"}
            Total Files: ${files.size}
            Pending Issues: ${context?.knownIssues ?: "None reported"}
        """.trimIndent()
    }
}
