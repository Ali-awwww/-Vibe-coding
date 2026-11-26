package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.AIProviderDao
import com.example.di.DependencyProvider
import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.AuthRepository
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AIModelTeamRepositoryImpl(
    private val context: Context,
    private val authRepository: AuthRepository? = null,
    private val firestore: FirebaseFirestore? = null,
    private val firebaseAuth: FirebaseAuth? = null,
    private val aiProviderDaoOverride: AIProviderDao? = null,
    private val currentUserIdProvider: (() -> String?)? = null,
    private val enableRemoteBackend: Boolean = true
) : AIModelTeamRepository {

    private val localTeamsState = MutableStateFlow<Map<String, ModelTeam>>(emptyMap())
    private val localExecutionsState = MutableStateFlow<Map<String, TeamExecution>>(emptyMap())

    private val activeFirebaseAuth: FirebaseAuth? by lazy {
        if (!enableRemoteBackend) return@lazy null
        firebaseAuth ?: try {
            if (!com.example.util.isFirebaseConfigured(context)) null else FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    private val db: FirebaseFirestore? by lazy {
        if (!enableRemoteBackend) return@lazy null
        firestore ?: try {
            if (!com.example.util.isFirebaseConfigured(context)) {
                return@lazy null
            }
            val dbId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                null
            }
            if (dbId.isNullOrBlank()) {
                null
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private val aiProviderDao: AIProviderDao by lazy {
        aiProviderDaoOverride ?: DependencyProvider.provideDatabase(context).aiProviderDao()
    }

    private fun resolveAuthenticatedUserId(requestedUserId: String? = null): String {
        val activeUserId = currentUserIdProvider?.invoke()?.takeIf { it.isNotBlank() }
            ?: authRepository?.getCurrentUserSync()?.id?.takeIf { it.isNotBlank() }
            ?: activeFirebaseAuth?.currentUser?.uid?.takeIf { it.isNotBlank() }

        return ProjectIdentityGuard.requireAuthenticatedUserId(
            currentUserId = activeUserId,
            requestedUserId = requestedUserId,
            firebaseUserId = if (currentUserIdProvider == null) activeFirebaseAuth?.currentUser?.uid else activeUserId
        )
    }

    override fun getTeams(userId: String): Flow<List<ModelTeam>> = flow {
        val uid = resolveAuthenticatedUserId(userId)
        val firestoreDb = db
        if (firestoreDb != null) {
            val collectionPath = "users/$uid/model_teams"
            emitAll(
                firestoreDb.collection("users").document(uid).collection("model_teams")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { snapshot ->
                        val remoteTeams = snapshot.toObjects(ModelTeam::class.java)
                        localTeamsState.update { current ->
                            val withoutOwner = current.filterValues { it.ownerId != uid }
                            withoutOwner + remoteTeams.associateBy { it.id }
                        }
                        remoteTeams
                    }
                    .catch { error ->
                        handleFirestoreError(error, OperationType.LIST, collectionPath)
                        throw error
                    }
            )
            return@flow
        }

        if (enableRemoteBackend) {
            throw IllegalStateException("No authenticated Firebase backend is available for team data.")
        }

        emitAll(
            localTeamsState.map { map ->
                map.values.filter { it.ownerId == uid }.sortedByDescending { it.createdAt }
            }
        )
    }

    override suspend fun getTeamById(teamId: String): ModelTeam? {
        val uid = resolveAuthenticatedUserId()
        val firestoreDb = db
        if (firestoreDb != null) {
            localTeamsState.value[teamId]?.let { cached ->
                if (cached.ownerId == uid) return cached
                throw SecurityException("Team access denied for the current account.")
            }

            val docPath = "users/$uid/model_teams/$teamId"
            return try {
                val remote = firestoreDb.collection("users").document(uid)
                    .collection("model_teams").document(teamId)
                    .get().await().toObject<ModelTeam>()
                if (remote != null) {
                    if (remote.ownerId.isNotBlank() && remote.ownerId != uid) {
                        throw SecurityException("Team access denied for the current account.")
                    }
                    localTeamsState.update { it + (teamId to remote) }
                }
                remote
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, docPath)
                throw e
            }
        }

        if (!enableRemoteBackend) {
            return localTeamsState.value[teamId]?.also {
                if (it.ownerId != uid) throw SecurityException("Team access denied for the current account.")
            }
        }

        throw IllegalStateException("Team data is unavailable because Firebase Firestore is not configured.")
    }

    override suspend fun createTeam(team: ModelTeam): Result<String> = runCatching {
        val uid = resolveAuthenticatedUserId(
            requestedUserId = team.ownerId.takeIf { it.isNotBlank() }
        )
        val id = if (team.id.isBlank()) UUID.randomUUID().toString() else team.id
        val now = System.currentTimeMillis()
        val finalTeam = team.copy(
            id = id,
            ownerId = uid,
            createdAt = if (team.createdAt > 0L) team.createdAt else now,
            updatedAt = now
        )

        val firestoreDb = db
        val persistedTeam = when {
            firestoreDb != null -> {
                firestoreDb.collection("users").document(uid)
                    .collection("model_teams").document(id)
                    .set(finalTeam)
                    .await()
                finalTeam
            }
            !enableRemoteBackend -> finalTeam
            else -> throw IllegalStateException("Team creation was not saved because Firebase Firestore is unavailable.")
        }

        localTeamsState.update { it + (persistedTeam.id to persistedTeam) }
        persistedTeam.id
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun updateTeam(team: ModelTeam): Result<Unit> = runCatching {
        val uid = resolveAuthenticatedUserId(
            requestedUserId = team.ownerId.takeIf { it.isNotBlank() }
        )
        val existing = localTeamsState.value[team.id]
        if (existing != null && existing.ownerId != uid) {
            throw SecurityException("Cannot modify a team owned by another user.")
        }

        val updated = team.copy(
            ownerId = uid,
            updatedAt = System.currentTimeMillis()
        )

        val firestoreDb = db
        val persistedTeam = when {
            firestoreDb != null -> {
                firestoreDb.collection("users").document(uid)
                    .collection("model_teams").document(team.id)
                    .set(updated)
                    .await()
                updated
            }
            !enableRemoteBackend -> updated
            else -> throw IllegalStateException("Team update was not saved because Firebase Firestore is unavailable.")
        }

        localTeamsState.update { it + (team.id to persistedTeam) }
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun deleteTeam(teamId: String): Result<Unit> = runCatching {
        val uid = resolveAuthenticatedUserId()
        val existing = localTeamsState.value[teamId]
        if (existing != null && existing.ownerId != uid) {
            throw SecurityException("Cannot delete a team owned by another user.")
        }

        val firestoreDb = db
        when {
            firestoreDb != null -> {
                firestoreDb.collection("users").document(uid)
                    .collection("model_teams").document(teamId)
                    .delete()
                    .await()
            }
            !enableRemoteBackend -> Unit
            else -> throw IllegalStateException("Team deletion was not saved because Firebase Firestore is unavailable.")
        }

        localTeamsState.update { it - teamId }
    }.onFailure { if (it is CancellationException) throw it }

    override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> = flow {
        val uid = resolveAuthenticatedUserId()
        val firestoreDb = db
        if (firestoreDb != null) {
            val collectionPath = "users/$uid/team_executions"
            emitAll(
                firestoreDb.collection("users").document(uid).collection("team_executions")
                    .whereEqualTo("teamId", teamId)
                    .orderBy("startedAt", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { snapshot ->
                        val remote = snapshot.toObjects(TeamExecution::class.java)
                        localExecutionsState.update { current ->
                            current + remote.associateBy { it.id }
                        }
                        remote
                    }
                    .catch { error ->
                        handleFirestoreError(error, OperationType.LIST, collectionPath)
                        throw error
                    }
            )
            return@flow
        }

        if (enableRemoteBackend) {
            throw IllegalStateException("Execution history is unavailable because Firebase Firestore is not configured.")
        }

        emitAll(
            localExecutionsState.map { map ->
                map.values.filter { it.teamId == teamId && it.userId == uid }
                    .sortedByDescending { it.startedAt }
            }
        )
    }

    override suspend fun getExecutionById(executionId: String): TeamExecution? {
        val uid = resolveAuthenticatedUserId()
        val firestoreDb = db
        if (firestoreDb != null) {
            localExecutionsState.value[executionId]?.let { cached ->
                if (cached.userId == uid) return cached
                throw SecurityException("Execution access denied for the current account.")
            }

            val docPath = "users/$uid/team_executions/$executionId"
            return try {
                val remote = firestoreDb.collection("users").document(uid)
                    .collection("team_executions").document(executionId)
                    .get().await().toObject<TeamExecution>()
                if (remote != null) {
                    if (remote.userId.isNotBlank() && remote.userId != uid) {
                        throw SecurityException("Execution access denied for the current account.")
                    }
                    localExecutionsState.update { it + (executionId to remote) }
                }
                remote
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, docPath)
                throw e
            }
        }

        if (!enableRemoteBackend) {
            return localExecutionsState.value[executionId]?.also {
                if (it.userId != uid) throw SecurityException("Execution access denied for the current account.")
            }
        }

        throw IllegalStateException("Execution data is unavailable because Firebase Firestore is not configured.")
    }

    override suspend fun logExecution(execution: TeamExecution): Result<Unit> = runCatching {
        val uid = resolveAuthenticatedUserId(
            requestedUserId = execution.userId.takeIf { it.isNotBlank() }
        )
        val id = if (execution.id.isBlank()) UUID.randomUUID().toString() else execution.id
        val finalExec = execution.copy(id = id, userId = uid)

        val firestoreDb = db
        val persistedExecution = when {
            firestoreDb != null -> {
                firestoreDb.collection("users").document(uid)
                    .collection("team_executions").document(id)
                    .set(finalExec)
                    .await()
                finalExec
            }
            !enableRemoteBackend -> finalExec
            else -> throw IllegalStateException("Execution was not saved because Firebase Firestore is unavailable.")
        }

        localExecutionsState.update { it + (id to persistedExecution) }
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit> = runCatching {
        val team = getTeamById(teamId) ?: throw NoSuchElementException("Team not found")
        val memberId = member.id.ifBlank { UUID.randomUUID().toString() }
        val newMembers = team.members + member.copy(id = memberId)
        updateTeam(team.copy(members = newMembers)).getOrThrow()
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit> = runCatching {
        val team = getTeamById(teamId) ?: throw NoSuchElementException("Team not found")
        val newMembers = team.members.filter { it.id != memberId }
        val updatedLeader = if (team.leaderMemberId == memberId) newMembers.firstOrNull()?.id else team.leaderMemberId
        updateTeam(team.copy(members = newMembers, leaderMemberId = updatedLeader)).getOrThrow()
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun updateMemberStatus(
        teamId: String,
        memberId: String,
        status: MemberStatus,
        currentTask: String?,
        lastResult: String?
    ): Result<Unit> = runCatching {
        val team = getTeamById(teamId) ?: throw NoSuchElementException("Team not found")
        val newMembers = team.members.map {
            if (it.id == memberId) {
                it.copy(
                    status = status,
                    currentTask = currentTask ?: it.currentTask,
                    lastResult = lastResult ?: it.lastResult,
                    lastActiveAt = System.currentTimeMillis()
                )
            } else it
        }
        updateTeam(team.copy(members = newMembers)).getOrThrow()
    }.onFailure { if (it is CancellationException) throw it }

    override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> {
        val uid = resolveAuthenticatedUserId()
        return aiProviderDao.getTeamUsageRecords(teamId).map { rows ->
            rows.filter { it.userId == uid }
        }
    }

    override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> {
        val uid = resolveAuthenticatedUserId(userId)
        return aiProviderDao.getAllTeamUsageRecords(uid)
    }

    override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = runCatching {
        val uid = resolveAuthenticatedUserId(
            requestedUserId = record.userId.takeIf { it.isNotBlank() }
        )
        val scopedRecord = record.copy(userId = uid)

        val firestoreDb = db
        if (firestoreDb != null) {
            firestoreDb.collection("users").document(uid)
                .collection("team_usage_records").document(scopedRecord.id)
                .set(scopedRecord)
                .await()
        }

        aiProviderDao.insertTeamUsageRecord(scopedRecord)
    }.onFailure { if (it is CancellationException) throw it }
}
