package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.CrashDao
import com.example.data.remote.supabase.SupabaseProjectDataSource
import com.example.data.remote.*
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.CrashRepository
import com.example.util.OperationType
import com.example.util.SecurityUtils
import com.example.util.handleFirestoreError
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.UUID

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObjects
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import android.util.Log

class CrashRepositoryImpl(
    private val firestore: FirebaseFirestore?,
    private val crashDao: CrashDao,
    private val sentryApi: SentryApi,
    private val bugsnagApi: BugsnagApi,
    private val githubRepository: com.example.domain.repository.GitHubRepository,
    private val symbolicationRepository: com.example.domain.repository.SymbolicationRepository,
    private val aiModelRepository: com.example.domain.repository.AIModelRepository,
    private val auth: FirebaseAuth? = null,
    private val authRepository: AuthRepository? = null,
    private val supabaseProjectDataSource: SupabaseProjectDataSource? = null,
    private val currentUserIdProvider: (() -> String?)? = null
) : CrashRepository {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private fun requireUserId(): String = try {
        currentUserIdProvider?.invoke()?.takeIf { it.isNotBlank() }
            ?: auth?.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Authentication is required for cloud crash data operations.")
    } catch (error: IllegalStateException) {
        throw error
    } catch (error: Exception) {
        throw IllegalStateException("Unable to verify the authenticated user.", error)
    }

    private fun requireProjectUserId(requestedUserId: String? = null): String {
        val uid = ProjectIdentityGuard.requireSupabaseUserId(
            currentUserId = authRepository?.getCurrentUserSync()?.id,
            requestedUserId = requestedUserId
        )
        if (firestore != null) {
            ProjectIdentityGuard.requireLegacyFirebaseIdentityMatch(
                supabaseUserId = uid,
                firebaseUserId = auth?.currentUser?.uid
            )
        }
        return uid
    }

    private suspend fun currentUserProjectIds(): Set<String> {
        val userId = requireProjectUserId()
        return getProjectsForUser(userId).first().map { it.id }.toSet()
    }

    // Project Operations
    override fun getProjects(): Flow<List<Project>> = flow {
        val currentUserId = authRepository?.getCurrentUserSync()?.id
            ?.takeIf { it.isNotBlank() }
        if (currentUserId == null) {
            emit(emptyList())
            return@flow
        }

        val uid = requireProjectUserId(currentUserId)
        supabaseProjectDataSource?.let {
            emit(it.getProjectsForUser(uid))
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            emitAll(
                fs.collection("users").document(uid).collection("projects")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { it.toObjects(Project::class.java) }
            )
        } else {
            emitAll(crashDao.getProjectsForUser(uid))
        }
    }

    override fun getProjectsForUser(userId: String): Flow<List<Project>> = flow {
        val uid = requireProjectUserId(userId)
        supabaseProjectDataSource?.let {
            emit(it.getProjectsForUser(uid))
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            emitAll(
                fs.collection("users").document(uid).collection("projects")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { it.toObjects(Project::class.java) }
            )
        } else {
            emitAll(crashDao.getProjectsForUser(uid))
        }
    }

    override suspend fun getProjectById(projectId: String): Project? {
        val uid = requireProjectUserId()
        supabaseProjectDataSource?.let {
            val project = it.getProjectById(projectId)
            project?.let { row -> crashDao.insertProject(row) }
            return project
        }
        val fs = firestore
        if (fs != null) {
            val remoteProject = fs.collection("users").document(uid).collection("projects")
                .document(projectId).get().await().toObject<Project>()
            if (remoteProject != null) return remoteProject
        }
        return crashDao.getProjectsForUser(uid).first().firstOrNull { it.id == projectId }
    }

    override suspend fun addProject(project: Project) {
        val uid = requireProjectUserId()
        if (project.ownerId.isNotBlank() && project.ownerId != uid) {
            throw SecurityException("A project cannot be created for another account.")
        }
        val id = if (project.id.isEmpty()) UUID.randomUUID().toString() else project.id
        val newProject = project.copy(id = id, ownerId = uid)
        supabaseProjectDataSource?.let {
            crashDao.insertProject(it.createProject(newProject))
            return
        }

        firestore?.let { fs ->
            val projectPath = "users/$uid/projects/$id"
            val projectRef = fs.collection("users").document(uid).collection("projects").document(id)
            try {
                projectRef.set(newProject).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, projectPath)
                throw e
            }

            val auditRef = fs.collection("users").document(uid).collection("audit_logs").document()
            try {
                auditRef.set(mapOf(
                    "id" to auditRef.id,
                    "projectId" to id,
                    "userId" to uid,
                    "action" to "PROJECT_CREATED",
                    "details" to "Project ${newProject.name} created",
                    "timestamp" to FieldValue.serverTimestamp()
                )).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, auditRef.path)
            }
        }

        crashDao.insertProject(newProject)
    }


    override suspend fun deleteProject(projectId: String, callerUserId: String): Result<Unit> = runCatching {
        val uid = requireProjectUserId(callerUserId)
        val project = getProjectById(projectId) ?: throw Exception("Project not found")
        // Note: Security rules enforce that only the owner can delete, but we check here too.
        if (project.ownerId != uid) throw SecurityException("غير مصرح لك بحذف هذا المشروع.")
        supabaseProjectDataSource?.let {
            it.deleteProject(projectId)
            crashDao.deleteProject(projectId)
            return@runCatching
        }

        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(projectId)?.delete()?.await()
        
        crashDao.deleteProject(projectId)
        
        firestore?.collection("users")?.document(uid)?.collection("audit_logs")?.document()?.let { auditRef ->
            auditRef.set(mapOf(
                "id" to auditRef.id,
                "projectId" to projectId,
                "userId" to uid,
                "action" to "PROJECT_DELETED",
                "details" to "Project ${project.name} deleted",
                "timestamp" to FieldValue.serverTimestamp()
            )).await()
        }
    }

    // Application Operations
    override fun getApplications(projectId: String): Flow<List<Application>> = flow {
        val projectIds = currentUserProjectIds()
        if (projectId !in projectIds) {
            emit(emptyList())
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireProjectUserId()
            emitAll(
                fs.collection("users").document(uid).collection("projects").document(projectId)
                    .collection("applications")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { it.toObjects(Application::class.java) }
            )
        } else {
            emitAll(crashDao.getApplications(projectId))
        }
    }

    override fun getAllApplications(): Flow<List<Application>> = flow {
        val uid = requireProjectUserId()
        val projectIds = currentUserProjectIds()
        supabaseProjectDataSource?.let {
            emit(it.getAllApplications().filter { application -> application.projectId in projectIds })
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            emitAll(
                fs.collectionGroup("applications")
                    .whereEqualTo("ownerId", uid)
                    .snapshots()
                    .map { snapshot -> snapshot.toObjects(Application::class.java).filter { it.projectId in projectIds } }
                    .catch { e ->
                        handleFirestoreError(e, OperationType.LIST, "collectionGroup/applications")
                        throw e
                    }
            )
        } else if (projectIds.isEmpty()) {
            emit(emptyList())
        } else {
            emitAll(crashDao.getAllApplications().map { applications -> applications.filter { it.projectId in projectIds } })
        }
    }

    override suspend fun addApplication(application: Application) {
        val uid = requireProjectUserId()
        if (getProjectById(application.projectId) == null) {
            throw SecurityException("The selected project is not available to the active account.")
        }
        if (application.ownerId.isNotBlank() && application.ownerId != uid) {
            throw SecurityException("An application cannot be created for another account.")
        }

        val id = if (application.id.isEmpty()) UUID.randomUUID().toString() else application.id
        val newApp = application.copy(id = id, ownerId = uid)
        supabaseProjectDataSource?.let {
            crashDao.insertApplication(it.createApplication(newApp))
            return
        }

        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(application.projectId)
            ?.collection("applications")?.document(id)?.set(newApp)?.await()

        crashDao.insertApplication(newApp)
    }

    override suspend fun deleteApplication(applicationId: String) {
        val uid = requireProjectUserId()
        val applications = getAllApplications().first()
        val app = applications.find { it.id == applicationId } ?: return
        supabaseProjectDataSource?.let {
            it.deleteApplication(applicationId)
            crashDao.deleteApplication(applicationId)
            return
        }

        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(app.projectId)
            ?.collection("applications")?.document(applicationId)?.delete()?.await()

        crashDao.deleteApplication(applicationId)
    }

    // Crashes Operations & PII Scrubbing
    override fun getLatestCrashes(projectId: String?, applicationId: String?): Flow<List<CrashReport>> = flow {
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            if (projectId != null) {
                val collectionPath = "users/$uid/projects/$projectId/crashes"
                val query = fs.collection("users").document(uid).collection("projects").document(projectId)
                    .collection("crashes")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(100)
                val filteredQuery = if (applicationId != null) query.whereEqualTo("applicationId", applicationId) else query
                emitAll(
                    filteredQuery.snapshots()
                        .map { it.toObjects(CrashReport::class.java) }
                        .catch { e ->
                            handleFirestoreError(e, OperationType.LIST, collectionPath)
                            throw e
                        }
                )
            } else {
                emitAll(
                    fs.collectionGroup("crashes")
                        .whereEqualTo("userId", uid)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(100)
                        .snapshots()
                        .map { it.toObjects(CrashReport::class.java) }
                        .catch { e ->
                            handleFirestoreError(e, OperationType.LIST, "collectionGroup/crashes")
                            throw e
                        }
                )
            }
        } else {
            val projectIds = currentUserProjectIds()
            if (projectId != null && projectId !in projectIds) {
                emit(emptyList())
            } else if (projectIds.isEmpty()) {
                emit(emptyList())
            } else {
                val source = if (projectId != null) crashDao.getCrashesByProject(projectId) else crashDao.getAllCrashes()
                emitAll(
                    source.map { crashes ->
                        crashes.filter {
                            it.projectId in projectIds && (applicationId == null || it.applicationId == applicationId)
                        }.sortedByDescending { it.timestamp }.take(100)
                    }
                )
            }
        }
    }

    override fun getAllCrashes(): Flow<List<CrashReport>> = flow {
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("crashes")
                    .whereEqualTo("userId", uid)
                    .snapshots()
                    .map { it.toObjects(CrashReport::class.java) }
                    .catch { e ->
                        handleFirestoreError(e, OperationType.LIST, "collectionGroup/crashes")
                        throw e
                    }
            )
        } else {
            val projectIds = currentUserProjectIds()
            if (projectIds.isEmpty()) emit(emptyList())
            else emitAll(crashDao.getAllCrashes().map { crashes -> crashes.filter { it.projectId in projectIds } })
        }
    }

    override fun getCrashDetails(crashId: String): Flow<CrashReport?> = flow {
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("crashes")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("id", crashId)
                    .snapshots()
                    .map { it.toObjects(CrashReport::class.java).firstOrNull() }
                    .catch { e ->
                        handleFirestoreError(e, OperationType.GET, "collectionGroup/crashes/$crashId")
                        throw e
                    }
            )
        } else {
            val projectIds = currentUserProjectIds()
            emitAll(crashDao.getCrashDetails(crashId).map { crash -> crash?.takeIf { it.projectId in projectIds } })
        }
    }

    override fun getIssues(projectId: String): Flow<List<Issue>> = flow {
        val projectIds = currentUserProjectIds()
        if (projectId !in projectIds) {
            emit(emptyList())
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            val collectionPath = "users/$uid/projects/$projectId/issues"
            emitAll(
                fs.collection("users").document(uid).collection("projects").document(projectId)
                    .collection("issues")
                    .orderBy("lastOccurrence", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { it.toObjects(Issue::class.java) }
                    .catch { e ->
                        handleFirestoreError(e, OperationType.LIST, collectionPath)
                        throw e
                    }
            )
        } else {
            emitAll(crashDao.getIssues(projectId))
        }
    }

    override fun getAllIssues(): Flow<List<Issue>> = flow {
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("issues")
                    .whereEqualTo("ownerId", uid)
                    .snapshots()
                    .map { it.toObjects(Issue::class.java) }
                    .catch { e ->
                        handleFirestoreError(e, OperationType.LIST, "collectionGroup/issues")
                        throw e
                    }
            )
        } else {
            val projectIds = currentUserProjectIds()
            if (projectIds.isEmpty()) emit(emptyList())
            else emitAll(crashDao.getAllIssues().map { issues -> issues.filter { it.projectId in projectIds } })
        }
    }

    override fun getIssueDetails(issueId: String): Flow<Issue?> = flow {
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("issues")
                    .whereEqualTo("ownerId", uid)
                    .whereEqualTo("id", issueId)
                    .snapshots()
                    .map { it.toObjects(Issue::class.java).firstOrNull() }
                    .catch { e ->
                        handleFirestoreError(e, OperationType.GET, "collectionGroup/issues/$issueId")
                        throw e
                    }
            )
        } else {
            val projectIds = currentUserProjectIds()
            emitAll(crashDao.getIssueDetails(issueId).map { issue -> issue?.takeIf { it.projectId in projectIds } })
        }
    }

    override fun getNotifications(): Flow<List<SystemNotification>> = flow {
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collection("users").document(uid).collection("notifications")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { snapshot -> snapshot.toObjects(SystemNotification::class.java).filter { it.projectId in projectIds } }
            )
        } else {
            emitAll(crashDao.getNotifications().map { notifications -> notifications.filter { it.projectId in projectIds } })
        }
    }

    override fun getNotificationsForProject(projectId: String): Flow<List<SystemNotification>> = flow {
        val projectIds = currentUserProjectIds()
        if (projectId !in projectIds) {
            emit(emptyList())
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collection("users").document(uid).collection("notifications")
                    .whereEqualTo("projectId", projectId)
                    .snapshots()
                    .map { it.toObjects(SystemNotification::class.java) }
            )
        } else {
            emitAll(crashDao.getNotificationsForProject(projectId))
        }
    }

    override fun getUnreadNotificationCount(): Flow<Int> =
        getNotifications().map { notifications -> notifications.count { !it.isRead } }

    override suspend fun addNotification(notification: SystemNotification) {
        val projectIds = currentUserProjectIds()
        if (notification.projectId !in projectIds) {
            throw SecurityException("Cannot create a notification for a project outside the active account.")
        }
        val uid = requireUserId()
        val id = if (notification.id.isEmpty()) UUID.randomUUID().toString() else notification.id
        val scopedNotification = notification.copy(id = id)
        firestore?.collection("users")?.document(uid)?.collection("notifications")?.document(id)
            ?.let { ref ->
                try {
                    ref.set(scopedNotification).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, ref.path)
                    throw e
                }
            }
        crashDao.insertNotification(scopedNotification)
    }

    override suspend fun markNotificationAsRead(id: String) {
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty()) return
        val visibleNotification = getNotifications().first().firstOrNull { it.id == id }
        if (firestore != null && visibleNotification == null) {
            throw SecurityException("Notification does not belong to the active account.")
        }
        val uid = requireUserId()
        firestore?.collection("users")?.document(uid)?.collection("notifications")?.document(id)
            ?.let { ref ->
                try {
                    ref.update("isRead", true).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, ref.path)
                    throw e
                }
            }
        crashDao.markNotificationAsReadForProjects(id, projectIds.toList())
    }

    override suspend fun markAllNotificationsAsRead() {
        val projectIds = currentUserProjectIds().toList()
        if (projectIds.isEmpty()) return
        val uid = requireUserId()
        firestore?.let { fs ->
            val collectionPath = "users/$uid/notifications"
            try {
                val unread = fs.collection("users").document(uid).collection("notifications")
                    .whereEqualTo("isRead", false).get().await()
                val scopedDocuments = unread.documents.filter { it.getString("projectId") in projectIds }
                if (scopedDocuments.isNotEmpty()) {
                    val batch = fs.batch()
                    scopedDocuments.forEach { batch.update(it.reference, "isRead", true) }
                    batch.commit().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, collectionPath)
                throw e
            }
        }
        crashDao.markAllNotificationsAsReadForProjects(projectIds)
    }

    override suspend fun clearNotifications() {
        val projectIds = currentUserProjectIds().toList()
        if (projectIds.isEmpty()) return
        val uid = requireUserId()
        firestore?.let { fs ->
            val collectionPath = "users/$uid/notifications"
            try {
                val all = fs.collection("users").document(uid).collection("notifications").get().await()
                val scopedDocuments = all.documents.filter { it.getString("projectId") in projectIds }
                if (scopedDocuments.isNotEmpty()) {
                    val batch = fs.batch()
                    scopedDocuments.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, collectionPath)
                throw e
            }
        }
        crashDao.clearNotificationsForProjects(projectIds)
    }

    private fun truncateText(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength / 2) + "\n... [TRUNCATED " + (text.length - maxLength) + " CHARS] ...\n" + text.takeLast(maxLength / 2)
    }

    private fun extractRelevantLines(code: String, targetLine: Int, range: Int = 20): String {
        val lines = code.lines()
        val errorLine = (targetLine - 1).coerceIn(0, lines.size - 1)
        val start = (errorLine - range).coerceAtLeast(0)
        val end = (errorLine + range).coerceAtMost(lines.size - 1)
        
        return lines.subList(start, end + 1).mapIndexed { index, line ->
            val idx = start + index + 1
            if (idx == targetLine) ">>> $idx: $line" else "$idx: $line"
        }.joinToString("\n")
    }
}

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

private data class FixResultResponse(
    val suggestedCode: String,
    val explanation: String,
    val potentialSideEffects: String,
    val fullFileContent: Boolean
)

data class SentryConfig(val org: String, val project: String, val token: String)
data class BugsnagConfig(val projectId: String, val token: String)
