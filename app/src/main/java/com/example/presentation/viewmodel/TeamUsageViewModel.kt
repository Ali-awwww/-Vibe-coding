package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TeamUsageUiState(
    val team: ModelTeam? = null,
    val usageRecords: List<TeamUsageRecord> = emptyList(),
    val filteredRecords: List<TeamUsageRecord> = emptyList(),
    val memberSummaries: List<MemberUsageSummary> = emptyList(),
    val failoverEvents: List<ModelFailoverEvent> = emptyList(),
    val totalRequests: Int = 0,
    val totalTokens: Int = 0,
    val totalCostUsd: Double = 0.0,
    val rateLimitHits: Int = 0,
    val selectedMemberId: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

class TeamUsageViewModel(
    private val teamId: String,
    private val teamRepository: AIModelTeamRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamUsageUiState())
    val uiState: StateFlow<TeamUsageUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val team = teamRepository.getTeamById(teamId)
            
            combine(
                teamRepository.getTeamUsageRecords(teamId),
                teamRepository.getTeamExecutions(teamId)
            ) { records, executions ->
                val allFailovers = executions.flatMap { it.modelFailovers }
                
                // Aggregate usage per member
                val memberMap = mutableMapOf<String, MemberUsageSummary>()
                
                // Initialize with team members
                team?.members?.forEach { member ->
                    memberMap[member.id] = MemberUsageSummary(
                        memberId = member.id,
                        memberName = member.name,
                        role = member.role,
                        modelId = member.modelId,
                        modelName = member.name,
                        providerId = member.providerId,
                        providerName = member.providerId.substringBefore(":"),
                        requestsCount = 0,
                        inputTokens = 0,
                        outputTokens = 0,
                        totalTokens = 0,
                        costUsd = 0.0,
                        isFreeModel = true,
                        lastActiveTime = member.lastActiveAt
                    )
                }

                // Aggregate from records
                records.forEach { record ->
                    val existing = memberMap[record.memberId]
                    val updated = if (existing != null) {
                        existing.copy(
                            requestsCount = existing.requestsCount + 1,
                            inputTokens = existing.inputTokens + record.tokensInput,
                            outputTokens = existing.outputTokens + record.tokensOutput,
                            totalTokens = existing.totalTokens + record.totalTokens,
                            costUsd = existing.costUsd + record.costUsd,
                            rateLimitHits = existing.rateLimitHits + if (record.status == "RATE_LIMITED") 1 else 0,
                            lastActiveTime = maxOf(existing.lastActiveTime, record.timestamp),
                            quotaStatus = record.quotaRemaining ?: existing.quotaStatus
                        )
                    } else {
                        MemberUsageSummary(
                            memberId = record.memberId,
                            memberName = record.memberName,
                            modelId = record.modelId,
                            modelName = record.modelName,
                            providerId = record.providerId,
                            providerName = record.providerName,
                            requestsCount = 1,
                            inputTokens = record.tokensInput,
                            outputTokens = record.tokensOutput,
                            totalTokens = record.totalTokens,
                            costUsd = record.costUsd,
                            rateLimitHits = if (record.status == "RATE_LIMITED") 1 else 0,
                            isFreeModel = record.isFree,
                            lastActiveTime = record.timestamp,
                            quotaStatus = record.quotaRemaining
                        )
                    }
                    memberMap[record.memberId] = updated
                }

                val totalReq = records.size
                val totalTok = records.sumOf { it.totalTokens }
                val totalCost = records.sumOf { it.costUsd }
                val totalRateLimit = records.count { it.status == "RATE_LIMITED" }

                val filtered = applyFilter(records, _uiState.value.selectedMemberId, _uiState.value.searchQuery)

                _uiState.update { current ->
                    current.copy(
                        team = team,
                        usageRecords = records,
                        filteredRecords = filtered,
                        memberSummaries = memberMap.values.toList(),
                        failoverEvents = allFailovers.sortedByDescending { it.timestamp },
                        totalRequests = totalReq,
                        totalTokens = totalTok,
                        totalCostUsd = totalCost,
                        rateLimitHits = totalRateLimit,
                        isLoading = false
                    )
                }
            }.collect()
        }
    }

    fun selectMemberFilter(memberId: String?) {
        _uiState.update { current ->
            val newSelected = if (current.selectedMemberId == memberId) null else memberId
            current.copy(
                selectedMemberId = newSelected,
                filteredRecords = applyFilter(current.usageRecords, newSelected, current.searchQuery)
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredRecords = applyFilter(current.usageRecords, current.selectedMemberId, query)
            )
        }
    }

    fun updateTeamGovernance(governance: TeamGovernanceSettings) {
        val currentTeam = _uiState.value.team ?: return
        viewModelScope.launch {
            val updated = currentTeam.copy(governance = governance)
            teamRepository.updateTeam(updated)
            _uiState.update { it.copy(team = updated) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun applyFilter(records: List<TeamUsageRecord>, memberId: String?, query: String): List<TeamUsageRecord> {
        return records.filter { record ->
            val matchesMember = memberId == null || record.memberId == memberId
            val matchesQuery = query.isBlank() ||
                    record.taskDescription.contains(query, ignoreCase = true) ||
                    record.modelName.contains(query, ignoreCase = true) ||
                    record.memberName.contains(query, ignoreCase = true)
            matchesMember && matchesQuery
        }
    }
}
