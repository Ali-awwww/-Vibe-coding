package com.example.data.repository

import com.example.domain.model.AICapability
import com.example.domain.model.AIModel
import com.example.domain.model.AIModelCategory
import com.example.domain.model.AIProvider
import com.example.domain.model.AIProviderStatus
import com.example.domain.model.AIProviderType

internal object AIModelPricing {
    fun estimateCost(model: AIModel, inputTokens: Int, outputTokens: Int): Double {
        val safeInputTokens = inputTokens.coerceAtLeast(0)
        val safeOutputTokens = outputTokens.coerceAtLeast(0)
        return (safeInputTokens * model.inputPricePer1M / 1_000_000.0) +
            (safeOutputTokens * model.outputPricePer1M / 1_000_000.0)
    }
}

internal object GeminiModelCatalog {
    const val CONNECTION_MODEL_ID = "gemini-3.8-flash"

    /**
     * Standard paid-tier USD prices verified against the Gemini Developer API
     * pricing page on 2026-11-16. Gemini free-tier access depends on the user's
     * Google billing tier, so these models are conservatively treated as paid
     * for the app's Free AI Only guarantee.
     *
     * Gemini 3.8 Flash rates are valid through 2026-12-31; review before 2027-01-01.
     */
    fun models(providerId: String): List<AIModel> = listOf(
        AIModel(
            id = "$providerId:gemini-3.8-flash",
            providerId = providerId,
            apiId = "gemini-3.8-flash",
            name = "Gemini 3.8 Flash",
            isFree = false,
            contextWindow = 1_048_576,
            inputPricePer1M = 0.75,
            outputPricePer1M = 3.75,
            capabilities = listOf(
                AICapability.CODING,
                AICapability.REASONING,
                AICapability.LONG_CONTEXT,
                AICapability.VISION,
                AICapability.FUNCTION_CALLING,
                AICapability.AGENTIC
            ),
            category = AIModelCategory.GENERAL_PURPOSE
        ),
        AIModel(
            id = "$providerId:gemini-3.5-flash-lite",
            providerId = providerId,
            apiId = "gemini-3.5-flash-lite",
            name = "Gemini 3.5 Flash-Lite",
            isFree = false,
            contextWindow = 1_048_576,
            inputPricePer1M = 0.30,
            outputPricePer1M = 2.50,
            capabilities = listOf(
                AICapability.CODING,
                AICapability.REASONING,
                AICapability.LONG_CONTEXT,
                AICapability.VISION,
                AICapability.FUNCTION_CALLING,
                AICapability.AGENTIC
            ),
            category = AIModelCategory.GENERAL_PURPOSE
        )
    )
}

internal object FirebaseAiModelCatalog {
    const val DEFAULT_MODEL_ID = "gemini-3.8-flash"
    const val LITE_MODEL_ID = "gemini-3.5-flash-lite"

    fun platformProviderId(userId: String): String = "firebase_ai_platform:$userId"

    fun platformProvider(userId: String): AIProvider = AIProvider(
        id = platformProviderId(userId),
        userId = userId,
        name = "Firebase AI Logic (Gemini)",
        type = AIProviderType.FIREBASE_AI_LOGIC,
        status = AIProviderStatus.DISCONNECTED,
        apiKey = null,
        baseUrl = null,
        createdAt = 0L,
        lastSyncAt = null
    )

    /**
     * Firebase AI Logic can use the Gemini Developer API or Agent Platform backend.
     * The no-cost tier depends on the project billing plan and selected backend; the
     * app cannot inspect that plan, so these models are not guaranteed-free.
     * Paid-tier prices are used as a conservative estimate when usage metadata exists.
     * https://firebase.google.com/docs/ai-logic/pricing
     */
    fun models(providerId: String): List<AIModel> =
        GeminiModelCatalog.models(providerId).map { model ->
            model.copy(
                name = "${model.name} (Firebase AI)",
                category = AIModelCategory.GENERAL_PURPOSE
            )
        }
}
