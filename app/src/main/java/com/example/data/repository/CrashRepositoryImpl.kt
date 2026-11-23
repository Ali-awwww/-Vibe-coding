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
    private suspend fun currentUserProjectIds(): Set<String> =
        getProjectsForUser(requireProjectUserId()).first().map { it.id }.toSet()

    private suspend fun requireOwnedProject(projectId: String, requestedUserId: String? = null): Project {
        val uid = requireProjectUserId(requestedUserId)
        val project = getProjectById(projectId)
            ?: throw SecurityException("The selected project is not available to the active account.")
        if (project.ownerId != uid) throw SecurityException("Project operations are restricted to the project owner.")
        return project
    }

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
        supabaseProjectDataSource?.let {
            emit(it.getApplications(projectId))
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
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty() || (projectId != null && projectId !in projectIds)) {
            emit(emptyList())
            return@flow
        }
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
                        .map { snapshot -> snapshot.toObjects(CrashReport::class.java).filter { it.projectId in projectIds } }
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
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("crashes")
                    .whereEqualTo("userId", uid)
                    .snapshots()
                    .map { snapshot -> snapshot.toObjects(CrashReport::class.java).filter { it.projectId in projectIds } }
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
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty()) {
            emit(null)
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("crashes")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("id", crashId)
                    .snapshots()
                    .map { snapshot -> snapshot.toObjects(CrashReport::class.java).firstOrNull { it.projectId in projectIds } }
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
    override suspend fun addCrash(crash: CrashReport) {
        val uid = requireProjectUserId(crash.userId.takeIf { it.isNotBlank() })
        val project = requireOwnedProject(crash.projectId, uid)
        if (crash.applicationId.isNotBlank() &&
            getApplications(crash.projectId).first().none { it.id == crash.applicationId }
        ) throw SecurityException("The selected application is not available in this project.")
        // Deduplicate only against records visible to the active account.
        val recentCrashes = getLatestCrashes(crash.projectId, crash.applicationId.ifBlank { null }).first()
        val isDuplicate = recentCrashes.any { existing ->
            existing.projectId == crash.projectId &&
            existing.exceptionType == crash.exceptionType &&
            existing.message == crash.message &&
            Math.abs(existing.timestamp - crash.timestamp) < 5000L
        }
        if (isDuplicate) return

        val shouldScrub = project.piiScrubbingEnabled

        val scrubbedMsg = if (shouldScrub) SecurityUtils.PiiScrubber.scrub(crash.message) else crash.message
        val scrubbedStack = if (shouldScrub) SecurityUtils.PiiScrubber.scrub(crash.stackTrace) else crash.stackTrace

        val newCrash = (if (crash.id.isEmpty()) crash.copy(id = UUID.randomUUID().toString()) else crash)
            .copy(message = scrubbedMsg, stackTrace = scrubbedStack)
            
        // We attach userId to facilitate collection group queries and security rules
        val crashWithAuth = newCrash.copy(userId = uid)

        firestore?.let { fs ->
            if (crash.projectId.isNotBlank()) {
                val docRef = fs.collection("users").document(uid).collection("projects").document(crash.projectId)
                    .collection("crashes").document(newCrash.id)
                try {
                    docRef.set(crashWithAuth).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, docRef.path)
                    throw e
                }
            }
        }
        
        crashDao.insertCrash(crashWithAuth)

        // 3. Issue Aggregation: Group crash reports by unique fingerprint
        val fingerprint = generateFingerprint("${newCrash.exceptionType}:${newCrash.stackTrace.lines().firstOrNull() ?: newCrash.message}")
        val projectIssues = crashDao.getIssues(newCrash.projectId).first()
        val existingIssue = projectIssues.find { it.fingerprint == fingerprint }
        val aggregatedIssue = if (existingIssue != null) {
            val updatedVersions = if (newCrash.appVersion.isNotBlank() && !existingIssue.affectedVersions.contains(newCrash.appVersion)) {
                if (existingIssue.affectedVersions.isBlank()) newCrash.appVersion else "${existingIssue.affectedVersions}, ${newCrash.appVersion}"
            } else {
                existingIssue.affectedVersions
            }
            existingIssue.copy(
                occurrenceCount = existingIssue.occurrenceCount + 1,
                lastOccurrence = newCrash.timestamp,
                affectedVersions = updatedVersions,
                mostAffectedOs = newCrash.osVersion.ifBlank { existingIssue.mostAffectedOs },
                mostAffectedDevice = newCrash.deviceModel.ifBlank { existingIssue.mostAffectedDevice }
            )
        } else {
            Issue(
                id = UUID.randomUUID().toString(),
                projectId = newCrash.projectId,
                applicationId = newCrash.applicationId,
                title = newCrash.exceptionType,
                subtitle = newCrash.message.take(120),
                fingerprint = fingerprint,
                severity = newCrash.severity,
                status = IssueStatus.NEW,
                occurrenceCount = 1,
                affectedUsers = 1,
                firstSeen = newCrash.timestamp,
                lastOccurrence = newCrash.timestamp,
                affectedVersions = newCrash.appVersion,
                source = newCrash.source,
                mostAffectedOs = newCrash.osVersion,
                mostAffectedDevice = newCrash.deviceModel
            )
        }
        crashDao.insertIssue(aggregatedIssue)
        firestore?.let { fs ->
            if (newCrash.projectId.isNotBlank()) {
                val issueRef = fs.collection("users").document(uid)
                    .collection("projects").document(newCrash.projectId)
                    .collection("issues").document(aggregatedIssue.id)
                try {
                    issueRef.set(issueToFirestoreMap(aggregatedIssue, uid)).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.WRITE, issueRef.path)
                    throw e
                }
            }
        }

        // 4. Live Test Session Correlation: Link crash to any ongoing live test session
        val sessions = crashDao.getTestSessions(newCrash.projectId).first()
        val activeSession = sessions.find { 
            (it.applicationId == newCrash.applicationId || it.projectId == newCrash.projectId) &&
            (it.status == TestSessionStatus.APP_RUNNING || it.status == TestSessionStatus.CONNECTED)
        }
        val isAnr = newCrash.exceptionType.contains("ANR", ignoreCase = true) ||
                    newCrash.exceptionType.contains("ApplicationNotResponding", ignoreCase = true) ||
                    newCrash.message.contains("ANR", ignoreCase = true) ||
                    newCrash.message.contains("Application Not Responding", ignoreCase = true)

        if (activeSession != null) {
            crashDao.updateTestSessionStatus(activeSession.id, TestSessionStatus.CRASH_DETECTED, System.currentTimeMillis(), newCrash.id)
            crashDao.insertTestEvent(
                TestEvent(
                    id = UUID.randomUUID().toString(),
                    sessionId = activeSession.id,
                    type = if (isAnr) TestEventType.ANR else TestEventType.CRASH,
                    details = "FATAL: ${newCrash.exceptionType} - ${newCrash.message.take(150)}"
                )
            )
        }

        // 5. System Notifications: Alert developers on critical crashes or ANRs
        if (isAnr) {
            addNotification(
                SystemNotification(
                    id = UUID.randomUUID().toString(),
                    projectId = newCrash.projectId,
                    title = "تنبيه ANR: ${newCrash.exceptionType}",
                    message = "تم رصد تجميد واجهة (ANR) في التطبيق (${newCrash.appVersion})",
                    type = NotificationType.ANR_ALERT,
                    targetCrashId = newCrash.id
                )
            )
        } else if (newCrash.severity == Severity.CRITICAL || newCrash.severity == Severity.HIGH) {
            addNotification(
                SystemNotification(
                    id = UUID.randomUUID().toString(),
                    projectId = newCrash.projectId,
                    title = "انهيار حرج (${newCrash.severity.name}): ${newCrash.exceptionType}",
                    message = newCrash.message.take(120),
                    type = NotificationType.CRASH_ALERT,
                    targetCrashId = newCrash.id
                )
            )
        }
    }
    override suspend fun updateCrashStatus(crashId: String, isResolved: Boolean) {
        val uid = requireProjectUserId()
        val crashes = getAllCrashes().first()
        val crash = crashes.find { it.id == crashId } ?: return
        requireOwnedProject(crash.projectId, uid)
        
        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(crash.projectId)
            ?.collection("crashes")?.document(crashId)?.let { ref ->
                try {
                    ref.update("isResolved", isResolved).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, ref.path)
                    throw e
                }
            }
            
        crashDao.updateCrashStatus(crashId, isResolved)
    }

    // Advanced AI Diagnosis combining Crash Report, Symbolication, and Source Code
    override suspend fun getAIDiagnosis(crashReport: CrashReport): AIDiagnosis {
        val uid = requireProjectUserId()
        val authorizedCrash = getCrashDetails(crashReport.id).first()
            ?: throw SecurityException("The crash is not available to the active account.")
        requireOwnedProject(authorizedCrash.projectId, uid)


        val fs = firestore
        val existing = if (fs != null) {
            fs.collection("users").document(uid).collection("projects").document(authorizedCrash.projectId)
                .collection("diagnoses").document(authorizedCrash.id).get().await().toObject<AIDiagnosis>()
        } else {
            crashDao.getAIDiagnosis(authorizedCrash.id).first()
        }
        
        if (existing != null && existing.summary != "Analysis Failed" && existing.summary != "Analysis incomplete.") return existing

        // 1. Symbolicate
        val symbolication = symbolicationRepository.symbolicate(authorizedCrash).getOrNull()
        val stackTraceToUse = if (symbolication != null && symbolication.frames.isNotEmpty()) {
            symbolication.frames.joinToString("\n") { frame ->
                "${frame.methodName ?: "unknown"}(${frame.fileName ?: "unknown"}:${frame.lineNumber ?: 0})"
            }
        } else {
            authorizedCrash.stackTrace
        }
        
        // 2. Fetch Source Code Context if available
        var sourceContext = ""
        try {
            val mapping = githubRepository.getMappingForApp(authorizedCrash.applicationId)
            if (mapping != null) {
                val fileName = stackTraceToUse.lines().firstOrNull { it.contains(".kt") || it.contains(".java") }
                    ?.substringAfterLast(".")?.substringBefore("(") ?: ""
                
                if (fileName.isNotBlank()) {
                    val searchResults = githubRepository.searchCode(uid, mapping.repoOwner, mapping.repoName, "filename:$fileName", mapping.defaultBranch).getOrNull()
                    val filePath = searchResults?.firstOrNull()
                    if (filePath != null) {
                        val content = githubRepository.fetchFileContent(uid, mapping.repoOwner, mapping.repoName, filePath, mapping.defaultBranch).getOrNull()
                        if (content != null) {
                            sourceContext = "\nSOURCE CODE ($filePath):\n$content"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CrashRepository", "Failed to fetch source context: ${e.message}")
        }

        // 3. Prepare AI Prompt
        val prompt = """
            You are a Senior Android Engineer and Crash Expert. Analyze this crash report and provide a detailed diagnosis.
            
            CRASH DETAILS:
            Type: ${authorizedCrash.exceptionType}
            Message: ${authorizedCrash.message}
            Severity: ${authorizedCrash.severity}
            Device: ${authorizedCrash.deviceModel} (Android ${authorizedCrash.osVersion})
            App Version: ${authorizedCrash.appVersion}
            
            STACK TRACE:
            $stackTraceToUse
            $sourceContext
            
            INSTRUCTIONS:
            1. Identify the root cause precisely.
            2. Explain why it happened.
            3. Provide a clear solution.
            4. If possible, suggest the specific code fix.
            5. Categorize the error.
            6. Provide a summary in Arabic.
            7. Output raw JSON ONLY matching the schema.
            
            JSON SCHEMA:
            {
              "summary": "Short Arabic summary",
              "rootCause": "Technical root cause",
              "certainty": "CONFIRMED | HIGHLY_LIKELY | POSSIBLE",
              "sourceLocation": "File.kt:line (method)",
              "whyItHappened": "Reasoning",
              "solution": "How to fix",
              "suggestedCode": "Optional code snippet",
              "category": "NULL_POINTER_EXCEPTION | OUT_OF_MEMORY | etc",
              "confidence": 0.9
            }
        """.trimIndent()

        // 4. Generate Response
        val defaultModel = aiModelRepository.getDefaultModel(uid) ?: throw Exception("No AI model configured for this user")
        val response = aiModelRepository.generateResponse(
            userId = uid,
            modelId = defaultModel.id,
            prompt = prompt,
            systemInstruction = "You are an AI Crash Diagnostic Assistant. Provide accurate, production-grade root cause analysis.",
            taskId = authorizedCrash.id,
            taskType = AITaskType.ROOT_CAUSE_DIAGNOSIS
        ).getOrThrow()

        val json = response.removeSurrounding("```json", "```").trim()
        val parsed = moshi.adapter(AIDiagnosisResponse::class.java).fromJson(json) ?: throw Exception("Failed to parse diagnosis response")

        val diagnosis = AIDiagnosis(
            crashId = authorizedCrash.id,
            summary = parsed.summary,
            rootCause = parsed.rootCause,
            certainty = try { DiagnosisCertainty.valueOf(parsed.certainty) } catch (e: Exception) { DiagnosisCertainty.UNKNOWN },
            sourceLocation = parsed.sourceLocation,
            whyItHappened = parsed.whyItHappened,
            solution = parsed.solution,
            suggestedCode = parsed.suggestedCode,
            category = try { ErrorCategory.valueOf(parsed.category) } catch (e: Exception) { ErrorCategory.UNKNOWN_ERROR },
            confidence = parsed.confidence,
            modelId = defaultModel.id,
            modelName = defaultModel.name,
            providerName = "AI Hub"
        )

        // 5. Save
        fs?.collection("users")?.document(uid)?.collection("projects")?.document(authorizedCrash.projectId)
            ?.collection("diagnoses")?.document(authorizedCrash.id)?.set(diagnosis)?.await()
            
        crashDao.insertAIDiagnosis(diagnosis)
        
        return diagnosis
    }

    // Integrations & Secret Encryption
    override fun getIntegrations(projectId: String): Flow<List<Integration>> = flow {
        if (projectId !in currentUserProjectIds()) { emit(emptyList()); return@flow }

        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collection("users").document(uid).collection("projects").document(projectId)
                    .collection("integrations")
                    .snapshots()
                    .map { it.toObjects(Integration::class.java) }
            )
        } else {
            emitAll(crashDao.getIntegrations(projectId))
        }
    }
    override suspend fun addIntegration(integration: Integration) {
        requireOwnedProject(integration.projectId)

        val uid = requireUserId()
        val secret = if (integration.webhookSecret.isBlank()) SecurityUtils.generateSalt() else integration.webhookSecret
        val encryptedConfig = SecurityUtils.encryptSecret(integration.configJson)

        val newIntegration = (if (integration.id.isEmpty()) integration.copy(id = UUID.randomUUID().toString()) else integration)
            .copy(webhookSecret = secret, configJson = encryptedConfig, status = IntegrationStatus.DISCONNECTED, lastSyncedAt = null)

        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(integration.projectId)
            ?.collection("integrations")?.document(newIntegration.id)?.set(newIntegration)?.await()

        crashDao.insertIntegration(newIntegration)
    }
    override suspend fun deleteIntegration(
        integrationId: String,
        projectId: String,
        callerUserId: String
    ): Result<Unit> = runCatchingCancellable {
        val uid = requireProjectUserId(callerUserId)
        requireOwnedProject(projectId, uid)
        val integration = crashDao.getIntegrationById(integrationId) ?: throw NoSuchElementException("Integration not found.")
        if (integration.projectId != projectId) throw SecurityException("Integration is outside the selected project.")
        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(projectId)
            ?.collection("integrations")?.document(integrationId)?.delete()?.await()
            
        crashDao.deleteIntegration(integrationId)
    }
    override suspend fun validateWebhook(
        projectId: String,
        integrationId: String,
        payload: String,
        signatureHeader: String?
    ): Result<Boolean> = runCatchingCancellable {
        requireOwnedProject(projectId)
        if (!SecurityUtils.checkRateLimit(projectId)) {
            crashDao.insertAuditLog(
                AuditLog(
                    projectId = projectId,
                    action = "RATE_LIMIT_EXCEEDED",
                    details = "Rate limit of 60 req/min exceeded for project $projectId"
                )
            )
            throw IllegalStateException("تم تجاوز الحد الأقصى للطلبات (Rate Limit Exceeded)")
        }

        val integration = crashDao.getIntegrationById(integrationId)
            ?: throw IllegalArgumentException("لم يتم العثور على التكامل")

        if (integration.projectId != projectId) throw SecurityException("Integration is outside the selected project.")
        if (integration.webhookSecret.isBlank() || signatureHeader.isNullOrBlank()) return@runCatchingCancellable false
        if (integration.webhookSecret.isNotBlank() && signatureHeader != null) {
            val valid = SecurityUtils.verifyWebhookSignature(payload, integration.webhookSecret, signatureHeader)
            if (!valid) {
                crashDao.insertAuditLog(
                    AuditLog(
                        projectId = projectId,
                        action = "WEBHOOK_SIGNATURE_FAILED",
                        details = "Invalid HMAC signature received for integration ${integration.name}"
                    )
                )
                return@runCatching false
            }
        }
        true
    }

    // Issues
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
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("issues")
                    .whereEqualTo("ownerId", uid)
                    .snapshots()
                    .map { snapshot -> snapshot.toObjects(Issue::class.java).filter { it.projectId in projectIds } }
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
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty()) {
            emit(null)
            return@flow
        }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collectionGroup("issues")
                    .whereEqualTo("ownerId", uid)
                    .whereEqualTo("id", issueId)
                    .snapshots()
                    .map { snapshot -> snapshot.toObjects(Issue::class.java).firstOrNull { it.projectId in projectIds } }
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
    override suspend fun addIssue(issue: Issue) {
        val uid = requireProjectUserId()
        requireOwnedProject(issue.projectId, uid)
        if (issue.applicationId.isNotBlank() && getApplications(issue.projectId).first().none { it.id == issue.applicationId }) throw SecurityException("The selected application is not available in this project.")
        val id = if (issue.id.isEmpty()) UUID.randomUUID().toString() else issue.id
        val issueWithAuth = issue.copy(id = id)
        
        firestore?.let { fs ->
            val issueRef = fs.collection("users").document(uid).collection("projects").document(issue.projectId)
                .collection("issues").document(id)
            try {
                issueRef.set(issueToFirestoreMap(issueWithAuth, uid)).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, issueRef.path)
                throw e
            }
        }
            
        crashDao.insertIssue(issueWithAuth)
    }
    override suspend fun updateIssueStatus(issueId: String, status: IssueStatus) {
        val uid = requireProjectUserId()
        val issue = getAllIssues().first().find { it.id == issueId } ?: return
        requireOwnedProject(issue.projectId, uid)
        
        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(issue.projectId)
            ?.collection("issues")?.document(issueId)?.let { ref ->
                try {
                    ref.update("status", status.name).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, ref.path)
                    throw e
                }
            }
            
        crashDao.updateIssueStatus(issueId, status)
    }
    private fun issueToFirestoreMap(issue: Issue, ownerId: String): Map<String, Any?> = buildMap {
        put("id", issue.id)
        put("projectId", issue.projectId)
        put("applicationId", issue.applicationId)
        put("ownerId", ownerId)
        put("title", issue.title)
        put("subtitle", issue.subtitle)
        put("fingerprint", issue.fingerprint)
        put("severity", issue.severity.name)
        put("status", issue.status.name)
        put("occurrenceCount", issue.occurrenceCount)
        put("affectedUsers", issue.affectedUsers)
        put("firstSeen", issue.firstSeen)
        put("lastOccurrence", issue.lastOccurrence)
        put("affectedVersions", issue.affectedVersions)
        issue.source?.let { put("source", it.name) }
        issue.diagnosisResult?.let { put("diagnosisResult", it) }
        put("trend", issue.trend)
        put("mostAffectedOs", issue.mostAffectedOs)
        put("mostAffectedDevice", issue.mostAffectedDevice)
    }

    private suspend fun persistSyncedIssue(issue: Issue) {
        val uid = requireProjectUserId()
        firestore?.let { fs ->
            val ref = fs.collection("users").document(uid).collection("projects").document(issue.projectId)
                .collection("issues").document(issue.id)
            try {
                ref.set(issueToFirestoreMap(issue, uid)).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleFirestoreError(error, OperationType.WRITE, ref.path)
                throw error
            }
        }
        crashDao.insertIssue(issue)
    }

    private suspend fun persistSyncedCrash(crash: CrashReport) {
        val uid = requireProjectUserId()
        val scoped = crash.copy(
            userId = uid,
            message = SecurityUtils.PiiScrubber.scrub(crash.message),
            stackTrace = SecurityUtils.PiiScrubber.scrub(crash.stackTrace)
        )
        firestore?.let { fs ->
            val ref = fs.collection("users").document(uid).collection("projects").document(scoped.projectId)
                .collection("crashes").document(scoped.id)
            try {
                ref.set(scoped).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleFirestoreError(error, OperationType.WRITE, ref.path)
                throw error
            }
        }
        crashDao.insertCrash(scoped)
    }

    private suspend fun updateIntegrationState(integration: Integration, status: IntegrationStatus, syncedAt: Long? = null) {
        val updated = integration.copy(status = status, lastSyncedAt = syncedAt ?: integration.lastSyncedAt)
        firestore?.let { fs ->
            val uid = requireProjectUserId()
            val ref = fs.collection("users").document(uid).collection("projects").document(integration.projectId)
                .collection("integrations").document(integration.id)
            try {
                ref.set(updated).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleFirestoreError(error, OperationType.UPDATE, ref.path)
                throw error
            }
        }
        crashDao.insertIntegration(updated)
    }

    override suspend fun getVersionStats(projectId: String, version: String): VersionStats {
        requireOwnedProject(projectId)

        val crashes = getAllCrashes().first().filter { it.projectId == projectId && it.appVersion == version }
        val affectedUsers = crashes.map { it.userId }.filter { it.isNotEmpty() }.distinct().size
        val topErrors = crashes.groupBy { it.exceptionType }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }

        return VersionStats(
            version = version,
            crashCount = crashes.size,
            affectedUsers = affectedUsers,
            topErrors = topErrors,
            newErrorsCount = crashes.distinctBy { it.exceptionType }.size,
            fixedErrorsCount = crashes.count { it.isResolved },
            crashRateChange = 0f
        )
    }

    // Team Members & Access Control
    override fun getProjectMembers(projectId: String): Flow<List<ProjectMember>> = flow {
        if (projectId !in currentUserProjectIds()) { emit(emptyList()); return@flow }

        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collection("users").document(uid).collection("projects").document(projectId)
                    .collection("members")
                    .snapshots()
                    .map { it.toObjects(ProjectMember::class.java) }
            )
        } else {
            emitAll(crashDao.getProjectMembers(projectId))
        }
    }
    override suspend fun getUserRoleInProject(projectId: String, userId: String): Role? {
        val uid = requireProjectUserId()
        if (projectId !in currentUserProjectIds()) return null
        val project = requireOwnedProject(projectId, uid)
        if (project.ownerId == userId && userId == uid) return Role.OWNER
        
        return firestore?.collection("users")?.document(uid)?.collection("projects")?.document(projectId)
            ?.collection("members")?.document(userId)?.get()?.await()?.toObject<ProjectMember>()?.role ?: crashDao.getProjectMember(projectId, userId)?.role
    }
    override suspend fun addProjectMember(
        projectId: String,
        email: String,
        role: Role,
        callerUserId: String
    ): Result<Unit> = runCatchingCancellable {
        val uid = requireProjectUserId(callerUserId)
        requireOwnedProject(projectId, uid)
        if (!AccessControl.canManageMembers(getUserRoleInProject(projectId, uid))) throw SecurityException("You are not allowed to manage project members.")
        if (role == Role.OWNER) throw SecurityException("Project ownership cannot be granted through an invitation.")
        val fs = firestore ?: throw UnsupportedOperationException("A remote user directory is required; no synthetic member was created.")
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "A valid email address is required." }
        val matches = fs.collection("users").whereEqualTo("email", normalizedEmail).limit(2).get().await().documents
        val target = matches.singleOrNull() ?: throw NoSuchElementException("No unique registered account was found for this email.")
        if (target.id == uid) throw IllegalArgumentException("The project owner is already a member.")
        val member = ProjectMember(
            projectId = projectId, userId = target.id, userEmail = normalizedEmail,
            displayName = target.getString("displayName")?.takeIf { it.isNotBlank() } ?: normalizedEmail.substringBefore("@"),
            role = role
        )
        fs.collection("users").document(uid).collection("projects").document(projectId)
            .collection("members").document(target.id).set(member).await()
        crashDao.insertProjectMember(member)
    }
    override suspend fun updateMemberRole(
        projectId: String,
        userId: String,
        newRole: Role,
        callerUserId: String
    ): Result<Unit> = runCatchingCancellable {
        val uid = requireProjectUserId(callerUserId)
        val project = requireOwnedProject(projectId, uid)
        if (!AccessControl.canManageMembers(getUserRoleInProject(projectId, uid))) throw SecurityException("You are not allowed to manage project members.")
        if (userId == project.ownerId || newRole == Role.OWNER) throw SecurityException("Project ownership cannot be changed through member-role updates.")
        val fs = firestore ?: throw UnsupportedOperationException("Remote member management is unavailable.")
        val ref = fs.collection("users").document(uid).collection("projects").document(projectId).collection("members").document(userId)
        val member = ref.get().await().toObject<ProjectMember>() ?: throw NoSuchElementException("Project member not found.")
        ref.update("role", newRole.name).await()
        crashDao.updateProjectMemberRole(projectId, member.userId, newRole)
    }
    override suspend fun removeProjectMember(
        projectId: String,
        userId: String,
        callerUserId: String
    ): Result<Unit> = runCatchingCancellable {
        val uid = requireProjectUserId(callerUserId)
        val project = requireOwnedProject(projectId, uid)
        if (!AccessControl.canManageMembers(getUserRoleInProject(projectId, uid))) throw SecurityException("You are not allowed to manage project members.")
        if (userId == project.ownerId) throw SecurityException("The project owner cannot be removed.")
        val fs = firestore ?: throw UnsupportedOperationException("Remote member management is unavailable.")
        val ref = fs.collection("users").document(uid).collection("projects").document(projectId).collection("members").document(userId)
        val member = ref.get().await().toObject<ProjectMember>() ?: throw NoSuchElementException("Project member not found.")
        ref.delete().await()
        crashDao.deleteProjectMember(projectId, member.userId)
    }
    override suspend fun updateProjectSecurityPolicy(
        projectId: String,
        retentionDays: Int,
        piiScrubbing: Boolean,
        callerUserId: String
    ): Result<Unit> = runCatching {
        require(retentionDays in 1..3650) { "Retention must be between 1 and 3650 days." }
        val uid = requireProjectUserId(callerUserId)
        requireOwnedProject(projectId, uid)
        supabaseProjectDataSource?.let {
            it.updateProjectSettings(projectId, retentionDays, piiScrubbing)
            crashDao.updateProjectSecurityPolicy(projectId, retentionDays, piiScrubbing)
            return@runCatching
        }
        firestore?.collection("users")?.document(uid)?.collection("projects")?.document(projectId)
            ?.update(mapOf(
                "dataRetentionDays" to retentionDays,
                "piiScrubbingEnabled" to piiScrubbing
            ))?.await()
            
        crashDao.updateProjectSecurityPolicy(projectId, retentionDays, piiScrubbing)
    }
    override suspend fun purgeOldCrashes(projectId: String, callerUserId: String): Result<Int> =
        runCatchingCancellable {
            requireOwnedProject(projectId, callerUserId)
            throw UnsupportedOperationException("Crash retention purge is unavailable until a transactional cloud and local deletion path is implemented.")
        }
    override fun getAuditLogsForProject(projectId: String): Flow<List<AuditLog>> = flow {
        if (projectId !in currentUserProjectIds()) { emit(emptyList()); return@flow }

        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collection("users").document(uid).collection("audit_logs")
                    .whereEqualTo("projectId", projectId)
                    .snapshots()
                    .map { it.toObjects(AuditLog::class.java) }
            )
        } else {
            emitAll(crashDao.getAuditLogsForProject(projectId))
        }
    }
    override fun getRecentAuditLogs(): Flow<List<AuditLog>> = flow {
        val projectIds = currentUserProjectIds()
        if (projectIds.isEmpty()) { emit(emptyList()); return@flow }
        val fs = firestore
        if (fs != null) {
            val uid = requireUserId()
            emitAll(
                fs.collection("users").document(uid).collection("audit_logs")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .snapshots()
                    .map { it.toObjects(AuditLog::class.java).filter { log -> log.projectId in projectIds } }
            )
        } else {
            emitAll(crashDao.getRecentAuditLogs().map { logs -> logs.filter { it.projectId in projectIds } })
        }
    }
    override suspend fun logAuditEvent(auditLog: AuditLog) {
        val uid = requireProjectUserId(auditLog.userId.takeIf { it.isNotBlank() })
        requireOwnedProject(auditLog.projectId, uid)
        val id = if (auditLog.id.isEmpty()) UUID.randomUUID().toString() else auditLog.id
        val scopedLog = auditLog.copy(id = id, userId = uid)
        firestore?.collection("users")?.document(uid)?.collection("audit_logs")?.document(id)
            ?.let { ref ->
                try {
                    ref.set(scopedLog).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, ref.path)
                    throw e
                }
            }
        crashDao.insertAuditLog(scopedLog)
    }

    // Sync from Sentry / Bugsnag
    override suspend fun syncCrashes(projectId: String): Result<Unit> = runCatchingCancellable {
        requireOwnedProject(projectId)
        val integrations = getIntegrations(projectId).first()
        if (integrations.isEmpty()) throw IllegalStateException("لا توجد تكاملات مرتبطة بهذا المشروع.")

        integrations.forEach { integration ->
            try {
                when (integration.type) {
                    IntegrationType.SENTRY -> syncSentry(projectId, integration)
                    IntegrationType.BUGSNAG -> syncBugsnag(projectId, integration)
                    IntegrationType.FIREBASE_CRASHLYTICS -> throw UnsupportedOperationException(
                        "تكامل Firebase Crashlytics يتطلب وسيطًا برمجيًا خلفيًا موثوقًا للتحقق من هوية Google Cloud."
                    )
                    else -> throw UnsupportedOperationException("نوع التكامل ${integration.type} غير مدعوم حاليًا.")
                }
                updateIntegrationState(integration, IntegrationStatus.CONNECTED, System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                try {
                    updateIntegrationState(integration, IntegrationStatus.ERROR)
                } catch (statusError: Exception) {
                    error.addSuppressed(statusError)
                }
                throw error
            }
        }
    }
    private suspend fun syncSentry(projectId: String, integration: Integration) {
        val config = parseConfig<SentryConfig>(integration.configJson)
            ?: throw IllegalStateException("Sentry integration credentials are invalid or unreadable.")
        val issues = sentryApi.getIssues(
            authToken = "Bearer ${config.token}",
            orgSlug = config.org,
            projectSlug = config.project
        )

        issues.forEach { sentryIssue ->
            val fingerprint = generateFingerprint(sentryIssue.title + sentryIssue.culprit)
            val occurrenceCount = sentryIssue.count.toIntOrNull() ?: 0
            val affectedUsers = sentryIssue.userCount

            val events = sentryApi.getIssueEvents("Bearer ${config.token}", sentryIssue.id)
            val firstEvent = events.firstOrNull()

            val issue = Issue(
                id = sentryIssue.id,
                projectId = projectId,
                title = sentryIssue.title,
                subtitle = sentryIssue.culprit,
                fingerprint = fingerprint,
                severity = calculateSeverity(occurrenceCount, affectedUsers),
                status = mapSentryStatus(sentryIssue.status),
                occurrenceCount = occurrenceCount,
                affectedUsers = affectedUsers,
                firstSeen = parseDate(sentryIssue.firstSeen),
                lastOccurrence = parseDate(sentryIssue.lastSeen),
                source = IntegrationType.SENTRY,
                mostAffectedOs = firstEvent?.contexts?.os?.name ?: "Unknown",
                mostAffectedDevice = firstEvent?.contexts?.device?.model ?: "Unknown",
                trend = 0f
            )
            persistSyncedIssue(issue)

            firstEvent?.let { event ->
                val rawMsg = event.message ?: ""
                val rawStack = event.entries?.firstOrNull { it.type == "stacktrace" }?.data?.values?.firstOrNull()?.stacktrace?.frames?.joinToString("\n") {
                    "${it.function} (${it.filename}:${it.lineNo})"
                } ?: "No stacktrace available"

                val crash = CrashReport(
                    id = event.id,
                    projectId = projectId,
                    exceptionType = sentryIssue.metadata?.type ?: "Unknown",
                    message = SecurityUtils.PiiScrubber.scrub(rawMsg),
                    stackTrace = SecurityUtils.PiiScrubber.scrub(rawStack),
                    timestamp = parseDate(event.dateCreated),
                    severity = issue.severity,
                    source = IntegrationType.SENTRY,
                    appVersion = event.release ?: "Unknown",
                    osVersion = event.contexts?.os?.version ?: "Unknown",
                    deviceModel = event.contexts?.device?.model ?: "Unknown"
                )
                persistSyncedCrash(crash)
            }
        }
    }
    private suspend fun syncBugsnag(projectId: String, integration: Integration) {
        val config = parseConfig<BugsnagConfig>(integration.configJson)
            ?: throw IllegalStateException("Bugsnag integration credentials are invalid or unreadable.")
        val errors = bugsnagApi.getErrors(
            authToken = "token ${config.token}",
            projectId = config.projectId
        )

        errors.forEach { bugsnagError ->
            val fingerprint = generateFingerprint(bugsnagError.error_class + bugsnagError.context)
            val issue = Issue(
                id = bugsnagError.id,
                projectId = projectId,
                title = bugsnagError.error_class,
                subtitle = bugsnagError.message,
                fingerprint = fingerprint,
                severity = calculateSeverity(bugsnagError.events, bugsnagError.users),
                status = mapBugsnagStatus(bugsnagError.status),
                occurrenceCount = bugsnagError.events,
                affectedUsers = bugsnagError.users,
                firstSeen = parseDate(bugsnagError.first_seen),
                lastOccurrence = parseDate(bugsnagError.last_seen),
                source = IntegrationType.BUGSNAG
            )
            persistSyncedIssue(issue)

            val events = bugsnagApi.getErrorEvents("token ${config.token}", bugsnagError.id)
            events.firstOrNull()?.let { event ->
                val exception = event.exceptions?.firstOrNull()
                val rawMsg = exception?.message ?: ""
                val rawStack = exception?.stacktrace?.joinToString("\n") {
                    "${it.method} (${it.file}:${it.lineNumber})"
                } ?: "No stacktrace available"

                val crash = CrashReport(
                    id = event.id,
                    projectId = projectId,
                    exceptionType = exception?.errorClass ?: "Unknown",
                    message = SecurityUtils.PiiScrubber.scrub(rawMsg),
                    stackTrace = SecurityUtils.PiiScrubber.scrub(rawStack),
                    timestamp = parseDate(event.received_at),
                    severity = issue.severity,
                    source = IntegrationType.BUGSNAG,
                    appVersion = event.app?.version ?: "Unknown",
                    osVersion = event.device?.osVersion ?: "Unknown",
                    deviceModel = event.device?.model ?: "Unknown"
                )
                persistSyncedCrash(crash)
            }
        }
    }
    private inline fun <reified T> parseConfig(encryptedOrRawJson: String): T? {
        val decrypted = SecurityUtils.decryptSecret(encryptedOrRawJson)
        return try {
            moshi.adapter(T::class.java).fromJson(decrypted)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateFingerprint(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun mapSentryStatus(status: String): IssueStatus = when (status) {
        "resolved" -> IssueStatus.RESOLVED
        "unresolved" -> IssueStatus.NEW
        "ignored" -> IssueStatus.RESOLVED
        else -> IssueStatus.INVESTIGATING
    }

    private fun mapBugsnagStatus(status: String): IssueStatus = when (status) {
        "fixed" -> IssueStatus.RESOLVED
        "open" -> IssueStatus.NEW
        "snoozed" -> IssueStatus.RESOLVED
        else -> IssueStatus.INVESTIGATING
    }

    private fun calculateSeverity(occurrenceCount: Int, affectedUsers: Int): Severity {
        return when {
            occurrenceCount > 100 || affectedUsers > 50 -> Severity.CRITICAL
            occurrenceCount > 50 || affectedUsers > 20 -> Severity.HIGH
            occurrenceCount > 10 -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            // ISO 8601 format usually used by APIs
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                // Fallback for format without milliseconds
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (ex: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    override suspend fun addRepositoryMapping(mapping: RepositoryMapping) {
        requireOwnedProject(mapping.projectId)
        if (getApplications(mapping.projectId).first().none { it.id == mapping.applicationId }) throw SecurityException("The application mapping is outside the active account's project.")
        crashDao.insertRepositoryMapping(mapping)
    }
    override suspend fun saveGitHubConnection(connection: GitHubConnection) {
        val uid = requireProjectUserId(connection.userId)
        crashDao.insertGitHubConnection(connection.copy(userId = uid))
    }

    // AI Fix Assistant Implementation
    override fun getFixSuggestions(crashId: String): Flow<List<FixSuggestion>> = flow {
        if (getCrashDetails(crashId).first() == null) { emit(emptyList()); return@flow }
        emitAll(crashDao.getFixSuggestions(crashId))
    }
    override suspend fun generateFixSuggestion(
        crashReport: CrashReport,
        diagnosis: AIDiagnosis
    ): Result<FixSuggestion> = runCatchingCancellable {
        val uid = requireProjectUserId(crashReport.userId.takeIf { it.isNotBlank() })
        val authorizedCrash = getCrashDetails(crashReport.id).first()
            ?: throw SecurityException("The crash is not available to the active account.")
        requireOwnedProject(authorizedCrash.projectId, uid)
        require(diagnosis.crashId == authorizedCrash.id) { "The diagnosis does not belong to this crash." }

        val mapping = crashDao.getRepositoryMapping(authorizedCrash.applicationId)?.takeIf { it.projectId == authorizedCrash.projectId }
            ?: throw IllegalStateException("يجب ربط مستودع GitHub أولاً لاقتراح إصلاح برمجي.")
        
        val sourceLocation = diagnosis.sourceLocation // e.g. "ProfileFragment.kt:74"
        val fileName = sourceLocation.substringBefore(":")
        
        // Search for the file to get the full path
        val searchResults = githubRepository.searchCode(
            userId = uid,
            owner = mapping.repoOwner,
            repo = mapping.repoName,
            query = "filename:$fileName",
            ref = mapping.defaultBranch
        ).getOrThrow()
        
        val filePath = searchResults.firstOrNull() ?: throw IllegalStateException("لم يتم العثور على الملف $fileName في المستودع.")
        
        val originalCode = githubRepository.fetchFileContent(
            userId = uid,
            owner = mapping.repoOwner,
            repo = mapping.repoName,
            path = filePath,
            ref = mapping.defaultBranch
        ).getOrThrow()

        val prompt = """
            You are an AI Fix Assistant. Propose a code-level fix for this crash.
            
            DIAGNOSIS:
            ${diagnosis.summary}
            Root Cause: ${diagnosis.rootCause}
            Why it happened: ${diagnosis.whyItHappened}
            
            SOURCE CODE (${filePath}):
            ${originalCode}
            
            INSTRUCTIONS:
            1. Analyze the original code and the diagnosis.
            2. Propose a clean, idiomatic fix that addresses the root cause.
            3. Do not just hide the error; fix the logic.
            4. Identify potential side effects.
            5. Provide a detailed explanation for the change.
            6. Output raw JSON ONLY.
            
            JSON SCHEMA:
            {
              "suggestedCode": "The complete updated file content or the specific block",
              "explanation": "Why this fix works",
              "potentialSideEffects": "What to watch out for",
              "fullFileContent": true_or_false
            }
        """.trimIndent()

        val userId = uid
        val defaultModel = aiModelRepository.getDefaultModel(userId) ?: throw Exception("No AI model configured for this user")
        val responseText = aiModelRepository.generateResponse(
            userId = userId,
            modelId = defaultModel.id,
            prompt = prompt,
            systemInstruction = "You are an AI Fix Assistant. Propose high-quality code fixes for application crashes.",
            taskId = authorizedCrash.id,
            taskType = AITaskType.CODE_FIX_SUGGESTION
        ).getOrThrow()

        val jsonText = responseText.removeSurrounding("```json", "```").trim()
        
        val fixResult = moshi.adapter(FixResultResponse::class.java).fromJson(jsonText)
            ?: throw IllegalStateException("فشل تحليل استجابة الذكاء الاصطناعي.")

        val fixId = UUID.randomUUID().toString()
        val fixSuggestion = FixSuggestion(
            id = fixId,
            crashId = authorizedCrash.id,
            originalCode = originalCode,
            suggestedCode = fixResult.suggestedCode,
            explanation = fixResult.explanation,
            potentialSideEffects = fixResult.potentialSideEffects,
            filePath = filePath,
            branchName = "fix/${authorizedCrash.exceptionType.lowercase()}-${fixId.take(5)}",
            status = FixStatus.PROPOSED,
            testResults = null // Compilation, static analysis, and unit tests have not been executed here.
        )
        
        crashDao.insertFixSuggestion(fixSuggestion)

        // System notification
        crashDao.insertNotification(
            SystemNotification(
                id = UUID.randomUUID().toString(),
                projectId = authorizedCrash.projectId,
                title = "اقتراح إصلاح كود جاهز للمراجعة",
                message = "اكتمل تحليل واقتراح تصحيح لـ ${authorizedCrash.exceptionType} في $filePath",
                type = NotificationType.FIX_READY,
                targetCrashId = authorizedCrash.id
            )
        )

        fixSuggestion
    }
    override suspend fun applyFixAndCreatePR(fixId: String, userId: String): Result<String> =
        runCatchingCancellable {
            val uid = requireProjectUserId(userId)
            val fix = crashDao.getFixSuggestionById(fixId) ?: throw IllegalArgumentException("لم يتم العثور على مقترح الإصلاح المعني.")
            if (fix.status == FixStatus.PR_CREATED) throw IllegalStateException("A pull request has already been created for this fix.")
            val crash = getCrashDetails(fix.crashId).first() ?: throw SecurityException("The crash is not available to the active account.")
            requireOwnedProject(crash.projectId, uid)
            val mapping = crashDao.getRepositoryMapping(crash.applicationId)?.takeIf { it.projectId == crash.projectId }
                ?: throw IllegalStateException("المستودع غير مرتبط بهذا التطبيق.")
            githubRepository.createBranch(uid, mapping.repoOwner, mapping.repoName, fix.branchName, mapping.defaultBranch).getOrThrow()
            githubRepository.commitChanges(uid, mapping.repoOwner, mapping.repoName, fix.branchName, fix.filePath, fix.suggestedCode,
                "Fix: ${crash.exceptionType} in ${fix.filePath}").getOrThrow()
            val body = """
                ## Crash Diagnosis
                ${crash.exceptionType}: ${crash.message}

                ## Root Cause
                ${fix.explanation}

                ## Potential Side Effects
                ${fix.potentialSideEffects}
            """.trimIndent()
            val prUrl = githubRepository.createPullRequest(uid, mapping.repoOwner, mapping.repoName,
                "Fix: ${crash.exceptionType} in ${fix.filePath}", fix.branchName, mapping.defaultBranch, body).getOrThrow()
            crashDao.updateFixStatus(fixId, FixStatus.PR_CREATED, prUrl)
            addNotification(SystemNotification(
                id = UUID.randomUUID().toString(), projectId = crash.projectId,
                title = "تم إنشاء Pull Request على GitHub بنجاح",
                message = "تم إنشاء PR لإصلاح ${crash.exceptionType} في ${fix.filePath}",
                type = NotificationType.PR_CREATED, targetCrashId = crash.id
            ))
            prUrl
        }
    override fun getAllVerifications(): Flow<List<CrashVerificationRecord>> = flow {
        val ids = currentUserProjectIds()
        if (ids.isEmpty()) { emit(emptyList()); return@flow }
        emitAll(crashDao.getAllVerifications().map { rows -> rows.filter { it.projectId in ids } })
    }
    override fun getVerificationsByProject(projectId: String): Flow<List<CrashVerificationRecord>> = flow {
        if (projectId !in currentUserProjectIds()) { emit(emptyList()); return@flow }
        emitAll(crashDao.getVerificationsByProject(projectId))
    }
    override fun getVerificationsByCrash(crashId: String): Flow<List<CrashVerificationRecord>> = flow {
        val crash = getCrashDetails(crashId).first()
        if (crash == null) { emit(emptyList()); return@flow }
        emitAll(crashDao.getVerificationsByCrash(crashId).map { rows -> rows.filter { it.projectId == crash.projectId } })
    }
    override suspend fun getVerificationById(id: String): CrashVerificationRecord? {
        val record = crashDao.getVerificationById(id) ?: return null
        return record.takeIf { it.projectId in currentUserProjectIds() }
    }
    override suspend fun verifyFix(
        crashId: String,
        fixSuggestionId: String?,
        newVersion: String,
        buildId: String,
        scenarioName: String,
        simulatedOutcome: VerificationStatus?
    ): Result<CrashVerificationRecord> = Result.failure(
        UnsupportedOperationException(
            "التحقق الآلي غير متاح: لا يوجد مشغل اختبار فعلي مرتبط بالتطبيق. لم تُنشأ جلسة أو نتيجة تحقق."
        )
    )

        override suspend fun addVerificationRecord(record: CrashVerificationRecord) {
        requireOwnedProject(record.projectId)
        val crash = getCrashDetails(record.crashId).first()
            ?: throw SecurityException("The crash is not available to the active account.")
        require(record.projectId == crash.projectId) { "The verification project does not match the crash." }
        if (record.status != VerificationStatus.INCONCLUSIVE) {
            throw UnsupportedOperationException("Verification records require execution evidence; no real Android test runner is connected.")
        }
        crashDao.insertVerification(record)
    }

    // Notifications Implementation
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