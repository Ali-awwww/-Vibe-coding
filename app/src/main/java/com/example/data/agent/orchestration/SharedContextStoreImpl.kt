package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.SharedContextStore
import com.example.domain.model.*
import java.util.UUID

class SharedContextStoreImpl : SharedContextStore {

    override fun prepareAgentContext(
        member: TeamMember,
        execution: TeamExecution,
        currentStep: ExecutionStep
    ): String {
        val sb = StringBuilder()
        
        sb.append("YOUR ROLE: ${member.role.labelEn}\n")
        sb.append("SPECIFIC INSTRUCTIONS: ${member.instructions}\n\n")
        
        sb.append("SHARED TEAM MEMORY:\n")
        execution.sharedMemory.entries.takeLast(10).forEach { entry ->
            sb.append("- [${entry.type.name}] from Member ${entry.sourceMemberId}: ${entry.content}\n")
        }
        
        if (execution.sharedMemory.approvedDecisions.isNotEmpty()) {
            sb.append("\nAPPROVED DECISIONS:\n")
            execution.sharedMemory.approvedDecisions.forEach { sb.append("- $it\n") }
        }
        
        // Include output of dependent tasks
        val dependencies = execution.steps.filter { currentStep.dependsOnIds.contains(it.id) }
        if (dependencies.isNotEmpty()) {
            sb.append("\nPREREQUISITE TASK RESULTS:\n")
            dependencies.forEach { dep ->
                sb.append("### Task: ${dep.task}\n")
                sb.append("Result: ${dep.output ?: "No output"}\n\n")
            }
        }
        
        sb.append("CURRENT ASSIGNMENT: ${currentStep.task}\n")
        
        return sb.toString()
    }

    override fun updateMemory(
        execution: TeamExecution,
        member: TeamMember,
        output: String,
        type: MemoryType
    ): SharedMemory {
        // Logic to extract facts/decisions would ideally use another AI pass,
        // but for now, we treat the output as a set of facts/suggestions.
        
        val newEntry = MemoryEntry(
            id = UUID.randomUUID().toString(),
            sourceMemberId = member.id,
            content = output.take(500) + if (output.length > 500) "..." else "",
            type = type
        )
        
        return execution.sharedMemory.copy(
            entries = execution.sharedMemory.entries + newEntry
        )
    }
}
