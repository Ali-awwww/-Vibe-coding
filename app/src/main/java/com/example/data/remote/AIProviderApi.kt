package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query

interface OpenRouterApi {
    @GET("models")
    suspend fun getModels(): OpenRouterModelsResponse

    @POST("chat/completions")
    suspend fun generateCompletion(
        @Header("Authorization") token: String,
        @Body request: OpenRouterChatRequest
    ): OpenRouterChatResponse
}

data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelData>
)

data class OpenRouterModelData(
    val id: String,
    val name: String,
    val description: String?,
    val context_length: Int,
    val pricing: OpenRouterPricing,
    val architecture: OpenRouterArchitecture?
)

data class OpenRouterPricing(
    val prompt: String,
    val completion: String,
    val request: String,
    val image: String
)

data class OpenRouterArchitecture(
    val modality: String?,
    val tokenizer: String?,
    val instruct_type: String?
)

data class OpenRouterChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class OpenRouterChatResponse(
    val id: String,
    val choices: List<ChatChoice>,
    val usage: TokenUsage?
)

data class ChatChoice(
    val message: ChatMessage
)

data class TokenUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

interface HuggingFaceApi {
    @GET("models")
    suspend fun getModels(
        @Query("filter") filter: String = "conversational",
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: Int = -1,
        @Query("limit") limit: Int = 100
    ): List<HuggingFaceModelData>
}

data class HuggingFaceModelData(
    val modelId: String,
    val sha: String?,
    val lastModified: String?,
    val tags: List<String>?,
    val pipeline_tag: String?,
    val siblings: List<Any>?,
    val downloads: Int?,
    val likes: Int?,
    val library_name: String?
)
