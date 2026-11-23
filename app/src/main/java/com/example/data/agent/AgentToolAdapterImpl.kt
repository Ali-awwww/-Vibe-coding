package com.example.data.agent

import com.example.domain.agent.*
import com.example.domain.model.*
import com.example.domain.repository.VibeStudioRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.firstOrNull
import java.util.*

class AgentToolAdapterImpl(
    private val vibeRepositoryProvider: () -> VibeStudioRepository
) : AgentToolAdapter {
    
    private val vibeRepository: VibeStudioRepository get() = vibeRepositoryProvider()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    override suspend fun executeTool(workspaceId: String, request: ToolCallRequest): ToolCallResult {
        return try {
            val output = when (request.toolName) {
                "read_file" -> {
                    val path = request.arguments["path"] as? String ?: throw Exception("Path is required")
                    val file = vibeRepository.getFileByPath(workspaceId, path)
                    file?.content ?: "Error: File not found at $path"
                }
                "write_file" -> {
                    val path = request.arguments["path"] as? String ?: throw Exception("Path is required")
                    val content = request.arguments["content"] as? String ?: ""
                    val existing = vibeRepository.getFileByPath(workspaceId, path)
                    val file = existing?.copy(content = content) ?: WorkspaceFile(
                        id = UUID.randomUUID().toString(),
                        workspaceId = workspaceId,
                        name = path.substringAfterLast("/"),
                        path = path,
                        content = content,
                        extension = path.substringAfterLast(".", "")
                    )
                    vibeRepository.saveFile(file)
                    "Success: File written to $path"
                }
                "list_dir" -> {
                    val path = request.arguments["path"] as? String ?: ""
                    val files = vibeRepository.getFiles(workspaceId).firstOrNull() ?: emptyList()
                    val children = if (path.isEmpty()) {
                        files.filter { !it.path.contains("/") }
                    } else {
                        files.filter { it.path.startsWith("$path/") && !it.path.removePrefix("$path/").contains("/") }
                    }
                    children.joinToString("\n") { (if (it.isFolder) "[DIR] " else "      ") + it.path }
                }
                "search_files" -> {
                    val query = request.arguments["query"] as? String ?: ""
                    val results = vibeRepository.searchFiles(workspaceId, query)
                    results.joinToString("\n") { it.path }
                }
                "run_command" -> {
                    val command = request.arguments["command"] as? String ?: throw Exception("Command is required")
                    val result = vibeRepository.executeTerminalCommand(workspaceId, command).getOrThrow()
                    "Exit Code: ${result.exitCode}\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}"
                }
                "run_tests" -> {
                    val result = vibeRepository.runWorkspaceTests(workspaceId).getOrThrow()
                    val summary = if (result.executionVerified) {
                        "Executed tests: ${result.passed}/${result.totalTests} passed. Success: ${result.isSuccess}"
                    } else {
                        "Runtime tests were not executed. Static checks: ${result.staticChecksPassed}/${result.staticChecksTotal} passed; ${result.staticChecksFailed} failed. This is not evidence of build or test success."
                    }
                    "$summary\nLogs:\n${result.outputLogs.joinToString("\n")}"
                }
                "get_build_logs" -> {
                    val logs = vibeRepository.getBuildLogs(workspaceId).firstOrNull() ?: emptyList()
                    logs.takeLast(20).joinToString("\n") { "[${it.type}] ${it.message}" }
                }
                "git_diff" -> {
                    val diffs = vibeRepository.getWorkingTreeDiff(workspaceId).getOrThrow()
                    diffs.joinToString("\n\n") { diff ->
                        "File: ${diff.path} (${diff.changeType})\n" +
                        diff.diffLines.joinToString("\n") { line ->
                            val prefix = when(line.type) {
                                DiffLineType.ADD -> "+"
                                DiffLineType.REMOVE -> "-"
                                DiffLineType.CONTEXT -> " "
                            }
                            "$prefix ${line.content}"
                        }
                    }
                }
                "create_checkpoint" -> {
                    val title = request.arguments["title"] as? String ?: "Agent Checkpoint"
                    val desc = request.arguments["description"] as? String ?: ""
                    val id = vibeRepository.createCheckpoint(workspaceId, title, desc)
                    "Success: Checkpoint created with ID: $id"
                }
                "restore_checkpoint" -> {
                    val id = request.arguments["checkpoint_id"] as? String ?: throw Exception("ID required")
                    vibeRepository.revertToCheckpoint(id).getOrThrow()
                    "Success: Restored to checkpoint $id"
                }
                else -> throw Exception("Unknown tool: ${request.toolName}")
            }
            ToolCallResult(request.id, request.toolName, output)
        } catch (e: Exception) {
            ToolCallResult(request.id, request.toolName, "Error: ${e.message}", isError = true)
        }
    }

    override fun getAvailableTools(taskType: AITaskType): List<AgentTool> {
        val tools = mutableListOf(
            AgentTool("read_file", "Reads content of a file", mapOf("path" to ToolParameter("string", "Path to the file", true))),
            AgentTool("write_file", "Writes content to a file", mapOf("path" to ToolParameter("string", "Path", true), "content" to ToolParameter("string", "Content", true))),
            AgentTool("list_dir", "Lists files in a directory", mapOf("path" to ToolParameter("string", "Path (empty for root)"))),
            AgentTool("search_files", "Searches for files by name or content", mapOf("query" to ToolParameter("string", "Search query", true))),
            AgentTool("get_build_logs", "Gets recent build and terminal logs")
        )
        
        if (taskType == AITaskType.VIBE_AGENT_CODING) {
            tools.add(AgentTool("run_command", "Executes a terminal command in the sandbox", mapOf("command" to ToolParameter("string", "Shell command", true))))
            tools.add(AgentTool("run_tests", "Runs all project tests"))
            tools.add(AgentTool("git_diff", "Shows current uncommitted changes"))
            tools.add(AgentTool("create_checkpoint", "Saves a point in time to revert to later", mapOf("title" to ToolParameter("string", "Checkpoint name"))))
            tools.add(AgentTool("restore_checkpoint", "Reverts the project to a previous state", mapOf("checkpoint_id" to ToolParameter("string", "Target checkpoint ID", true))))
        }
        
        return tools
    }

    override fun parseToolCalls(response: String): List<ToolCallRequest> {
        // Fallback parser for models without native function calling
        // Looks for JSON blocks or specific markdown markers
        val toolCalls = mutableListOf<ToolCallRequest>()
        
        // Strategy 1: Look for JSON blocks ```json ... ```
        val jsonRegex = Regex("```json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
        jsonRegex.findAll(response).forEach { match ->
            try {
                val json = match.groupValues[1]
                val requestMap = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
                if (requestMap?.get("tool") != null) {
                    toolCalls.add(ToolCallRequest(
                        id = UUID.randomUUID().toString(),
                        toolName = requestMap["tool"] as String,
                        arguments = (requestMap["args"] as? Map<String, Any?>) ?: emptyMap()
                    ))
                }
            } catch (e: Exception) {
                // Ignore invalid JSON blocks
            }
        }
        
        // Strategy 2: Look for specific text patterns like "CALL: tool_name(args)"
        if (toolCalls.isEmpty()) {
             val callRegex = Regex("CALL:\\s*(\\w+)\\((.*?)\\)")
             callRegex.findAll(response).forEach { match ->
                 val name = match.groupValues[1]
                 val argsStr = match.groupValues[2]
                 // Very simple arg parser (could be improved)
                 val args = argsStr.split(",").associate { 
                     val parts = it.split("=")
                     if (parts.size == 2) parts[0].trim() to parts[1].trim().removeSurrounding("'").removeSurrounding("\"")
                     else "" to ""
                 }.filterKeys { it.isNotEmpty() }
                 
                 toolCalls.add(ToolCallRequest(UUID.randomUUID().toString(), name, args))
             }
        }
        
        return toolCalls
    }

    override fun formatResultsForModel(results: List<ToolCallResult>): String {
        return results.joinToString("\n\n") { 
            "TOOL_RESULT: ${it.toolName} (ID: ${it.callId})\n${if (it.isError) "ERROR: " else ""}${it.output}"
        }
    }
}
