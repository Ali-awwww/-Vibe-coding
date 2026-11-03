package com.example.domain.agent

import com.example.domain.model.AITaskType

/**
 * Represents a tool that the AI Agent can use.
 */
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter> = emptyMap(),
    val requiredPermissions: List<String> = emptyList()
)

data class ToolParameter(
    val type: String, // string, number, boolean, array, object
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null
)

/**
 * Represents a request from the AI to call a tool.
 */
data class ToolCallRequest(
    val id: String,
    val toolName: String,
    val arguments: Map<String, Any?>
)

/**
 * Represents the result of a tool execution.
 */
data class ToolCallResult(
    val callId: String,
    val toolName: String,
    val output: String,
    val isError: Boolean = false
)

/**
 * Adapter interface to bridge AI model outputs with actual tool executions.
 */
interface AgentToolAdapter {
    /**
     * Executes a tool call request within the context of a workspace.
     */
    suspend fun executeTool(workspaceId: String, request: ToolCallRequest): ToolCallResult

    /**
     * Returns the list of available tools for a given task type.
     */
    fun getAvailableTools(taskType: AITaskType): List<AgentTool>
    
    /**
     * Parses a structured text response (e.g. JSON or Markdown) into tool calls 
     * for models that don't support native function calling.
     */
    fun parseToolCalls(response: String): List<ToolCallRequest>
    
    /**
     * Formats tool call results back into a prompt for the model.
     */
    fun formatResultsForModel(results: List<ToolCallResult>): String
}
