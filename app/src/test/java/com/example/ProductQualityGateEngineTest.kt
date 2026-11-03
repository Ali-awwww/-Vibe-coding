package com.example

import com.example.data.quality.ProductQualityGateEngine
import com.example.domain.model.quality.*
import com.example.presentation.viewmodel.ProductQualityCenterViewModel
import com.example.presentation.viewmodel.QualityCenterTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProductQualityGateEngineTest {

    private lateinit var engine: ProductQualityGateEngine

    @Before
    fun setUp() {
        engine = ProductQualityGateEngine()
    }

    @Test
    fun `test full platform assessment audits all registered screens`() {
        val assessment = engine.performFullAssessment()
        assertTrue("Total screens should be at least 26", assessment.totalScreens >= 26)
        assertEquals(assessment.totalScreens, assessment.screenAudits.size)

        // Verify key modules are all represented
        val modules = assessment.screenAudits.map { it.module }.toSet()
        assertTrue(modules.contains(ScreenModule.CRASH_INTELLIGENCE))
        assertTrue(modules.contains(ScreenModule.VIBE_STUDIO))
        assertTrue(modules.contains(ScreenModule.MODEL_TEAMS))
        assertTrue(modules.contains(ScreenModule.PLATFORM_CORE))
    }

    @Test
    fun `test all 13 completion criteria are evaluated with evidence or valid exemptions`() {
        val assessment = engine.performFullAssessment()

        assessment.screenAudits.forEach { screen ->
            assertEquals(
                "Screen ${screen.screenName} must evaluate all 13 criteria",
                13,
                screen.evaluations.size
            )

            CompletionCriterion.values().forEach { criterion ->
                val eval = screen.evaluations[criterion]
                assertNotNull("Criterion ${criterion.code} missing on ${screen.screenName}", eval)
                assertFalse("Evidence must not be blank for ${criterion.code}", eval!!.evidence.isBlank())

                if (!eval.isApplicable) {
                    assertNotNull(
                        "Exempt criterion ${criterion.code} on ${screen.screenName} must provide exemptionReason",
                        eval.exemptionReason
                    )
                }
            }
        }
    }

    @Test
    fun `test completion rate is strictly computed dynamically without hardcoded assumptions`() {
        val assessment = engine.performFullAssessment()

        var manualTotalApplicable = 0
        var manualTotalSatisfied = 0

        assessment.screenAudits.forEach { audit ->
            manualTotalApplicable += audit.totalApplicableCriteria
            manualTotalSatisfied += audit.satisfiedApplicableCriteria
        }

        assertTrue("Applicable criteria count should be greater than 200", manualTotalApplicable > 200)
        val expectedPercentage = (manualTotalSatisfied.toFloat() / manualTotalApplicable.toFloat()) * 100f

        assertEquals(
            "Overall completion percentage must match mathematical formula exactly",
            expectedPercentage,
            assessment.overallCompletionPercentage,
            0.01f
        )
        assertTrue("Completion rate must be >= 95%", assessment.overallCompletionPercentage >= 95f)
    }

    @Test
    fun `test zero active critical blockers in production ready state`() {
        val assessment = engine.performFullAssessment()

        val criticalBlockers = assessment.blockers.filter {
            it.severity == BlockerSeverity.CRITICAL_RELEASE_BLOCKER && !it.isResolved
        }

        assertEquals(
            "Zero critical release blockers should be active for production readiness",
            0,
            criticalBlockers.size
        )
        assertEquals(GateVerdictStatus.PRODUCTION_READY, assessment.verdict)
    }

    @Test
    fun `test all screens have valid registered routes and non-blank source files`() {
        val assessment = engine.performFullAssessment()

        assessment.screenAudits.forEach { screen ->
            assertTrue(
                "Screen ${screen.screenName} must have valid route starting with Screen.",
                screen.routeClass.startsWith("Screen.")
            )
            assertTrue(
                "Screen ${screen.screenName} must specify source file path",
                screen.sourceFilePath.isNotBlank()
            )
        }
    }

    @Test
    fun `test markdown acceptance report contains comprehensive verifiable audit data`() {
        val assessment = engine.performFullAssessment()
        val markdown = assessment.auditSummaryMarkdown

        assertTrue(markdown.contains("Crash Intelligence AI – Product Completion & Release Gate Audit Report"))
        assertTrue(markdown.contains("Final Release Verdict:"))
        assertTrue(markdown.contains("Empirical Completion Rate:"))
        assertTrue(markdown.contains("Screen Completion Matrix"))
        assertTrue(markdown.contains("Real Integrations Inventory"))
        assertTrue(markdown.contains("CRIT_01"))
        assertTrue(markdown.contains("CRIT_13"))
    }

    @Test
    fun `test ProductQualityCenterViewModel state management and tab selection`() = runTest {
        val viewModel = ProductQualityCenterViewModel(engine)
        val initialState = viewModel.uiState.first()

        assertNotNull("Initial assessment should be computed", initialState.assessment)
        assertEquals(QualityCenterTab.OVERVIEW_GATE, initialState.activeTab)

        viewModel.selectTab(QualityCenterTab.SCREENS_MATRIX)
        assertEquals(QualityCenterTab.SCREENS_MATRIX, viewModel.uiState.value.activeTab)

        viewModel.setModuleFilter(ScreenModule.VIBE_STUDIO)
        assertEquals(ScreenModule.VIBE_STUDIO, viewModel.uiState.value.selectedModuleFilter)

        viewModel.setSearchQuery("Crash")
        assertEquals("Crash", viewModel.uiState.value.searchQuery)

        val firstScreen = initialState.assessment!!.screenAudits.first()
        viewModel.inspectScreen(firstScreen)
        assertEquals(firstScreen.screenId, viewModel.uiState.value.selectedScreenForInspection?.screenId)

        viewModel.inspectScreen(null)
        assertNull(viewModel.uiState.value.selectedScreenForInspection)
    }
}
