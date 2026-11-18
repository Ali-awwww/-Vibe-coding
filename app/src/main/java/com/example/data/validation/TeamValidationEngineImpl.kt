package com.example.data.validation

import com.example.data.governance.TeamGovernanceEngineImpl
import com.example.data.repository.RateLimitRetryPolicy
import com.example.domain.governance.TeamGovernanceException
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.validation.*
import com.example.util.SecurityUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TeamValidationEngineImpl(
    private val aiModelRepository: AIModelRepository,
    private val teamRepository: AIModelTeamRepository,
    private val encryptSecret: (String) -> String = SecurityUtils::encryptSecret,
    private val decryptSecret: (String) -> String = SecurityUtils::decryptSecret
) : TeamValidationEngine {

    override fun getInitialTests(): List<ValidationTestItem> = listOf(
        ValidationTestItem(
            id = 1,
            nameAr = "إنشاء فريق تحقق مؤقت",
            nameEn = "Create, Read and Clean Up a Temporary Team",
            category = ValidationCategory.SETUP_AND_KEYS,
            descriptionAr = "إنشاء فريق مملوك للمستخدم الحالي، وقراءته من المستودع، ثم حذفه والتحقق من نجاح التنظيف.",
            modelsInvolved = listOf("AIModelTeamRepository")
        ),
        ValidationTestItem(
            id = 2,
            nameAr = "حفظ واسترجاع إعدادات المزودين",
            nameEn = "Provider Configuration Persistence",
            category = ValidationCategory.SETUP_AND_KEYS,
            descriptionAr = "حفظ ثلاثة سجلات إعداد مؤقتة لمزودين مختلفين، وقراءتها ضمن نطاق المستخدم، ثم حذفها. لا يختبر هذا اتصال المزود أو صلاحية مفاتيحه.",
            modelsInvolved = listOf("Google Gemini", "OpenRouter", "Hugging Face")
        ),
        ValidationTestItem(
            id = 3,
            nameAr = "اختبار تشفير القيم السرية محلياً",
            nameEn = "Local Secret Encryption Round Trip",
            category = ValidationCategory.SETUP_AND_KEYS,
            descriptionAr = "اختبار تشفير وفك تشفير قيم اختبار عشوائية في الذاكرة. لا يثبت هذا حفظ مفاتيح مزود حقيقية أو اتصالها.",
            modelsInvolved = listOf("SecurityUtils")
        ),
        ValidationTestItem(
            id = 4,
            nameAr = "اختبار اتصال المزودين المهيأين",
            nameEn = "Configured Provider Connectivity",
            category = ValidationCategory.SETUP_AND_KEYS,
            descriptionAr = "تنفيذ فحص اتصال حقيقي للمزودين المدعومين الذين أعدّهم المستخدم الحالي بمفاتيح صالحة.",
            modelsInvolved = listOf("Configured AI providers")
        ),
        ValidationTestItem(
            id = 5,
            nameAr = "حفظ واسترجاع أدوار أعضاء الفريق",
            nameEn = "Persist Distinct Team Roles",
            category = ValidationCategory.SETUP_AND_KEYS,
            descriptionAr = "حفظ خمسة أدوار مختلفة في فريق مؤقت، ثم قراءتها من المستودع والتحقق من عدم فقدان أي دور.",
            modelsInvolved = listOf("TeamRole", "AIModelTeamRepository")
        ),
        ValidationTestItem(
            id = 6,
            nameAr = "حفظ واسترجاع قائد الفريق",
            nameEn = "Persist Team Leader Assignment",
            category = ValidationCategory.SETUP_AND_KEYS,
            descriptionAr = "حفظ قائد مرتبط بعضو موجود في فريق مؤقت، ثم التحقق من علاقة القائد بعد القراءة.",
            modelsInvolved = listOf("Team leader assignment")
        ),
        ValidationTestItem(
            id = 7,
            nameAr = "تشغيل مهمة متتابعة",
            nameEn = "Sequential Execution Flow",
            category = ValidationCategory.EXECUTION_MODES,
            descriptionAr = "يتطلب تنفيذ Orchestrator حقيقياً مع مخرجات خطوة محفوظة ومدخلة في الخطوة التالية.",
            modelsInvolved = listOf("TeamOrchestrator")
        ),
        ValidationTestItem(
            id = 8,
            nameAr = "تشغيل مهام متوازية",
            nameEn = "Parallel Execution Flow",
            category = ValidationCategory.EXECUTION_MODES,
            descriptionAr = "يتطلب تشغيل أعضاء فعليين بالتوازي وقياس التزامن والنتائج.",
            modelsInvolved = listOf("TeamOrchestrator")
        ),
        ValidationTestItem(
            id = 9,
            nameAr = "تشغيل نمط النقاش",
            nameEn = "Debate Mode Execution",
            category = ValidationCategory.EXECUTION_MODES,
            descriptionAr = "يتطلب مقترحات ونقداً ومخرجات فعلية من نماذج الفريق مع قرار قائد موثق.",
            modelsInvolved = listOf("TeamOrchestrator")
        ),
        ValidationTestItem(
            id = 10,
            nameAr = "تشغيل النمط الهرمي",
            nameEn = "Hierarchical Mode Execution",
            category = ValidationCategory.EXECUTION_MODES,
            descriptionAr = "يتطلب خطة موزعة فعلياً ومخرجات أعضاء مرتبطة باعتمادياتها.",
            modelsInvolved = listOf("TeamOrchestrator")
        ),
        ValidationTestItem(
            id = 11,
            nameAr = "تمرير نتائج نموذج إلى نموذج آخر",
            nameEn = "Model-to-Model Result Handover",
            category = ValidationCategory.COLLABORATION_AND_HANDOVER,
            descriptionAr = "يتطلب إثباتاً من سجل تنفيذ حقيقي بأن مخرجات عضو وصلت إلى سياق العضو التالي.",
            modelsInvolved = listOf("TeamOrchestrator")
        ),
        ValidationTestItem(
            id = 12,
            nameAr = "اكتشاف اختلاف الإجابات والتعارضات",
            nameEn = "Divergence and Conflict Detection",
            category = ValidationCategory.COLLABORATION_AND_HANDOVER,
            descriptionAr = "يتطلب اختلافاً فعلياً بين مخرجات النماذج ونتيجة تحليل محفوظة، لا أمثلة مكتوبة مسبقاً.",
            modelsInvolved = listOf("TeamOrchestrator")
        ),
        ValidationTestItem(
            id = 13,
            nameAr = "مراجعة المخرجات وتدقيق الجودة",
            nameEn = "Review and Critique Round",
            category = ValidationCategory.COLLABORATION_AND_HANDOVER,
            descriptionAr = "يتطلب استدعاء مراجع فعلياً وتقرير مراجعة مرتبطاً بمخرجات التنفيذ.",
            modelsInvolved = listOf("Code Reviewer Agent")
        ),
        ValidationTestItem(
            id = 14,
            nameAr = "إيقاف الفريق واستئناف العمل",
            nameEn = "Pause and Resume Team",
            category = ValidationCategory.COLLABORATION_AND_HANDOVER,
            descriptionAr = "يتطلب مهمة تنفيذ حقيقية قابلة للإيقاف والاستئناف والتحقق من استمرار حالتها.",
            modelsInvolved = listOf("TeamOrchestrator")
        ),
        ValidationTestItem(
            id = 15,
            nameAr = "اكتشاف فشل نموذج والتعامل معه",
            nameEn = "Model Failure and Failover",
            category = ValidationCategory.GOVERNANCE_AND_LIMITS,
            descriptionAr = "يتطلب حقن فشل مضبوطاً أو استجابة مزود فعلية مع إثبات التحويل إلى البديل.",
            modelsInvolved = listOf("Failover handling")
        ),
        ValidationTestItem(
            id = 16,
            nameAr = "التعامل مع حدود الاستهلاك",
            nameEn = "API Rate Limit and Quota Handling",
            category = ValidationCategory.GOVERNANCE_AND_LIMITS,
            descriptionAr = "يتطلب استجابة 429 فعلية أو محاكي مزود مضبوطاً وإثبات مهلة إعادة المحاولة.",
            modelsInvolved = listOf("Rate limit handling")
        ),
        ValidationTestItem(
            id = 17,
            nameAr = "منع الاستدعاءات المدفوعة في Free Only",
            nameEn = "Enforce Free Only Protection",
            category = ValidationCategory.GOVERNANCE_AND_LIMITS,
            descriptionAr = "يتطلب اختباراً سلبياً لحاجز التنفيذ قبل إرسال أي طلب مدفوع؛ لن يُرسل طلب مدفوع لهذا الفحص.",
            modelsInvolved = listOf("Cost protection")
        ),
        ValidationTestItem(
            id = 18,
            nameAr = "تسجيل الاستهلاك والتكلفة لكل عضو",
            nameEn = "Track Member Usage and Cost",
            category = ValidationCategory.GOVERNANCE_AND_LIMITS,
            descriptionAr = "يتطلب سجل استخدام مقروءاً وقابلاً للتنظيف أو بيئة اختبار منفصلة؛ لا تُكتب سجلات اصطناعية في بيانات المستخدم.",
            modelsInvolved = listOf("TeamUsageRecord")
        ),
        ValidationTestItem(
            id = 19,
            nameAr = "استعادة المهمة بعد انقطاع الاتصال",
            nameEn = "Task Interruption Recovery",
            category = ValidationCategory.GOVERNANCE_AND_LIMITS,
            descriptionAr = "يتطلب اختبار استعادة من التخزين بعد إعادة إنشاء العملية، لا إعادة بناء كائن في الذاكرة.",
            modelsInvolved = listOf("Execution persistence")
        ),
        ValidationTestItem(
            id = 20,
            nameAr = "عزل استعلامات مزودي المستخدمين",
            nameEn = "Local Provider Ownership Query Isolation",
            category = ValidationCategory.GOVERNANCE_AND_LIMITS,
            descriptionAr = "التحقق من أن استعلام المستودع المحلي لا يعيد سجل مزود عند طلبه بمعرف مستخدم مختلف. هذا لا يثبت عزل RLS أو Firestore.",
            modelsInvolved = listOf("AIModelRepository local scope")
        )
    )

    override fun runFullValidationSuite(userId: String): Flow<TeamValidationProgress> = flow {
        require(userId.isNotBlank()) { "An authenticated user is required." }
        val items = getInitialTests().toMutableList()
        var progress = TeamValidationProgress(
            currentTestIndex = 0,
            totalTests = items.size,
            items = items,
            isRunning = true
        )
        emit(progress)

        for (index in items.indices) {
            val current = items[index]
            items[index] = current.copy(status = ValidationStatus.RUNNING, errorMessage = null)
            progress = progress.copy(
                currentTestIndex = index + 1,
                currentTestName = current.nameAr,
                items = items.toList()
            )
            emit(progress)

            items[index] = executeSingleTestLogic(userId, current.id)
            progress = progress.copy(items = items.toList())
            emit(progress)
            delay(100)
        }

        val report = generateValidationReport(items, null, null)
        emit(progress.copy(isRunning = false, summaryReport = report))
    }

    override fun runSingleTest(userId: String, testId: Int): Flow<ValidationTestItem> = flow {
        require(userId.isNotBlank()) { "An authenticated user is required." }
        val initial = getInitialTests().firstOrNull { it.id == testId }
            ?: throw IllegalArgumentException("Unknown validation test ID.")
        emit(initial.copy(status = ValidationStatus.RUNNING, errorMessage = null))
        emit(executeSingleTestLogic(userId, testId))
    }

    private suspend fun executeSingleTestLogic(userId: String, testId: Int): ValidationTestItem {
        val base = getInitialTests().first { it.id == testId }
        val startTime = System.currentTimeMillis()

        return try {
            when (testId) {
                1 -> {
                    val team = ModelTeam(
                        id = validationId("team"),
                        name = "Vibe validation temporary team",
                        description = "Temporary repository verification fixture.",
                        goal = "Verify team create/read/delete lifecycle.",
                        ownerId = userId,
                        collaborationStyle = CollaborationStyle.ORCHESTRATED,
                        governance = TeamGovernanceSettings(freeOnly = true)
                    )
                    val stored = withTemporaryTeam(team) { it }
                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "Created and read temporary team ${stored.id}; repository cleanup completed successfully.",
                        modelsInvolved = listOf("AIModelTeamRepository")
                    )
                }

                2 -> {
                    val providers = listOf(
                        AIProvider(
                            id = validationId("google"),
                            name = "Validation Google provider",
                            type = AIProviderType.GOOGLE_GEMINI,
                            userId = userId
                        ),
                        AIProvider(
                            id = validationId("openrouter"),
                            name = "Validation OpenRouter provider",
                            type = AIProviderType.OPEN_ROUTER,
                            userId = userId
                        ),
                        AIProvider(
                            id = validationId("huggingface"),
                            name = "Validation Hugging Face provider",
                            type = AIProviderType.HUGGING_FACE,
                            userId = userId
                        )
                    )
                    withTemporaryProviders(userId, providers) {
                        providers.forEach { provider ->
                            val stored = checkNotNull(
                                aiModelRepository.getProviderById(userId, provider.id)
                            ) {
                                "A temporary provider could not be read within its owner scope."
                            }
                            check(stored.userId == userId && stored.id == provider.id) {
                                "A temporary provider was returned outside its owner scope."
                            }
                        }
                    }
                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "Saved and read three temporary provider configuration records for the current user, then removed all three. Provider connectivity and API-key validity were not tested.",
                        modelsInvolved = providers.map { it.name }
                    )
                }

                3 -> {
                    val firstValue = validationId("secret")
                    val secondValue = validationId("secret")
                    val encryptedFirst = encryptSecret(firstValue)
                    val encryptedSecond = encryptSecret(secondValue)
                    check(encryptedFirst != firstValue)
                    check(encryptedSecond != secondValue)
                    check(encryptedFirst != encryptedSecond)
                    check(decryptSecret(encryptedFirst) == firstValue)
                    check(decryptSecret(encryptedSecond) == secondValue)
                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "The local secret-encryption function encrypted two ephemeral values and restored both values without cross-value substitution. No real provider key was read or changed.",
                        modelsInvolved = listOf("SecurityUtils")
                    )
                }

                4 -> {
                    val configuredProviders = aiModelRepository.getProviders(userId).first()
                    val supportedProviders = configuredProviders.filter { provider ->
                        provider.type == AIProviderType.FIREBASE_AI_LOGIC ||
                            (provider.type in setOf(AIProviderType.GOOGLE_GEMINI, AIProviderType.OPEN_ROUTER) &&
                                !provider.apiKey.isNullOrBlank())
                    }
                    if (supportedProviders.isEmpty()) {
                        skipped(
                            base,
                            "لا يوجد مزود مدعوم ومهيأ بمفتاح صالح ضمن الحساب الحالي؛ لم يُرسل أي طلب اتصال."
                        )
                    } else {
                        val outcomes = supportedProviders.map { provider ->
                            provider to aiModelRepository.testConnection(userId, provider.id)
                        }
                        val failures = outcomes.filter { it.second.isFailure }
                        if (failures.isNotEmpty()) {
                            base.copy(
                                status = ValidationStatus.FAILED,
                                durationMs = elapsed(startTime),
                                errorMessage = "فشل فحص الاتصال لعدد ${failures.size} من ${outcomes.size} مزوداً مهيأً. راجع إعدادات المزود وسجلاته.",
                                modelsInvolved = outcomes.map { it.first.name }
                            )
                        } else {
                            base.copy(
                                status = ValidationStatus.PASSED,
                                durationMs = elapsed(startTime),
                                evidence = "نجح فحص الاتصال الفعلي لجميع المزودين المدعومين والمهيئين في الحساب الحالي (${outcomes.size}). لم يُنفذ استدلال ولم تُسجل تكلفة توكنز.",
                                modelsInvolved = outcomes.map { it.first.name }
                            )
                        }
                    }
                }

                5 -> {
                    val roles = listOf(
                        TeamRole.TEAM_LEADER,
                        TeamRole.SOFTWARE_ARCHITECT,
                        TeamRole.SENIOR_DEVELOPER,
                        TeamRole.CODE_REVIEWER,
                        TeamRole.QA_ENGINEER
                    )
                    val members = roles.mapIndexed { index, role ->
                        TeamMember(
                            id = validationId("member"),
                            name = "Validation role $index",
                            modelId = "validation/unconfigured",
                            role = role
                        )
                    }
                    val team = ModelTeam(
                        id = validationId("team"),
                        name = "Vibe validation roles",
                        ownerId = userId,
                        members = members,
                        governance = TeamGovernanceSettings(freeOnly = true)
                    )
                    val stored = withTemporaryTeam(team) { it }
                    check(stored.members.map { it.role }.toSet().containsAll(roles)) {
                        "One or more team roles were not persisted."
                    }
                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "Persisted and re-read five distinct role assignments on a temporary team, then removed the team. No model was invoked.",
                        modelsInvolved = roles.map { it.name }
                    )
                }

                6 -> {
                    val leader = TeamMember(
                        id = validationId("leader"),
                        name = "Validation team leader",
                        modelId = "validation/unconfigured",
                        role = TeamRole.TEAM_LEADER
                    )
                    val team = ModelTeam(
                        id = validationId("team"),
                        name = "Vibe validation leader",
                        ownerId = userId,
                        members = listOf(leader),
                        leaderMemberId = leader.id,
                        governance = TeamGovernanceSettings(freeOnly = true)
                    )
                    val stored = withTemporaryTeam(team) { it }
                    check(stored.leaderMemberId == leader.id && stored.members.any { it.id == leader.id }) {
                        "The persisted team leader does not reference a team member."
                    }
                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "Persisted and re-read the leader/member relationship on a temporary team, then removed the team. No model was invoked.",
                        modelsInvolved = listOf(leader.name)
                    )
                }


                15 -> {
                    val failedMember = TeamMember(
                        id = validationId("rate-limited-member"),
                        name = "Rate-limit validation member",
                        modelId = "paid-model",
                        role = TeamRole.SENIOR_DEVELOPER
                    )
                    val team = ModelTeam(
                        id = validationId("rate-limit-team"),
                        name = "Rate-limit fallback fixture",
                        ownerId = userId,
                        members = listOf(failedMember),
                        leaderMemberId = failedMember.id,
                        governance = TeamGovernanceSettings(
                            freeOnly = true,
                            autoFallbackToFree = true
                        )
                    )
                    val paidModel = AIModel(
                        id = "paid-model",
                        providerId = "validation-provider",
                        apiId = "paid-model",
                        name = "Paid validation model",
                        isFree = false,
                        inputPricePer1M = 1.0,
                        outputPricePer1M = 1.0
                    )
                    val freeModel = AIModel(
                        id = "free-model",
                        providerId = "validation-provider",
                        apiId = "openrouter/free",
                        name = "Free fallback validation model",
                        isFree = true,
                        category = AIModelCategory.FREE
                    )
                    val failover = TeamGovernanceEngineImpl(teamRepository)
                        .handleQuotaExhaustion(
                            userId = userId,
                            team = team,
                            failedMember = failedMember,
                            error = IllegalStateException("HTTP 429 Too Many Requests"),
                            availableModels = listOf(paidModel, freeModel)
                        )
                        .getOrThrow()
                    check(failover.replacementModel.id == freeModel.id) {
                        "A controlled HTTP 429 did not select the available free fallback."
                    }
                    check(failover.failoverEvent.originalModelId == paidModel.id)
                    check(failover.failoverEvent.replacementModelId == freeModel.id)
                    check(failover.failoverEvent.isFree)

                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "Controlled HTTP 429 fixture: TeamGovernanceEngineImpl selected the available free fallback and produced a failover event linking the failed paid model to the free replacement. No provider request or paid inference was sent.",
                        modelsInvolved = listOf("TeamGovernanceEngineImpl", "Controlled HTTP 429 fixture")
                    )
                }

                16 -> {
                    val policy = RateLimitRetryPolicy()
                    val now = 1_000_000L
                    val retryDelay = policy.delayBeforeRetry(
                        retryAttempt = 1,
                        retryAfterHeader = "2",
                        nowMillis = now
                    )
                    val exhaustedRetry = policy.delayBeforeRetry(
                        retryAttempt = 2,
                        retryAfterHeader = "2",
                        nowMillis = now
                    )
                    val boundedDelay = policy.delayBeforeRetry(
                        retryAttempt = 1,
                        retryAfterHeader = "120",
                        nowMillis = now
                    )
                    check(retryDelay == 2_000L) {
                        "Retry-After seconds were not converted into a millisecond cooldown."
                    }
                    check(exhaustedRetry == null) {
                        "The rate-limit policy allowed more retries than configured."
                    }
                    check(boundedDelay == 30_000L) {
                        "The Retry-After cooldown was not capped at the configured maximum."
                    }

                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "Controlled Retry-After fixture: a 2-second header produced a 2000ms cooldown, the single retry budget was enforced, and a 120-second header was capped at 30000ms. The same policy is used by OpenRouter inference; no provider request was sent by this probe.",
                        modelsInvolved = listOf("RateLimitRetryPolicy", "Controlled Retry-After fixture")
                    )
                }

                17 -> {
                    val member = TeamMember(
                        id = validationId("free-only-member"),
                        name = "Free-only validation member",
                        modelId = "paid-model",
                        role = TeamRole.TEAM_LEADER
                    )
                    val team = ModelTeam(
                        id = validationId("free-only-team"),
                        name = "Free-only guard fixture",
                        ownerId = userId,
                        members = listOf(member),
                        leaderMemberId = member.id,
                        governance = TeamGovernanceSettings(freeOnly = true)
                    )
                    val execution = TeamExecution(
                        id = validationId("free-only-execution"),
                        teamId = team.id,
                        userId = userId,
                        prompt = "Verify Free Only blocks paid inference."
                    )
                    val guard = TeamGovernanceEngineImpl(teamRepository)
                    val paidModel = AIModel(
                        id = "paid-model",
                        providerId = "validation-provider",
                        apiId = "paid-model",
                        name = "Paid validation model",
                        isFree = false,
                        inputPricePer1M = 1.0,
                        outputPricePer1M = 1.0
                    )
                    val paidDecision = guard.validateMemberCall(
                        team,
                        member,
                        execution,
                        paidModel
                    )
                    check(paidDecision.isFailure &&
                        paidDecision.exceptionOrNull() is TeamGovernanceException.PaidModelBlocked
                    ) {
                        "Free Only did not reject the paid model before inference."
                    }
                    val freeModel = AIModel(
                        id = "free-model",
                        providerId = "validation-provider",
                        apiId = "free-model",
                        name = "Free validation model",
                        isFree = true,
                        category = AIModelCategory.FREE
                    )
                    check(
                        guard.validateMemberCall(
                            team,
                            member.copy(modelId = freeModel.id),
                            execution,
                            freeModel
                        ).isSuccess
                    ) {
                        "Free Only rejected a zero-priced free model."
                    }

                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "TeamGovernanceEngineImpl rejected a paid model with PaidModelBlocked and accepted a zero-priced free model before inference. This controlled in-memory guard test sent no provider request.",
                        modelsInvolved = listOf("TeamGovernanceEngineImpl", "In-memory paid/free model fixture")
                    )
                }

                20 -> {
                    val provider = AIProvider(
                        id = validationId("isolation"),
                        name = "Temporary ownership isolation provider",
                        type = AIProviderType.OPEN_ROUTER,
                        userId = userId,
                        apiKey = validationId("ephemeral-key")
                    )
                    val otherUserId = validationId("unrelated-user")
                    withTemporaryProviders(userId, listOf(provider)) {
                        val ownerRead = aiModelRepository.getProviderById(userId, provider.id)
                        val foreignRead = aiModelRepository.getProviderById(otherUserId, provider.id)
                        check(ownerRead != null) { "The owner could not read the temporary provider." }
                        check(foreignRead == null) { "A different user ID could read the owner's provider." }
                    }
                    base.copy(
                        status = ValidationStatus.PASSED,
                        durationMs = elapsed(startTime),
                        evidence = "The local AIModelRepository returned the temporary provider to its owner and returned null for an unrelated user ID; the provider was removed afterwards. This does not verify Supabase RLS or Firestore security rules.",
                        modelsInvolved = listOf("AIModelRepository local query scope")
                    )
                }

                else -> skipped(base, notImplementedReason(testId))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            base.copy(
                status = ValidationStatus.FAILED,
                durationMs = elapsed(startTime),
                errorMessage = error.message?.take(240) ?: "تعذر إكمال الفحص بسبب خطأ غير معروف."
            )
        }
    }

    private fun skipped(base: ValidationTestItem, reason: String) = base.copy(
        status = ValidationStatus.SKIPPED,
        evidence = null,
        errorMessage = reason
    )

    private fun notImplementedReason(testId: Int): String = when (testId) {
        in 7..13 -> "يتطلب هذا الفحص تنفيذاً حقيقياً عبر TeamOrchestrator وسجل job مرتبطاً بالمدخلات والمخرجات. لم تُنشأ محاكاة ولم يُسجل نجاح."
        14 -> "لا توجد حالياً بيئة تنفيذ مؤقتة قابلة للإيقاف والاستئناف والتنظيف بأمان."
        15 -> "لا يوجد محاكي فشل مضبوط أو سجل failover فعلي يمكن ربط النتيجة به."
        16 -> "لا يوجد حالياً مصدر 429 مضبوط يمكنه إثبات سياسة إعادة المحاولة ومدة التهدئة."
        17 -> "لم يُنفذ اختبار سلبي لحاجز Free Only قبل إرسال الطلب؛ لم يُرسل أي طلب مدفوع."
        18 -> "لا يوفر مستودع الاستخدام عقداً قابلاً للتنظيف لهذا الفحص؛ لم تُكتب سجلات استخدام اصطناعية."
        19 -> "يتطلب هذا الفحص إعادة إنشاء العملية واستعادة سجل محفوظ؛ لم يُستخدم كائن ذاكرة بديل."
        else -> "هذا الفحص غير متاح في التنفيذ الحالي."
    }

    private suspend fun <T> withTemporaryTeam(
        team: ModelTeam,
        block: suspend (ModelTeam) -> T
    ): T {
        val teamId = teamRepository.createTeam(team).getOrThrow()
        try {
            val stored = checkNotNull(teamRepository.getTeamById(teamId)) {
                "The temporary team could not be read after creation."
            }
            check(stored.ownerId == team.ownerId) { "The temporary team owner changed during persistence." }
            return block(stored)
        } finally {
            withContext(NonCancellable) {
                teamRepository.deleteTeam(teamId).getOrThrow()
            }
        }
    }

    private suspend fun <T> withTemporaryProviders(
        userId: String,
        providers: List<AIProvider>,
        block: suspend () -> T
    ): T {
        val inserted = mutableListOf<AIProvider>()
        try {
            providers.forEach { provider ->
                aiModelRepository.addProvider(provider)
                inserted += provider
            }
            return block()
        } finally {
            withContext(NonCancellable) {
                val cleanupFailures = mutableListOf<Throwable>()
                inserted.asReversed().forEach { provider ->
                    try {
                        aiModelRepository.deleteProvider(userId, provider.id)
                    } catch (error: Throwable) {
                        cleanupFailures += error
                    }
                }
                cleanupFailures.firstOrNull()?.let {
                    throw IllegalStateException("Temporary validation provider cleanup failed.", it)
                }
            }
        }
    }

    private fun validationId(prefix: String): String =
        "validation_${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun elapsed(startTime: Long): Long =
        (System.currentTimeMillis() - startTime).coerceAtLeast(0L)

    override fun runVibeCodingSandbox(userId: String): Flow<VibeCodingSandboxProgress> = flow {
        emit(
            VibeCodingSandboxProgress(
                isAvailable = false,
                blockedReason = "لا توجد مساحة عمل معزولة متصلة تنشئ ملفات وتشغّل الاختبارات فعلياً. لم يتم إنشاء ملفات أو عرض نتائج محاكاة."
            )
        )
    }

    override fun runCrashInvestigationSandbox(userId: String): Flow<CrashInvestigationSandboxProgress> = flow {
        emit(
            CrashInvestigationSandboxProgress(
                isAvailable = false,
                blockedReason = "لا يوجد حالياً مصدر crash فعلي ومحرك تحليل معزول مرتبط به. لم يتم اختلاق تقرير انهيار أو ترقيع أو نتيجة اختبار."
            )
        )
    }

    override suspend fun generateValidationReport(
        testItems: List<ValidationTestItem>,
        vibeProgress: VibeCodingSandboxProgress?,
        crashProgress: CrashInvestigationSandboxProgress?
    ): TeamValidationSummaryReport {
        val localIsolationPassed = testItems.any {
            it.id == 20 && it.status == ValidationStatus.PASSED
        }
        val report = TeamValidationSummaryReport(
            timestamp = System.currentTimeMillis(),
            totalTests = testItems.size,
            passedCount = testItems.count { it.status == ValidationStatus.PASSED },
            failedCount = testItems.count { it.status == ValidationStatus.FAILED },
            skippedCount = testItems.count { it.status == ValidationStatus.SKIPPED },
            pendingCount = testItems.count {
                it.status == ValidationStatus.PENDING || it.status == ValidationStatus.RUNNING
            },
            totalDurationMs = testItems.sumOf { it.durationMs },
            modelsContacted = emptyList(),
            conflictsDetected = emptyList(),
            rateLimitsObserved = emptyList(),
            totalActualCostUsd = 0.0,
            actualCostMeasured = false,
            isFreeOnlyProtected = testItems.any {
                it.id == 17 && it.status == ValidationStatus.PASSED
            },
            userKeyIsolationVerified = localIsolationPassed,
            userKeyIsolationScope = if (localIsolationPassed) {
                "Local AIModelRepository query scoping only"
            } else {
                null
            },
            backendUserIsolationVerified = false,
            isolatedSandboxVerified = vibeProgress?.let { it.isAvailable && it.isComplete } == true &&
                crashProgress?.let { it.isAvailable && it.isComplete } == true,
            testResults = testItems.toList(),
            actionableErrors = testItems
                .filter { it.status == ValidationStatus.FAILED || it.status == ValidationStatus.SKIPPED }
                .mapNotNull { item ->
                    item.errorMessage?.let { "${item.nameAr}: $it" }
                }
        )
        return report.copy(markdownReport = buildMarkdownReport(report))
    }

    override fun buildMarkdownReport(report: TeamValidationSummaryReport): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(report.timestamp))
        val overallStatus = when {
            report.failedCount > 0 -> "FAILED"
            report.pendingCount > 0 -> "INCOMPLETE"
            report.skippedCount > 0 -> "PARTIAL — skipped checks remain"
            report.passedCount == report.totalTests && report.totalTests > 0 -> "ALL CHECKS PASSED"
            else -> "INCOMPLETE"
        }
        val testRows = report.testResults.joinToString("\n") { item ->
            val status = when (item.status) {
                ValidationStatus.PASSED -> "PASS"
                ValidationStatus.FAILED -> "FAIL"
                ValidationStatus.SKIPPED -> "SKIPPED"
                ValidationStatus.RUNNING -> "RUNNING"
                ValidationStatus.PENDING -> "PENDING"
            }
            val detail = item.evidence ?: item.errorMessage ?: item.descriptionAr
            "- [$status] #${item.id} ${item.nameAr}: ${detail.replace("\n", " ")}"
        }.ifBlank { "- No validation checks have been executed." }
        val errors = report.actionableErrors.joinToString("\n") { "- $it" }
            .ifBlank { "- None recorded." }
        val cost = if (report.actualCostMeasured) {
            "USD ${String.format(Locale.US, "%.4f", report.totalActualCostUsd)}"
        } else {
            "Not measured — no token billing record was collected."
        }
        val localIsolation = if (report.userKeyIsolationVerified) {
            "PASS — ${report.userKeyIsolationScope}"
        } else {
            "NOT VERIFIED"
        }
        val freeOnly = if (report.isFreeOnlyProtected) "VERIFIED" else "NOT VERIFIED"
        val backendIsolation = if (report.backendUserIsolationVerified) "VERIFIED" else "NOT VERIFIED"
        val sandboxes = if (report.isolatedSandboxVerified) "VERIFIED" else "NOT AVAILABLE / NOT VERIFIED"

        return """
            # Team Validation Report

            **Generated:** $date  
            **Overall status:** $overallStatus  
            **Total checks:** ${report.totalTests}  
            **Passed:** ${report.passedCount} | **Failed:** ${report.failedCount} | **Skipped:** ${report.skippedCount} | **Pending:** ${report.pendingCount}  
            **Execution duration:** ${report.totalDurationMs} ms  
            **Actual provider cost:** $cost

            ## Verification boundaries

            - **Free Only enforcement:** $freeOnly
            - **Local provider ownership query scope:** $localIsolation
            - **Supabase RLS / Firestore rules isolation:** $backendIsolation
            - **Executable Vibe and crash sandboxes:** $sandboxes
            - **Model inference calls:** none recorded by this validation run.
            - **Conflict and rate-limit events:** none recorded by this validation run.

            ## Validation checks

            $testRows

            ## Action required

            $errors
        """.trimIndent()
    }
}
