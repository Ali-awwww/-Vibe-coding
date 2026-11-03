package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.TaskPlanner
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import java.util.UUID

class TaskPlannerImpl(
    private val aiModelRepository: AIModelRepository
) : TaskPlanner {

    override suspend fun plan(
        userId: String,
        team: ModelTeam,
        prompt: String,
        projectContext: String
    ): Result<List<ExecutionStep>> = runCatching {
        val leader = team.members.find { it.id == team.leaderMemberId } ?: team.members.firstOrNull()
            ?: throw Exception("No team members available for planning")

        val membersInfo = team.members.joinToString("\n") { 
            "- ${it.name} (ID: ${it.id}, Role: ${it.role.labelEn}, Instructions: ${it.instructions})" 
        }

        val planningPrompt = """
            You are the Team Leader of an AI Model Team.
            
            PROJECT CONTEXT:
            $projectContext
            
            TEAM MEMBERS:
            $membersInfo
            
            USER REQUEST:
            $prompt
            
            TASK:
            Break down the user request into a set of specialized sub-tasks assigned to the appropriate team members.
            Identify dependencies between tasks (e.g., Task B cannot start until Task A is finished).
            
            FORMAT:
            Return a list of steps in the following pipe-separated format (one per line):
            MEMBER_ID|TASK_TITLE|TASK_DESCRIPTION|DEPENDS_ON_IDS(comma separated or NONE)
            
            Example:
            uuid-1|Architecture Design|Design the database schema|NONE
            uuid-2|API Implementation|Implement the CRUD endpoints|uuid-1
        """.trimIndent()

        val response = aiModelRepository.generateResponse(
            userId = userId,
            modelId = leader.modelId,
            prompt = planningPrompt,
            taskType = AITaskType.VIBE_AGENT_PLANNING
        )

        val text = response.getOrThrow()
        val steps = text.lines()
            .filter { it.contains("|") }
            .map { line ->
                val parts = line.split("|")
                val memberId = parts.getOrNull(0)?.trim() ?: ""
                val title = parts.getOrNull(1)?.trim() ?: "Sub-task"
                val taskDesc = parts.getOrNull(2)?.trim() ?: "Perform assigned role task"
                val deps = parts.getOrNull(3)?.trim()?.let { 
                    if (it.uppercase() == "NONE") emptyList() else it.split(",").map { id -> id.trim() }
                } ?: emptyList()

                val member = team.members.find { it.id == memberId } ?: leader
                
                ExecutionStep(
                    id = memberId + "_" + UUID.randomUUID().toString().take(4), // Semi-deterministic ID for planning ref
                    memberId = member.id,
                    memberName = member.name,
                    role = member.role.labelEn,
                    task = "$title: $taskDesc",
                    status = StepStatus.PENDING,
                    input = prompt,
                    dependsOnIds = deps
                )
            }
        
        if (steps.isEmpty()) throw Exception("Failed to generate a valid plan from leader response")
        steps
    }
}
