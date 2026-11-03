package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.domain.model.*
import com.example.domain.repository.AIModelTeamRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AIModelTeamRepositoryImpl(private val context: Context) : AIModelTeamRepository {

    private val auth: FirebaseAuth? by lazy {
        try {
            Firebase.auth
        } catch (_: Exception) {
            null
        }
    }
    private fun requireUserId(): String = auth?.currentUser?.uid ?: "usr_dev_master"

    private val localTeams = java.util.concurrent.ConcurrentHashMap<String, ModelTeam>()
    private val localExecutions = java.util.concurrent.ConcurrentHashMap<String, TeamExecution>()

    private val db: FirebaseFirestore? by lazy {
        try {
            if (!com.example.util.isFirebaseConfigured(context)) {
                return@lazy null
            }
            val dbId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                null
            }
            if (dbId.isNullOrBlank()) {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun getTeams(userId: String): Flow<List<ModelTeam>> = flow {
        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                emitAll(
                    firestoreDb.collection("users").document(userId).collection("model_teams")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .snapshots()
                        .map { it.toObjects(ModelTeam::class.java) }
                )
                return@flow
            } catch (_: Exception) {
                // fall through to local
            }
        }
        emit(localTeams.values.filter { it.ownerId == userId }.sortedByDescending { it.createdAt })
    }

    override suspend fun getTeamById(teamId: String): ModelTeam? {
        localTeams[teamId]?.let { return it }
        val uid = requireUserId()
        val firestoreDb = db ?: return null
        return try {
            firestoreDb.collection("users").document(uid).collection("model_teams").document(teamId)
                .get().await().toObject<ModelTeam>()?.also { localTeams[teamId] = it }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun createTeam(team: ModelTeam): Result<String> = runCatching {
        val uid = requireUserId()
        val id = if (team.id.isEmpty()) UUID.randomUUID().toString() else team.id
        val finalTeam = team.copy(id = id, ownerId = uid, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        localTeams[id] = finalTeam
        try {
            db?.collection("users")?.document(uid)?.collection("model_teams")?.document(id)?.set(finalTeam)?.await()
        } catch (_: Exception) {}
        id
    }

    override suspend fun updateTeam(team: ModelTeam): Result<Unit> = runCatching {
        val uid = requireUserId()
        val updated = team.copy(updatedAt = System.currentTimeMillis())
        localTeams[team.id] = updated
        try {
            db?.collection("users")?.document(uid)?.collection("model_teams")?.document(team.id)
                ?.set(updated)
                ?.await()
        } catch (_: Exception) {}
    }

    override suspend fun deleteTeam(teamId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        localTeams.remove(teamId)
        try {
            db?.collection("users")?.document(uid)?.collection("model_teams")?.document(teamId)?.delete()?.await()
        } catch (_: Exception) {}
    }

    override fun getTeamExecutions(teamId: String): Flow<List<TeamExecution>> = flow {
        val uid = requireUserId()
        val firestoreDb = db
        if (firestoreDb != null) {
            try {
                emitAll(
                    firestoreDb.collection("users").document(uid).collection("team_executions")
                        .whereEqualTo("teamId", teamId)
                        .orderBy("startedAt", Query.Direction.DESCENDING)
                        .snapshots()
                        .map { it.toObjects(TeamExecution::class.java) }
                )
                return@flow
            } catch (_: Exception) {
                // fall through to local
            }
        }
        emit(localExecutions.values.filter { it.teamId == teamId }.sortedByDescending { it.startedAt })
    }

    override suspend fun getExecutionById(executionId: String): TeamExecution? {
        localExecutions[executionId]?.let { return it }
        val uid = requireUserId()
        val firestoreDb = db ?: return null
        return try {
            firestoreDb.collection("users").document(uid).collection("team_executions").document(executionId)
                .get().await().toObject<TeamExecution>()?.also { localExecutions[executionId] = it }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun logExecution(execution: TeamExecution): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = if (execution.id.isEmpty()) UUID.randomUUID().toString() else execution.id
        val finalExec = execution.copy(id = id, userId = uid)
        localExecutions[id] = finalExec
        try {
            db?.collection("users")?.document(uid)?.collection("team_executions")?.document(id)?.set(finalExec)?.await()
        } catch (_: Exception) {}
    }

    override suspend fun addTeamMember(teamId: String, member: TeamMember): Result<Unit> = runCatching {
        val team = getTeamById(teamId) ?: throw Exception("Team not found")
        val newMembers = team.members + member.copy(id = UUID.randomUUID().toString())
        updateTeam(team.copy(members = newMembers)).getOrThrow()
    }

    override suspend fun removeTeamMember(teamId: String, memberId: String): Result<Unit> = runCatching {
        val team = getTeamById(teamId) ?: throw Exception("Team not found")
        val newMembers = team.members.filter { it.id != memberId }
        updateTeam(team.copy(members = newMembers)).getOrThrow()
    }

    override suspend fun updateMemberStatus(
        teamId: String,
        memberId: String,
        status: MemberStatus,
        currentTask: String?,
        lastResult: String?
    ): Result<Unit> = runCatching {
        val team = getTeamById(teamId) ?: throw Exception("Team not found")
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
    }

    private val aiProviderDao: com.example.data.local.AIProviderDao by lazy {
        com.example.di.DependencyProvider.provideDatabase(context).aiProviderDao()
    }

    override fun getTeamUsageRecords(teamId: String): Flow<List<TeamUsageRecord>> {
        return aiProviderDao.getTeamUsageRecords(teamId)
    }

    override fun getAllTeamUsageRecords(userId: String): Flow<List<TeamUsageRecord>> {
        return aiProviderDao.getAllTeamUsageRecords(userId)
    }

    override suspend fun logTeamUsage(record: TeamUsageRecord): Result<Unit> = runCatching {
        val uid = requireUserId()
        aiProviderDao.insertTeamUsageRecord(record)
        try {
            db?.collection("users")?.document(uid)?.collection("team_usage_records")?.document(record.id)?.set(record)?.await()
        } catch (_: Exception) {}
    }
}

