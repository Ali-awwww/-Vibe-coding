package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TeamUsagePeriod(val labelAr: String, val durationMillis: Long?) {
    LAST_7_DAYS("7 أيام", 7L * 24 * 60 * 60 * 1000),
    LAST_30_DAYS("30 يوماً", 30L * 24 * 60 * 60 * 1000),
    ALL_TIME("كل الفترة", null)
}

data class TeamUsageUiState(
    val userId: String? = null,
    val isAuthenticated: Boolean = false,
    val team: ModelTeam? = null,
    val usageRecords: List<TeamUsageRecord> = emptyList(),
    val filteredRecords: List<TeamUsageRecord> = emptyList(),
    val memberSummaries: List<MemberUsageSummary> = emptyList(),
    val failoverEvents: List<ModelFailoverEvent> = emptyList(),
    val totalRequests: Int = 0,
    val totalTokens: Int = 0,
    val totalCostUsd: Double = 0.0,
    val unconfirmedCostUsd: Double = 0.0,
    val hasMeasuredUsage: Boolean = false,
    val hasConfirmedCost: Boolean = false,
    val hasPaidUsageUnderFreeOnly: Boolean = false,
    val lastMeasuredAt: Long? = null,
    val rateLimitHits: Int = 0,
    val selectedMemberId: String? = null,
    val selectedPeriod: TeamUsagePeriod = TeamUsagePeriod.LAST_30_DAYS,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val loadError: String? = null,
    val error: String? = null
)

private sealed interface TeamUsageLoadResult {
    data class Loading(val userId: String) : TeamUsageLoadResult
    data object Unauthenticated : TeamUsageLoadResult
    data class Loaded(
        val userId: String,
        val team: ModelTeam?,
        val records: List<TeamUsageRecord>,
        val executions: List<TeamExecution>
    ) : TeamUsageLoadResult
    data class Failed(val cause: Throwable) : TeamUsageLoadResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class TeamUsageViewModel(
    private val teamId: String,
    private val teamRepository: AIModelTeamRepository,
    private val authRepository: AuthRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    companion object {
        const val AUTH_REQUIRED_MESSAGE = "يجب تسجيل الدخول لعرض بيانات استهلاك الفريق."
        private const val INVALID_TEAM_MESSAGE = "معرف الفريق غير صالح."
        private const val TEAM_NOT_FOUND_MESSAGE = "الفريق غير متاح لهذا الحساب أو لم يعد موجوداً."
    }

    private val _uiState = MutableStateFlow(TeamUsageUiState())
    val uiState: StateFlow<TeamUsageUiState> = _uiState.asStateFlow()
    private val currentUser = authRepository.currentUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var mutationJob: Job? = null
    private var allFailoverEvents: List<ModelFailoverEvent> = emptyList()

    init {
        if (teamId.isBlank()) {
            _uiState.value = TeamUsageUiState(isLoading = false, loadError = INVALID_TEAM_MESSAGE)
        } else {
            observeUsage()
        }
    }

    private fun observeUsage() {
        viewModelScope.launch {
            authRepository.currentUser
                .distinctUntilChanged()
                .flatMapLatest { account ->
                    val userId = account?.id?.trim()?.takeIf(String::isNotEmpty)
                    if (userId == null) {
                        flowOf(TeamUsageLoadResult.Unauthenticated)
                    } else {
                        refreshRequests
                            .onStart { emit(Unit) }
                            .flatMapLatest { loadUsageFlow(userId) }
                    }
                }
                .collect(::applyLoadResult)
        }
    }

    private fun loadUsageFlow(userId: String): Flow<TeamUsageLoadResult> =
        combine(
            teamRepository.getTeams(userId).map { teams -> teams.firstOrNull { it.id == teamId } },
            teamRepository.getTeamUsageRecords(teamId),
            teamRepository.getTeamExecutions(teamId)
        ) { team, records, executions ->
            val result: TeamUsageLoadResult = TeamUsageLoadResult.Loaded(userId, team, records, executions)
            result
        }
            .onStart { emit(TeamUsageLoadResult.Loading(userId)) }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                emit(TeamUsageLoadResult.Failed(cause))
            }

    private fun applyLoadResult(result: TeamUsageLoadResult) {
        when (result) {
            is TeamUsageLoadResult.Loading -> {
                val accountChanged = _uiState.value.userId != result.userId
                if (accountChanged) {
                    mutationJob?.cancel()
                    mutationJob = null
                    allFailoverEvents = emptyList()
                }
                _uiState.update { current ->
                    current.copy(
                        userId = result.userId,
                        isAuthenticated = true,
                        isLoading = true,
                        team = if (accountChanged) null else current.team,
                        usageRecords = if (accountChanged) emptyList() else current.usageRecords,
                        filteredRecords = if (accountChanged) emptyList() else current.filteredRecords,
                        memberSummaries = if (accountChanged) emptyList() else current.memberSummaries,
                        failoverEvents = if (accountChanged) emptyList() else current.failoverEvents,
                        hasMeasuredUsage = if (accountChanged) false else current.hasMeasuredUsage,
                        hasConfirmedCost = if (accountChanged) false else current.hasConfirmedCost,
                        selectedMemberId = if (accountChanged) null else current.selectedMemberId,
                        searchQuery = if (accountChanged) "" else current.searchQuery,
                        selectedPeriod = if (accountChanged) TeamUsagePeriod.LAST_30_DAYS else current.selectedPeriod,
                        loadError = null,
                        error = null
                    )
                }
            }

            TeamUsageLoadResult.Unauthenticated -> {
                mutationJob?.cancel()
                mutationJob = null
                allFailoverEvents = emptyList()
                _uiState.value = TeamUsageUiState(isAuthenticated = false, isLoading = false, loadError = AUTH_REQUIRED_MESSAGE)
            }

            is TeamUsageLoadResult.Loaded -> {
                if (result.team == null) {
                    allFailoverEvents = emptyList()
                    _uiState.value = TeamUsageUiState(
                        userId = result.userId,
                        isAuthenticated = true,
                        isLoading = false,
                        loadError = TEAM_NOT_FOUND_MESSAGE
                    )
                } else {
                    allFailoverEvents = result.executions.flatMap { it.modelFailovers }
                    val base = _uiState.value.copy(
                        userId = result.userId,
                        isAuthenticated = true,
                        team = result.team,
                        usageRecords = result.records.sortedByDescending { it.timestamp },
                        isLoading = false,
                        loadError = null,
                        error = null
                    )
                    _uiState.value = deriveMetrics(base)
                }
            }

            is TeamUsageLoadResult.Failed -> {
                val message = userFacingError(result.cause, "تعذر تحميل بيانات الاستهلاك. حاول إعادة المحاولة.")
                _uiState.update { current ->
                    current.copy(
                        isAuthenticated = true,
                        isLoading = false,
                        loadError = if (current.team == null) message else null,
                        error = if (current.team != null) message else null
                    )
                }
            }
        }
    }

    private fun deriveMetrics(current: TeamUsageUiState): TeamUsageUiState {
        val periodRecords = filterByPeriod(current.usageRecords, current.selectedPeriod)
        val billableRecords = periodRecords.filter { it.hasBillableUsage() }
        val summaries = aggregateMembers(billableRecords)
        val now = clock()
        val periodFailovers = allFailoverEvents
            .filter { current.selectedPeriod.includes(it.timestamp, now) }
            .sortedByDescending { it.timestamp }

        return current.copy(
            filteredRecords = applyFilter(periodRecords, current.selectedMemberId, current.searchQuery),
            memberSummaries = summaries,
            failoverEvents = periodFailovers,
            totalRequests = periodRecords.size,
            totalTokens = billableRecords.sumOf { it.totalTokens },
            totalCostUsd = billableRecords.filter { it.isCostConfirmed }.sumOf { it.costUsd },
            unconfirmedCostUsd = billableRecords.filterNot { it.isCostConfirmed }.sumOf { it.costUsd },
            hasMeasuredUsage = billableRecords.isNotEmpty(),
            hasConfirmedCost = billableRecords.any { it.isCostConfirmed },
            hasPaidUsageUnderFreeOnly = current.team?.governance?.freeOnly == true && billableRecords.any { !it.isFree },
            lastMeasuredAt = billableRecords.maxOfOrNull { it.timestamp },
            rateLimitHits = periodRecords.count { it.status == "RATE_LIMITED" }
        )
    }

    private fun filterByPeriod(
        records: List<TeamUsageRecord>,
        period: TeamUsagePeriod
    ): List<TeamUsageRecord> {
        val now = clock()
        val cutoff = period.durationMillis?.let { now - it }
        return records.filter { record ->
            record.timestamp <= now && (cutoff == null || record.timestamp >= cutoff)
        }.sortedByDescending { it.timestamp }
    }

    private fun aggregateMembers(records: List<TeamUsageRecord>): List<MemberUsageSummary> {
        val summaries = linkedMapOf<String, MemberUsageSummary>()
        records.forEach { record ->
            val key = record.memberId.ifBlank { "${record.memberName}|${record.modelId}|${record.providerId}" }
            val existing = summaries[key]
            if (existing == null) {
                val role = TeamRole.values().firstOrNull {
                    it.name == record.roleName || it.labelAr == record.roleName || it.labelEn == record.roleName
                } ?: TeamRole.CUSTOM
                summaries[key] = MemberUsageSummary(
                    memberId = record.memberId,
                    memberName = record.memberName,
                    role = role,
                    modelId = record.modelId,
                    modelName = record.modelName,
                    providerId = record.providerId,
                    providerName = record.providerName,
                    requestsCount = 1,
                    inputTokens = record.tokensInput,
                    outputTokens = record.tokensOutput,
                    totalTokens = record.totalTokens,
                    costUsd = if (record.isCostConfirmed) record.costUsd else 0.0,
                    isFreeModel = record.isFree,
                    isCostConfirmed = record.isCostConfirmed,
                    rateLimitHits = if (record.status == "RATE_LIMITED") 1 else 0,
                    quotaStatus = record.quotaRemaining,
                    lastActiveTime = record.timestamp
                )
            } else {
                val modelName = when {
                    existing.modelName.isBlank() -> record.modelName
                    record.modelName.isBlank() || existing.modelName == record.modelName -> existing.modelName
                    else -> "عدة نماذج"
                }
                val providerName = when {
                    existing.providerName.isBlank() -> record.providerName
                    record.providerName.isBlank() || existing.providerName == record.providerName -> existing.providerName
                    else -> "عدة مزودين"
                }
                summaries[key] = existing.copy(
                    modelName = modelName,
                    providerName = providerName,
                    requestsCount = existing.requestsCount + 1,
                    inputTokens = existing.inputTokens + record.tokensInput,
                    outputTokens = existing.outputTokens + record.tokensOutput,
                    totalTokens = existing.totalTokens + record.totalTokens,
                    costUsd = existing.costUsd + if (record.isCostConfirmed) record.costUsd else 0.0,
                    isFreeModel = existing.isFreeModel && record.isFree,
                    isCostConfirmed = existing.isCostConfirmed && record.isCostConfirmed,
                    rateLimitHits = existing.rateLimitHits + if (record.status == "RATE_LIMITED") 1 else 0,
                    lastActiveTime = maxOf(existing.lastActiveTime, record.timestamp),
                    quotaStatus = record.quotaRemaining ?: existing.quotaStatus
                )
            }
        }
        return summaries.values.sortedByDescending { it.totalTokens }
    }

    private fun applyFilter(
        records: List<TeamUsageRecord>,
        memberId: String?,
        query: String
    ): List<TeamUsageRecord> = records.filter { record ->
        val matchesMember = memberId == null || record.memberId == memberId
        val normalizedQuery = query.trim()
        val matchesQuery = normalizedQuery.isBlank() ||
            record.taskDescription.contains(normalizedQuery, ignoreCase = true) ||
            record.modelName.contains(normalizedQuery, ignoreCase = true) ||
            record.memberName.contains(normalizedQuery, ignoreCase = true)
        matchesMember && matchesQuery
    }

    fun selectMemberFilter(memberId: String?) {
        _uiState.update { current ->
            val selected = if (current.selectedMemberId == memberId) null else memberId
            deriveMetrics(current.copy(selectedMemberId = selected))
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current -> deriveMetrics(current.copy(searchQuery = query)) }
    }

    fun updatePeriod(period: TeamUsagePeriod) {
        _uiState.update { current ->
            deriveMetrics(current.copy(selectedPeriod = period, selectedMemberId = null))
        }
    }

    fun refresh() {
        if (!_uiState.value.isAuthenticated) {
            _uiState.update { it.copy(error = AUTH_REQUIRED_MESSAGE) }
            return
        }
        _uiState.update { it.copy(isLoading = true, loadError = null, error = null) }
        refreshRequests.tryEmit(Unit)
    }

    fun updateTeamGovernance(
        governance: TeamGovernanceSettings,
        onSaved: () -> Unit = {}
    ) {
        val userId = currentUser.value?.id?.trim()?.takeIf(String::isNotEmpty)
        if (userId == null) {
            _uiState.update { it.copy(error = AUTH_REQUIRED_MESSAGE) }
            return
        }
        val currentTeam = _uiState.value.team ?: return
        if (_uiState.value.isMutating) return

        mutationJob = viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, error = null) }
            try {
                val updated = currentTeam.copy(governance = governance)
                teamRepository.updateTeam(updated).getOrThrow()
                _uiState.update { it.copy(team = updated) }
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                _uiState.update {
                    it.copy(error = userFacingError(cause, "تعذر حفظ سياسة الفريق. لم يتم تطبيق التغيير."))
                }
            } finally {
                _uiState.update { it.copy(isMutating = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun userFacingError(cause: Throwable, fallback: String): String = when (cause) {
        is SecurityException -> "لا تملك صلاحية الوصول إلى بيانات هذا الفريق."
        is NoSuchElementException -> TEAM_NOT_FOUND_MESSAGE
        is IOException -> "تعذر الاتصال بالخدمة. تحقق من الاتصال ثم أعد المحاولة."
        else -> fallback
    }
}

private fun TeamUsageRecord.hasBillableUsage(): Boolean =
    status == "SUCCESS" || totalTokens > 0 || costUsd > 0.0

private fun TeamUsagePeriod.includes(timestamp: Long, now: Long): Boolean {
    val cutoff = durationMillis?.let { now - it }
    return timestamp <= now && (cutoff == null || timestamp >= cutoff)
}
