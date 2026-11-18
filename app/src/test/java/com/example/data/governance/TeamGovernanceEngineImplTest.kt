package com.example.data.governance

import com.example.domain.model.AIModel
import com.example.domain.model.ModelTeam
import com.example.domain.model.TeamGovernanceSettings
import com.example.domain.model.TeamMember
import com.example.domain.model.TeamRole
import com.example.domain.model.TeamUsageRecord
import com.example.domain.repository.AIModelTeamRepository
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class TeamGovernanceEngineImplTest {

    @Test
    fun newUsageRecordsDoNotAssumeProviderConfirmedCost() {
        assertFalse(TeamUsageRecord().isCostConfirmed)
    }

    @Test
    fun recordUsagePersistsAnUnconfirmedEstimate() = runTest {
        val captured = AtomicReference<TeamUsageRecord?>()
        val repository = usageRepository(Result.success(Unit), captured)
        val engine = TeamGovernanceEngineImpl(repository)

        val record = engine.recordUsage(
            userId = "user-1",
            team = sampleTeam(),
            member = sampleMember(),
            executionId = "execution-1",
            taskDescription = "Generate a feature",
            model = sampleModel(),
            inputTokens = 120,
            outputTokens = 45,
            cost = 0.025,
            isSuccess = true,
            errorMessage = null
        )

        assertFalse(record.isCostConfirmed)
        assertEquals(record, captured.get())
        assertEquals(165, record.totalTokens)
        assertEquals(0.025, record.costUsd, 0.000001)
    }

    @Test
    fun recordUsagePropagatesLocalLedgerWriteFailure() = runTest {
        val expected = IOException("usage ledger write failed")
        val repository = usageRepository(Result.failure(expected), AtomicReference(null))
        val engine = TeamGovernanceEngineImpl(repository)

        val actual = try {
            engine.recordUsage(
                userId = "user-1",
                team = sampleTeam(),
                member = sampleMember(),
                executionId = "execution-2",
                taskDescription = "Generate a feature",
                model = sampleModel(),
                inputTokens = 120,
                outputTokens = 45,
                cost = 0.025,
                isSuccess = true,
                errorMessage = null
            )
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(expected, actual)
    }

    private fun usageRepository(
        result: Result<Unit>,
        captured: AtomicReference<TeamUsageRecord?>
    ): AIModelTeamRepository {
        return Proxy.newProxyInstance(
            AIModelTeamRepository::class.java.classLoader,
            arrayOf(AIModelTeamRepository::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "logTeamUsage" -> {
                    captured.set(args?.getOrNull(0) as? TeamUsageRecord)
                    result
                }
                "toString" -> "UsageRepositoryFixture"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> null
            }
        } as AIModelTeamRepository
    }

    private fun sampleTeam() = ModelTeam(
        id = "team-1",
        name = "Vibe team",
        ownerId = "user-1",
        governance = TeamGovernanceSettings(freeOnly = false),
        members = listOf(sampleMember())
    )

    private fun sampleMember() = TeamMember(
        id = "member-1",
        name = "Engineer",
        modelId = "model-1",
        providerId = "provider-1",
        role = TeamRole.SENIOR_DEVELOPER
    )

    private fun sampleModel() = AIModel(
        id = "model-1",
        providerId = "provider-1",
        apiId = "model-1",
        name = "Paid model",
        isFree = false
    )
}
