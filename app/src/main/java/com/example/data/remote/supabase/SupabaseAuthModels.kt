package com.example.data.remote.supabase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseSignUpRequest(
    val email: String,
    val password: String,
    val data: SupabaseUserMetadata
)

@JsonClass(generateAdapter = true)
data class SupabaseUserMetadata(
    @Json(name = "display_name") val displayName: String,
    val organization: String
)

@JsonClass(generateAdapter = true)
data class SupabasePasswordGrantRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseIdTokenGrantRequest(
    val provider: String,
    @Json(name = "id_token") val idToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseRefreshGrantRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyOtpRequest(
    val type: String,
    val email: String? = null,
    val token: String? = null,
    @Json(name = "token_hash") val tokenHash: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseRecoveryRequest(val email: String)

@JsonClass(generateAdapter = true)
data class SupabaseUpdateUserRequest(val password: String)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    val user: SupabaseAuthUser? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null,
    // Supabase metadata is JSON and may contain strings, booleans, or numbers.
    @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseStoredSession(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_at_epoch_seconds") val expiresAtEpochSeconds: Long,
    val user: SupabaseAuthUser
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthError(
    val message: String? = null,
    val msg: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    val error: String? = null
)
