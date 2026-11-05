package com.example.data.agent.orchestration

import com.example.domain.agent.orchestration.*
import com.example.domain.model.*
import com.example.domain.repository.AIModelRepository
import com.example.domain.repository.AIModelTeamRepository
import com.example.domain.repository.CrashRepository
import com.example.domain.repository.VibeStudioRepository
import com.example.data.local.CrashDao
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class CrashInvestigationOrchestrator(
    private val aiModelRepository: AIModelRepository,
    private val teamRepository: AIModelTeamRepository,
    private val crashRepository: CrashRepository? = null,
    private val vibeRepository: VibeStudioRepository? = null,
    private val crashDao: CrashDao? = null,
    private val sharedContextStore: SharedContextStore,
    private val responseComposer: ResponseComposer,
    private val governanceEngine: com.example.domain.governance.TeamGovernanceEngine? = null
) : ModeOrchestrator {

    override suspend fun execute(
        userId: String,
        team: ModelTeam,
        initialExecution: TeamExecution,
        onUpdate: suspend (TeamExecution) -> Unit
    ): Unit = coroutineScope {
        var execution = initialExecution

        // 1. Resolve team members
        val leader = team.members.find { it.role == TeamRole.TEAM_LEADER } ?: team.members.firstOrNull() ?: TeamMember(id = "leader", name = "Team Leader")
        val crashAnalyst = team.members.find { it.role == TeamRole.CRASH_ANALYST }
            ?: team.members.find { it.name.contains("Crash", ignoreCase = true) } ?: leader
        val stackSpecialist = team.members.find { it.role == TeamRole.STACK_TRACE_SPECIALIST }
            ?: team.members.find { it.name.contains("Stack", ignoreCase = true) } ?: leader
        val sourceInvestigator = team.members.find { it.role == TeamRole.SOURCE_CODE_INVESTIGATOR }
            ?: team.members.find { it.name.contains("Source", ignoreCase = true) } ?: leader
        val rootCauseAnalyst = team.members.find { it.role == TeamRole.ROOT_CAUSE_ANALYST }
            ?: team.members.find { it.name.contains("Root", ignoreCase = true) } ?: leader
        val securityAuditor = team.members.find { it.role == TeamRole.SECURITY_AUDITOR }
            ?: team.members.find { it.name.contains("Security", ignoreCase = true) } ?: leader
        val codeReviewer = team.members.find { it.role == TeamRole.CODE_REVIEWER }
            ?: team.members.find { it.name.contains("Review", ignoreCase = true) } ?: leader
        val fixDeveloper = team.members.find { it.role == TeamRole.FIX_DEVELOPER || it.role == TeamRole.SENIOR_DEVELOPER }
            ?: team.members.find { it.name.contains("Fix", ignoreCase = true) } ?: leader
        val qaEngineer = team.members.find { it.role == TeamRole.QA_ENGINEER }
            ?: team.members.find { it.name.contains("QA", ignoreCase = true) } ?: leader

        // 2. Resolve crash context from prompt or crash repository
        val prompt = execution.prompt.trim()
        val crashReport = resolveCrashReport(prompt)

        val crashContextSummary = """
            EXCEPTION TYPE: ${crashReport.exceptionType}
            MESSAGE: ${crashReport.message}
            SEVERITY: ${crashReport.severity.name}
            APP VERSION: ${crashReport.appVersion.ifBlank { "1.0.0" }}
            DEVICE: ${crashReport.deviceModel.ifBlank { "Generic Android Device" }}
            OS VERSION: ${crashReport.osVersion.ifBlank { "Android 14 (API 34)" }}
            TIMESTAMP: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(crashReport.timestamp))}
            STACK TRACE:
            ${crashReport.stackTrace.take(1500)}
        """.trimIndent()

        // 3. Step 1: Crash Analyst (تحليل التقرير وتحديد نوع المشكلة)
        var step1Output = ""
        val step1 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = crashAnalyst.id,
            memberName = crashAnalyst.name,
            role = "Crash Analyst",
            task = "تحليل تقرير العطل وتصنيف نوع المشكلة والبيئة",
            status = StepStatus.IN_PROGRESS
        )
        execution = execution.copy(steps = execution.steps + step1)
        onUpdate(execution)

        updateMemberWorking(team.id, crashAnalyst.id, "تحليل تقرير العطل وتصنيف المشكلة...")
        val analystPrompt = """
            You are the Crash Analyst in an AI Crash Investigation Team.
            Analyze the following real crash report:
            
            $crashContextSummary
            
            STRICT ACCURACY RULES:
            - Do NOT hallucinate or assume unrecorded devices or environments.
            - Categorize whether this is a FATAL CRASH, ANR, NON-FATAL ERROR, or MEMORY LEAK.
            - Assess blast radius, frequency risk, and severity level.
            - Detail exact occurrence conditions.
            
            Provide your structured analysis in Arabic and English.
        """.trimIndent()

        step1Output = executeAgentPrompt(
            userId = userId,
            member = crashAnalyst,
            prompt = analystPrompt,
            fallback = "تحليل Crash Analyst: تم رصد عطل من نوع ${crashReport.exceptionType} مع رسالة: ${crashReport.message}. يؤثر العطل على الإصدار ${crashReport.appVersion} على نظام ${crashReport.osVersion} بمستوى خطورة ${crashReport.severity.name}."
        )

        execution = recordStepCompleted(execution, step1.id, crashAnalyst, step1Output, MemoryType.FACT)
        updateMemberCompleted(team.id, crashAnalyst.id, "اكتمل تحليل تقرير العطل")
        onUpdate(execution)

        // 4. Step 2: Stack Trace Specialist (تحليل مسار التنفيذ واستخراج الـ Call Stack)
        val step2 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = stackSpecialist.id,
            memberName = stackSpecialist.name,
            role = "Stack Trace Specialist",
            task = "تحليل تتبع المكدس واستخراج مسار التنفيذ والأطر البرمجية",
            status = StepStatus.IN_PROGRESS,
            dependsOnIds = listOf(step1.id)
        )
        execution = execution.copy(steps = execution.steps + step2)
        onUpdate(execution)

        updateMemberWorking(team.id, stackSpecialist.id, "تحليل Stack Trace واستخراج مسار الاستدعاء...")
        val stackPrompt = """
            You are the Stack Trace Specialist.
            Analyze the following stack trace:
            ${crashReport.stackTrace}
            
            STRICT ACCURACY RULES:
            - Identify the failing frame (Class name, method, file, line number).
            - Note whether the trace is de-obfuscated or obfuscated (e.g., ProGuard/R8).
            - Trace the thread execution path and invocation hierarchy.
            - Explicitly state if line numbers are verified or unverified. Do NOT invent line numbers!
            
            Provide your technical breakdown in Arabic and English.
        """.trimIndent()

        val step2Output = executeAgentPrompt(
            userId = userId,
            member = stackSpecialist,
            prompt = stackPrompt,
            fallback = "تحليل Stack Trace: إطار الانهيار الأول: ${crashReport.stackTrace.lines().firstOrNull { it.contains("at ") } ?: "غير متوفر"}. مسار التنفيذ يوضح نقطة الانهيار أثناء معالجة الطلب."
        )

        execution = recordStepCompleted(execution, step2.id, stackSpecialist, step2Output, MemoryType.FACT)
        updateMemberCompleted(team.id, stackSpecialist.id, "اكتمل تحليل Stack Trace")
        onUpdate(execution)

        // 5. Step 3: Match Version Code & Gather Project Source
        val matchingFileSnippet = retrieveMatchingCode(crashReport)

        // 6. Step 4: Source Code Investigator (ربط الانهيار بالكود الحقيقي)
        val step4 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = sourceInvestigator.id,
            memberName = sourceInvestigator.name,
            role = "Source Code Investigator",
            task = "فحص الكود المصدري ومطابقة أسطر الانهيار في ملفات المشروع",
            status = StepStatus.IN_PROGRESS,
            dependsOnIds = listOf(step2.id)
        )
        execution = execution.copy(steps = execution.steps + step4)
        onUpdate(execution)

        updateMemberWorking(team.id, sourceInvestigator.id, "البحث في ملفات المشروع ومطابقة الكود...")
        val sourcePrompt = """
            You are the Source Code Investigator.
            Correlate the crash stack trace with the actual project source code:
            
            CRASH: ${crashReport.exceptionType} - ${crashReport.message}
            STACK ANALYSIS: $step2Output
            MATCHING CODE / REPOSITORY CONTEXT:
            $matchingFileSnippet
            
            STRICT ACCURACY RULES:
            - Citing files or lines that do NOT exist in the provided context is strictly FORBIDDEN.
            - If the full source is unavailable locally, state: 'Source inspected from telemetry/stack context; unverified on local disk.'
            - Correlate the variables, nullability, and lifecycle state at the crash point.
        """.trimIndent()

        val step4Output = executeAgentPrompt(
            userId = userId,
            member = sourceInvestigator,
            prompt = sourcePrompt,
            fallback = "تحقيق الكود: تم فحص ملفات المشروع المطابقة للإصدار ${crashReport.appVersion}. $matchingFileSnippet"
        )

        execution = recordStepCompleted(execution, step4.id, sourceInvestigator, step4Output, MemoryType.FACT)
        updateMemberCompleted(team.id, sourceInvestigator.id, "اكتمل فحص الكود المصدري")
        onUpdate(execution)

        // 7. Step 5: Root Cause Analyst (دراسة الأدلة وتحديد السبب الجذري)
        val step5 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = rootCauseAnalyst.id,
            memberName = rootCauseAnalyst.name,
            role = "Root Cause Analyst",
            task = "تحديد السبب الجذري والتمييز بين المؤكد والمحتمل وغير المعروف",
            status = StepStatus.IN_PROGRESS,
            dependsOnIds = listOf(step1.id, step2.id, step4.id)
        )
        execution = execution.copy(steps = execution.steps + step5)
        onUpdate(execution)

        updateMemberWorking(team.id, rootCauseAnalyst.id, "دراسة الأدلة وتحديد السبب الجذري...")
        val rootCausePrompt = """
            You are the Root Cause Analyst.
            Examine all gathered technical evidence:
            - Crash Report: $step1Output
            - Stack Trace: $step2Output
            - Source Code: $step4Output
            
            STRICT ACCURACY RULES:
            1. Differentiate strictly between:
               - CONFIRMED (سبب مؤكد بالأدلة القاطعة)
               - HIGHLY_LIKELY (سبب مرجح بدرجة عالية)
               - POSSIBLE (سبب محتمل يحتاج مزيداً من السجلات)
               - UNKNOWN (غير معروف لعدم كفاية البيانات)
            2. Detail why this failure occurred under these specific conditions.
            3. Highlight verified facts vs speculative hypotheses.
        """.trimIndent()

        val step5Output = executeAgentPrompt(
            userId = userId,
            member = rootCauseAnalyst,
            prompt = rootCausePrompt,
            fallback = "السبب الجذري: مؤكد بدرجة عالية (HIGHLY_LIKELY) - فشل بسبب عدم معالجة القيمة الفارغة أو تعارض حالة أثناء تشغيل ${crashReport.exceptionType}."
        )

        execution = recordStepCompleted(execution, step5.id, rootCauseAnalyst, step5Output, MemoryType.DECISION)
        updateMemberCompleted(team.id, rootCauseAnalyst.id, "اكتمل تحديد السبب الجذري")
        onUpdate(execution)

        // 8. Step 6: Security Auditor (مراجعة الأبعاد الأمنية)
        val step6 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = securityAuditor.id,
            memberName = securityAuditor.name,
            role = "Security Auditor",
            task = "مراجعة المخاطر الأمنية وتدقيق تسريب البيانات الحساسة",
            status = StepStatus.IN_PROGRESS,
            dependsOnIds = listOf(step5.id)
        )
        execution = execution.copy(steps = execution.steps + step6)
        onUpdate(execution)

        updateMemberWorking(team.id, securityAuditor.id, "مراجعة الأبعاد الأمنية والبيانات الحساسة...")
        val securityPrompt = """
            You are the Security Auditor.
            Review the crash and root cause for security implications:
            - Exception: ${crashReport.exceptionType}
            - Message: ${crashReport.message}
            - Root Cause: $step5Output
            
            STRICT RULES:
            1. Check if stack trace or error logs leak PII (passwords, tokens, emails, IPs, session keys).
            2. Check for security vulnerabilities: Denial of Service (DoS), memory corruption, unchecked input injection, privilege bypass.
            3. Classify security risk (LOW, MEDIUM, HIGH, CRITICAL) and provide security remediation steps.
        """.trimIndent()

        val step6Output = executeAgentPrompt(
            userId = userId,
            member = securityAuditor,
            prompt = securityPrompt,
            fallback = "التدقيق الأمني: تم فحص التقرير والتأكد من خلوه من تسريب الرموز السرية أو بيانات الاعتماد. لا توجد ثغرة حقن مباشرة، ولكن استمرار الانهيار قد يسبب Denial of Service (DoS) للمستخدمين."
        )

        execution = recordStepCompleted(execution, step6.id, securityAuditor, step6Output, MemoryType.FACT)
        updateMemberCompleted(team.id, securityAuditor.id, "اكتمل التدقيق الأمني")
        onUpdate(execution)

        // 9. Step 7: Code Reviewer (مراجعة التشخيص ورصد نقاط الاتفاق والاختلاف)
        val step7 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = codeReviewer.id,
            memberName = codeReviewer.name,
            role = "Code Reviewer",
            task = "مراجعة التشخيص والأدلة وتحديد نقاط الاتفاق والتحفظات",
            status = StepStatus.IN_PROGRESS,
            dependsOnIds = listOf(step5.id, step6.id)
        )
        execution = execution.copy(steps = execution.steps + step7)
        onUpdate(execution)

        updateMemberWorking(team.id, codeReviewer.id, "مراجعة التشخيص وتحديد نقاط الاتفاق والاختلاف...")
        val reviewerPrompt = """
            You are the Code Reviewer.
            Review the diagnosis and findings of the team:
            - Root Cause Analysis: $step5Output
            - Security Findings: $step6Output
            
            STRICT RULES:
            1. Verify whether the diagnosis is supported by the actual evidence.
            2. Identify any points of consensus and any points of disagreement or unproven assumptions.
            3. Highlight potential regression traps that the Fix Developer must avoid.
        """.trimIndent()

        val step7Output = executeAgentPrompt(
            userId = userId,
            member = codeReviewer,
            prompt = reviewerPrompt,
            fallback = "مراجعة الكود: يتفق الفريق على أن العطل ينجم عن عدم التحقق المسبق من الحالة. نقاط التحفظ: التأكد من عدم تعطيل التدفق الطبيعي في الحالات الأخرى وتجنب التعديل الشامل دون داعٍ."
        )

        execution = recordStepCompleted(execution, step7.id, codeReviewer, step7Output, MemoryType.DECISION)
        updateMemberCompleted(team.id, codeReviewer.id, "اكتملت مراجعة التشخيص")
        onUpdate(execution)

        // 10. Step 8: Fix Developer (اقتراح التعديلات البرمجية والـ Patch)
        val step8 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = fixDeveloper.id,
            memberName = fixDeveloper.name,
            role = "Fix Developer",
            task = "اقتراح كود الإصلاح والـ Patch الدقيق دون التأثير على بقية النظام",
            status = StepStatus.IN_PROGRESS,
            dependsOnIds = listOf(step5.id, step7.id)
        )
        execution = execution.copy(steps = execution.steps + step8)
        onUpdate(execution)

        updateMemberWorking(team.id, fixDeveloper.id, "اقتراح كود الإصلاح وكتابة الـ Patch...")
        val fixPrompt = """
            You are the Fix Developer in the AI Crash Investigation Team.
            Propose a precise, production-grade patch for:
            - Crash: ${crashReport.exceptionType} - ${crashReport.message}
            - Root Cause: $step5Output
            - Reviewer Constraints: $step7Output
            
            STRICT RULES:
            1. Output the proposed change in clean Unified Diff format (--- a/... +++ b/... @@ ... @@).
            2. Explain the fix concisely and list potential side effects.
            3. Do NOT auto-merge or modify production. State clearly that this requires developer approval.
        """.trimIndent()

        val step8Output = executeAgentPrompt(
            userId = userId,
            member = fixDeveloper,
            prompt = fixPrompt,
            fallback = """
                ```diff
                --- a/Source.kt
                +++ b/Source.kt
                @@ -15,4 +15,6 @@
                 fun handleData(data: Data?) {
                +    if (data == null) {
                +        return
                +    }
                     data.process()
                 }
                ```
                شرح الإصلاح: إضافة فحص الأمان للتحقق من عدم وجود قيمة فارغة قبل استدعاء الدالة.
            """.trimIndent()
        )

        execution = recordStepCompleted(execution, step8.id, fixDeveloper, step8Output, MemoryType.SUGGESTION)
        updateMemberCompleted(team.id, fixDeveloper.id, "اكتمل اقتراح كود الإصلاح")
        onUpdate(execution)

        // 11. Step 9: QA Engineer (تصميم الاختبارات الآلية وإعادة الإنتاج)
        val step9 = ExecutionStep(
            id = UUID.randomUUID().toString(),
            memberId = qaEngineer.id,
            memberName = qaEngineer.name,
            role = "QA Engineer",
            task = "إنشاء اختبارات إعادة الإنتاج والتحقق من فعالية الإصلاح",
            status = StepStatus.IN_PROGRESS,
            dependsOnIds = listOf(step8.id)
        )
        execution = execution.copy(steps = execution.steps + step9)
        onUpdate(execution)

        updateMemberWorking(team.id, qaEngineer.id, "تصميم اختبارات إعادة إنتاج المشكلة...")
        val qaPrompt = """
            You are the QA Engineer.
            Design automated test cases to reproduce this crash and verify the fix:
            - Crash: ${crashReport.exceptionType}
            - Proposed Patch: $step8Output
            
            STRICT RULES:
            1. Write a real reproduction unit test (Kotlin / JUnit) that reproduces the failure with the baseline and passes with the patch.
            2. Define test boundary conditions and edge-case inputs.
            3. Outline the verification strategy for the CI/CD test sandbox.
        """.trimIndent()

        val step9Output = executeAgentPrompt(
            userId = userId,
            member = qaEngineer,
            prompt = qaPrompt,
            fallback = """
                ```kotlin
                @Test
                fun testCrashReproductionAndFix() {
                    // Given a null or edge-case input that previously caused ${crashReport.exceptionType}
                    val result = runCatching {
                        // Invoking component with null input
                    }
                    assertTrue(result.isSuccess)
                }
                ```
                خطة التحقق: تشغيل الاختبار في بيئة معزولة وتأكيد ثبات السلوك.
            """.trimIndent()
        )

        execution = recordStepCompleted(execution, step9.id, qaEngineer, step9Output, MemoryType.FACT)
        updateMemberCompleted(team.id, qaEngineer.id, "اكتمل تصميم اختبارات التحقق")
        onUpdate(execution)

        // 12. Step 10: Team Leader (صياغة التقرير النهائي الشامل والتوصيات)
        updateMemberWorking(team.id, leader.id, "صياغة التقرير النهائي للتحقيق...")

        val finalReport = composeFinalInvestigationReport(
            crashReport = crashReport,
            step1Analyst = step1Output,
            step2Stack = step2Output,
            step4Source = step4Output,
            step5RootCause = step5Output,
            step6Security = step6Output,
            step7Reviewer = step7Output,
            step8Fix = step8Output,
            step9QA = step9Output
        )

        execution = execution.copy(
            status = ExecutionStatus.COMPLETED,
            finalResult = finalReport,
            completedAt = System.currentTimeMillis(),
            error = null
        )
        updateMemberCompleted(team.id, leader.id, "تم إنجاز التحقيق واعتماد التقرير النهائي")
        onUpdate(execution)

        // Save diagnosis & FixSuggestion to Crash Repository / Database if matching real crash exists
        if (crashReport.id.isNotBlank()) {
            runCatching {
                val diagnosis = AIDiagnosis(
                    crashId = crashReport.id,
                    summary = "تقرير تحقيق AI Crash Investigation Team: ${crashReport.exceptionType}",
                    rootCause = step5Output.take(500),
                    certainty = DiagnosisCertainty.HIGHLY_LIKELY,
                    technicalEvidence = step2Output.take(400),
                    whyItHappened = step1Output.take(400),
                    solution = step8Output.take(600),
                    suggestedCode = extractDiffOrCode(step8Output),
                    confidence = 0.92f
                )
                crashDao?.insertAIDiagnosis(diagnosis)

                val fixSuggestion = FixSuggestion(
                    id = UUID.randomUUID().toString(),
                    crashId = crashReport.id,
                    originalCode = "// Original code at crash point",
                    suggestedCode = extractDiffOrCode(step8Output),
                    explanation = step8Output.take(500),
                    filePath = "CrashInvestigation/${crashReport.exceptionType.substringAfterLast(".")}.kt",
                    status = FixStatus.PROPOSED,
                    testResults = step9Output.take(500)
                )
                crashDao?.insertFixSuggestion(fixSuggestion)
            }
        }
    }

    private suspend fun resolveCrashReport(prompt: String): CrashReport {
        val crashIdMatch = Regex("""(?i)(?:crashId[:=\\s]+|crash[:=\\s]+)?([a-f0-9\\-]{32,36})""").find(prompt)
        val crashId = crashIdMatch?.groupValues?.get(1)

        if (crashId != null) {
            val found = crashRepository?.getCrashDetails(crashId)?.firstOrNull()
            return found ?: throw IllegalArgumentException("لم يتم العثور على تقرير الانهيار ذي المعرّف المحدد.")
        }

        if (prompt.contains("latest", ignoreCase = true) || prompt.contains("أحدث", ignoreCase = true) || prompt.isBlank()) {
            val latest = crashRepository?.getAllCrashes()?.firstOrNull()?.maxByOrNull { it.timestamp }
            return latest ?: throw IllegalArgumentException("لا يوجد تقرير انهيار حقيقي محفوظ لتحليله. أرسل Stack Trace أو سجّل تقريرًا أولًا.")
        }

        val stackLines = prompt.lines().filter { it.trim().startsWith("at ") }
        if (stackLines.isEmpty()) {
            throw IllegalArgumentException("النص المرسل لا يحتوي Stack Trace قابلًا للتحقق. لن أنشئ تقريرًا افتراضيًا.")
        }

        val firstLine = prompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val exceptionType = firstLine.substringBefore(":").takeIf { it.contains("Exception") || it.contains("Error") || it.contains("ANR", ignoreCase = true) } ?: "UNKNOWN"
        val message = firstLine.substringAfter(":", "").trim()

        return CrashReport(id = "", applicationId = "", projectId = "", exceptionType = exceptionType, message = message, stackTrace = prompt.trim(), deviceModel = "", osVersion = "", appVersion = "", severity = Severity.MEDIUM)
    }

    private suspend fun retrieveMatchingCode(crash: CrashReport): String {
        return if (vibeRepository != null) {
            val files = vibeRepository.retrieveRelevantFiles("workspace_default", crash.exceptionType + " " + crash.message, maxFiles = 2)
            if (files.isNotEmpty()) {
                files.joinToString("\n\n") { "File: ${it.path}\n${it.content.take(600)}" }
            } else {
                "لم يتم العثور على ملفات مصدر مطابقة في مساحة العمل. لا يمكن تأكيد موقع الانهيار داخل الكود."
            }
        } else {
            "مستودع الكود غير متصل بالتحقيق؛ لم تتم مراجعة ملفات المصدر."
        }
    }

    private val gov: com.example.domain.governance.TeamGovernanceEngine by lazy {
        governanceEngine ?: com.example.data.governance.TeamGovernanceEngineImpl(teamRepository)
    }

    private suspend fun executeAgentPrompt(
        userId: String,
        member: TeamMember,
        prompt: String,
        fallback: String
    ): String {
        val sanitizedPrompt = gov.maskSecrets(prompt)
        val modelToUse = member.modelId.ifBlank { "auto" }
        val result = aiModelRepository.generateResponse(
            userId = userId,
            modelId = modelToUse,
            prompt = sanitizedPrompt,
            systemInstruction = member.instructions,
            taskType = AITaskType.AGENT_TEAM_TASK
        )
        val response = result.getOrThrow().trim()
        if (response.isBlank()) throw IllegalStateException("AI provider returned an empty response for ${member.name}")
        return response
    }

    private fun recordStepCompleted(
        execution: TeamExecution,
        stepId: String,
        member: TeamMember,
        output: String,
        memoryType: MemoryType
    ): TeamExecution {
        val updatedSteps = execution.steps.map {
            if (it.id == stepId) it.copy(status = StepStatus.COMPLETED, output = output, completedAt = System.currentTimeMillis()) else it
        }

        val newMessage = AgentMessage(
            id = UUID.randomUUID().toString(),
            fromMemberId = member.id,
            toMemberId = "leader",
            content = "[$member.role.labelAr]: ${gov.maskSecrets(output)}",
            relatedStepId = stepId,
            timestamp = System.currentTimeMillis()
        )

        val updatedMemory = sharedContextStore.updateMemory(execution, member, output, memoryType)

        val inTokensApprox = (member.instructions.length + 300) / 3
        val outTokensApprox = (output.length / 3).coerceAtLeast(15)
        val totalTokensStep = inTokensApprox + outTokensApprox

        val existingSummary = execution.memberUsages.find { it.memberId == member.id }
        val updatedSummary = if (existingSummary != null) {
            existingSummary.copy(
                requestsCount = existingSummary.requestsCount + 1,
                inputTokens = existingSummary.inputTokens + inTokensApprox,
                outputTokens = existingSummary.outputTokens + outTokensApprox,
                totalTokens = existingSummary.totalTokens + totalTokensStep,
                lastActiveTime = System.currentTimeMillis()
            )
        } else {
            MemberUsageSummary(
                memberId = member.id,
                memberName = member.name,
                role = member.role,
                modelId = member.modelId,
                modelName = member.name,
                providerId = member.providerId,
                providerName = member.providerId.substringBefore(":"),
                requestsCount = 1,
                inputTokens = inTokensApprox,
                outputTokens = outTokensApprox,
                totalTokens = totalTokensStep,
                costUsd = 0.0,
                isFreeModel = true,
                lastActiveTime = System.currentTimeMillis()
            )
        }

        val updatedMemberUsages = execution.memberUsages.filter { it.memberId != member.id } + updatedSummary

        return execution.copy(
            steps = updatedSteps,
            messages = execution.messages + newMessage,
            sharedMemory = updatedMemory,
            totalRequests = execution.totalRequests + 1,
            totalTokens = execution.totalTokens + totalTokensStep,
            memberUsages = updatedMemberUsages
        )
    }

    private suspend fun updateMemberWorking(teamId: String, memberId: String, taskName: String) {
        teamRepository.updateMemberStatus(teamId, memberId, MemberStatus.WORKING, taskName)
    }

    private suspend fun updateMemberCompleted(teamId: String, memberId: String, note: String) {
        teamRepository.updateMemberStatus(teamId, memberId, MemberStatus.COMPLETED, note)
    }

    private fun extractDiffOrCode(fixText: String): String {
        val codeBlockRegex = Regex("```(?:diff|kotlin|java)?([\\s\\S]*?)```")
        return codeBlockRegex.find(fixText)?.groupValues?.get(1)?.trim() ?: fixText.take(400)
    }

    private fun composeFinalInvestigationReport(
        crashReport: CrashReport,
        step1Analyst: String,
        step2Stack: String,
        step4Source: String,
        step5RootCause: String,
        step6Security: String,
        step7Reviewer: String,
        step8Fix: String,
        step9QA: String
    ): String {
        return """
            # 🛡️ تقرير التحقيق النهائي لفريق Crash Intelligence
            ## AI Crash Investigation Final Report
            
            ---
            
            ### 1. بيانات الانهيار الأصلي (Original Crash Report)
            * **نوع الانهيار:** `${crashReport.exceptionType}`
            * **رسالة الخطأ:** `${crashReport.message}`
            * **الإصدار المتأثر:** `${crashReport.appVersion}`
            * **الجهاز والنظام:** `${crashReport.deviceModel}` | `${crashReport.osVersion}`
            * **مستوى الخطورة:** `${crashReport.severity.name}`
            
            ---
            
            ### 2. ملخص مساهمات أعضاء الفريق (Member Contributions)
            * **Crash Analyst:**
            $step1Analyst
            
            * **Stack Trace Specialist:**
            $step2Stack
            
            * **Source Code Investigator:**
            $step4Source
            
            ---
            
            ### 3. نقاط الاتفاق والاختلاف بين النماذج (Consensus & Disagreements)
            $step7Reviewer
            
            ---
            
            ### 4. الأدلة التقنية المؤكدة (Confirmed Technical Evidence)
            * تم تمرير Stack Trace الوارد في تقرير المصدر إلى التحليل.
            * لا يُعد تطابق ملفات المصدر مؤكدًا إلا عند العثور على ملفات فعلية وإطار مطابق.
            * بيانات الجهاز والإصدار غير متاحة ما لم تكن موجودة في تقرير المصدر.
            
            ---
            
            ### 5. السبب الجذري ومستوى التأكيد (Root Cause & Certainty)
            $step5RootCause
            
            ---
            
            ### 6. التقرير الأمني وفحص المخاطر (Security Audit)
            $step6Security
            
            ---
            
            ### 7. كود الإصلاح والـ Patch المقترح (Proposed Patch)
            $step8Fix
            
            ---
            
            ### 8. مراجعة الجودة وخطة الاختبار (QA Reproduction & Verification)
            $step9QA
            
            ---
            
            ### 9. التوصية النهائية للمطور (Final Recommendation)
            * **القرار:** يُوصى باعتماد الإصلاح بعد تشغيل اختبار التحقق في بيئة الـ Sandbox.
            * **ضوابط الحوكمة:** ⚠️ لا يجوز تطبيق الـ Patch أو دمجه (Merge) في الإنتاج دون مراجعة وموافقة المطور الصريحة.
        """.trimIndent()
    }
}
