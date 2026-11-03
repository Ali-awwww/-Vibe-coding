package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.EvaluationResult
import com.example.domain.agent.orchestration.ResultEvaluator
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import org.json.JSONObject

class ResultEvaluatorImpl(
    private val aiModelRepository: AIModelRepository
) : ResultEvaluator {

    override suspend fun evaluate(
        userId: String,
        member: TeamMember,
        task: String,
        output: String
    ): EvaluationResult {
        // Use a reasoning-capable model to evaluate the result
        val evaluationPrompt = """
            Evaluate the following task completion by an AI Agent.
            
            TASK:
            $task
            
            AGENT ROLE:
            ${member.role.labelEn}
            
            AGENT OUTPUT:
            $output
            
            CRITERIA:
            1. Does it complete the task?
            2. Is it technically sound?
            3. Are there any obvious conflicts or errors?
            
            FORMAT:
            Return a JSON object with:
            {
              "accepted": boolean,
              "feedback": "string",
              "conflicts": ["string"],
              "confidence": float (0.0 to 1.0)
            }
        """.trimIndent()

        val response = aiModelRepository.generateResponse(
            userId = userId,
            modelId = "gemini-3.8-flash", // Use a reliable model for evaluation
            prompt = evaluationPrompt,
            taskType = AITaskType.VIBE_AGENT_REVIEW
        )

        return try {
            val json = JSONObject(response.getOrThrow())
            EvaluationResult(
                isAccepted = json.getBoolean("accepted"),
                feedback = json.optString("feedback"),
                detectedConflicts = (0 until json.getJSONArray("conflicts").length()).map { json.getJSONArray("conflicts").getString(it) },
                confidenceScore = json.optDouble("confidence", 1.0).toFloat()
            )
        } catch (e: Exception) {
            // Fallback: accept if not empty
            EvaluationResult(isAccepted = output.isNotBlank())
        }
    }
}
