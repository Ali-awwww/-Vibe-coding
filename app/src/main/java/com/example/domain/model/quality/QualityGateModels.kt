package com.example.domain.model.quality

import kotlinx.serialization.Serializable

/**
 * Screen Completion Statuses according to Project Master Rules:
 * - Not Started
 * - UI In Progress
 * - UI Complete
 * - Backend In Progress
 * - Integration In Progress
 * - Testing In Progress
 * - Failed
 * - Blocked
 * - Verified
 * - Production Ready
 */
@Serializable
enum class ScreenCompletionStatus(val label: String, val labelAr: String) {
    NOT_STARTED("Not Started", "لم يبدأ"),
    UI_IN_PROGRESS("UI In Progress", "الواجهة قيد التطوير"),
    UI_COMPLETE("UI Complete", "الواجهة مكتملة"),
    BACKEND_IN_PROGRESS("Backend In Progress", "الخلفية قيد التطوير"),
    INTEGRATION_IN_PROGRESS("Integration In Progress", "الربط قيد التطوير"),
    TESTING_IN_PROGRESS("Testing In Progress", "قيد الاختبار"),
    FAILED("Failed", "فشل التحقق"),
    BLOCKED("Blocked", "معطل / محجوب"),
    VERIFIED("Verified", "تم التحقق"),
    PRODUCTION_READY("Production Ready", "جاهز للإنتاج")
}

/**
 * Functional Modules within Crash Intelligence AI platform
 */
@Serializable
enum class ScreenModule(val titleEn: String, val titleAr: String) {
    CRASH_INTELLIGENCE("Crash Intelligence", "ذكاء وتحليل الأعطال"),
    VIBE_STUDIO("AI Vibe Studio", "استوديو التطوير الذكي"),
    MODEL_TEAMS("AI Model Teams", "فرق نماذج الذكاء الاصطناعي"),
    PLATFORM_CORE("Platform Core", "البنية الأساسية للمنصة")
}

/**
 * 13 Strict Completion Criteria for screen acceptance
 */
@Serializable
enum class CompletionCriterion(
    val code: String,
    val titleEn: String,
    val titleAr: String,
    val description: String
) {
    HAS_REAL_DESIGN(
        "CRIT_01",
        "Real Design Exists",
        "وجود تصميم فعلي",
        "Screen has genuine Jetpack Compose layout adhering to engineering design system"
    ),
    HAS_VALID_ROUTE(
        "CRIT_02",
        "Valid Type-Safe Route",
        "وجود Route صحيح",
        "Screen route is registered with @Serializable key in Screen sealed hierarchy"
    ),
    HAS_VALID_NAVIGATION(
        "CRIT_03",
        "Valid Navigation & Back Stack",
        "وجود Navigation صحيح",
        "Screen is accessible via NavHost, handles backstack, and auto-mirrors RTL arrows"
    ),
    HAS_IDENTIFIED_DATA_SOURCE(
        "CRIT_04",
        "Identified Data Source",
        "وجود مصدر بيانات محدد",
        "Screen consumes data from dedicated ViewModel, Room DB, or orchestrator"
    ),
    HAS_BACKEND_IF_NEEDED(
        "CRIT_05",
        "Backend Connected (If Needed)",
        "وجود Backend عند الحاجة",
        "Persistence layer (Room SQLite / Firestore) is integrated when data durability is required"
    ),
    HAS_CONNECTED_API_IF_NEEDED(
        "CRIT_06",
        "Connected API (If Needed)",
        "وجود API متصل عند الحاجة",
        "External service contracts (Sentry, Bugsnag, GitHub, AI Hub) are functional"
    ),
    HAS_REAL_OPERATIONS(
        "CRIT_07",
        "Real Non-Cosmetic Operations",
        "وجود عمليات حقيقية",
        "Buttons and actions trigger actual transactions, code generation, or diagnostics"
    ),
    HAS_LOADING_EMPTY_ERROR_STATES(
        "CRIT_08",
        "Loading, Empty & Error States",
        "وجود حالات Loading وEmpty وError",
        "Screen handles loading spinners, meaningful empty states, and feedback snackbars"
    ),
    HAS_APPROPRIATE_PERMISSIONS(
        "CRIT_09",
        "Appropriate Permissions & Security",
        "وجود صلاحيات مناسبة",
        "Role-based access control, tenant isolation, and credential masking are enforced"
    ),
    HAS_TESTS(
        "CRIT_10",
        "Dedicated Tests Exist",
        "وجود اختبارات",
        "Component or workflow has test coverage in Robolectric / JUnit test suite"
    ),
    PASSES_REQUIRED_TESTS(
        "CRIT_11",
        "Passes Required Tests",
        "اجتياز الاختبارات المطلوبة",
        "All associated automated test cases pass without regressions"
    ),
    PASSES_DESIGN_REVIEW(
        "CRIT_12",
        "Passes Design & Accessibility Review",
        "اجتياز مراجعة التصميم",
        "Complies with WCAG contrast, minimum 48dp touch targets, and LTR code isolation"
    ),
    NO_UNCONNECTED_CORE_INTERACTIONS(
        "CRIT_13",
        "No Unconnected Core Interactions",
        "عدم وجود عناصر تفاعلية أساسية غير متصلة",
        "Zero dummy onClick handlers or dead-end affordances in production paths"
    )
}

/**
 * Evaluation of an individual criterion for a specific screen
 */
@Serializable
data class CriterionEvaluation(
    val criterion: CompletionCriterion,
    val isSatisfied: Boolean,
    val isApplicable: Boolean = true,
    val exemptionReason: String? = null,
    val evidence: String
)

/**
 * Complete Audit Record for a screen
 */
@Serializable
data class ScreenAuditRecord(
    val screenId: String,
    val screenName: String,
    val screenNameAr: String,
    val module: ScreenModule,
    val routeClass: String,
    val sourceFilePath: String,
    val testFilePath: String? = null,
    val status: ScreenCompletionStatus,
    val evaluations: Map<CompletionCriterion, CriterionEvaluation>,
    val unconnectedInteractions: List<String> = emptyList(),
    val activeBlockers: List<String> = emptyList(),
    val lastAuditTimestamp: Long = System.currentTimeMillis()
) {
    val totalApplicableCriteria: Int
        get() = evaluations.values.count { it.isApplicable }

    val satisfiedApplicableCriteria: Int
        get() = evaluations.values.count { it.isApplicable && it.isSatisfied }

    val completionRate: Float
        get() = if (totalApplicableCriteria > 0) {
            (satisfiedApplicableCriteria.toFloat() / totalApplicableCriteria.toFloat()) * 100f
        } else 0f

    val isFullyCompliant: Boolean
        get() = totalApplicableCriteria > 0 && satisfiedApplicableCriteria == totalApplicableCriteria && activeBlockers.isEmpty()
}

/**
 * Release Blocker Categories
 */
@Serializable
enum class BlockerCategory(val titleEn: String, val titleAr: String) {
    MISSING_CORE_PAGE("Missing Core Page", "صفحة أساسية مفقودة"),
    BROKEN_NAVIGATION("Broken Navigation", "مسار أساسي مكسور"),
    MOCK_OR_COSMETIC_OP("Cosmetic/Mock Operation", "عملية أساسية شكلية"),
    CRITICAL_SECURITY("Critical Security Vulnerability", "ثغرة أمنية حرجة"),
    FAILED_TEST("Failed Core Test", "اختبار أساسي فاشل"),
    DATA_LEAK("Data Leak", "تسريب بيانات حساسة"),
    FAKE_SUCCESS_CLAIM("Fake Success Claim", "ادعاء نجاح دون تنفيذ"),
    DISCONNECTED_CORE_INTEGRATION("Disconnected Core Integration", "تكامل أساسي لا يعمل")
}

@Serializable
enum class BlockerSeverity {
    CRITICAL_RELEASE_BLOCKER,
    MAJOR_WARNING,
    MINOR_DEBT
}

/**
 * Specific Release Blocker Item
 */
@Serializable
data class ReleaseBlocker(
    val id: String,
    val title: String,
    val titleAr: String,
    val category: BlockerCategory,
    val severity: BlockerSeverity,
    val description: String,
    val affectedScreenId: String? = null,
    val remediationHint: String,
    val isResolved: Boolean
)

/**
 * High-level Release Gate Verdict
 */
@Serializable
enum class GateVerdictStatus(val label: String, val labelAr: String) {
    BLOCKED("RELEASE BLOCKED", "الإصدار محجوب"),
    CONDITIONALLY_APPROVED("CONDITIONALLY APPROVED", "معتمد بشروط"),
    PRODUCTION_READY("PRODUCTION READY", "جاهز للإنتاج بالكامل")
}

/**
 * Full Quality Gate Assessment Result
 */
@Serializable
data class QualityGateAssessment(
    val timestamp: Long,
    val verdict: GateVerdictStatus,
    val totalScreens: Int,
    val productionReadyScreens: Int,
    val verifiedScreens: Int,
    val inProgressScreens: Int,
    val failedOrBlockedScreens: Int,
    val overallCompletionPercentage: Float,
    val criticalBlockersCount: Int,
    val majorWarningsCount: Int,
    val routesTestedCount: Int,
    val functionsVerifiedCount: Int,
    val e2eTestsPassedCount: Int,
    val e2eTestsTotalCount: Int,
    val securityAuditPassed: Boolean,
    val designAuditPassed: Boolean,
    val blockers: List<ReleaseBlocker>,
    val screenAudits: List<ScreenAuditRecord>,
    val auditSummaryMarkdown: String
)
