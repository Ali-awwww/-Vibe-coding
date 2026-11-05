package com.example.data.remote.supabase

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") publishableKey: String,
        @Query("redirect_to") redirectTo: String? = null,
        @Body request: SupabaseSignUpRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Header("apikey") publishableKey: String,
        @Query("grant_type") grantType: String = "password",
        @Body request: SupabasePasswordGrantRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token")
    suspend fun refreshSession(
        @Header("apikey") publishableKey: String,
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: SupabaseRefreshGrantRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/verify")
    suspend fun verifyEmailOtp(
        @Header("apikey") publishableKey: String,
        @Body request: SupabaseVerifyOtpRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/recover")
    suspend fun requestPasswordRecovery(
        @Header("apikey") publishableKey: String,
        @Query("redirect_to") redirectTo: String? = null,
        @Body request: SupabaseRecoveryRequest
    ): Response<Unit>

    @PUT("auth/v1/user")
    suspend fun updatePassword(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String,
        @Body request: SupabaseUpdateUserRequest
    ): Response<SupabaseAuthUser>

    @POST("auth/v1/logout")
    suspend fun signOut(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") bearerToken: String
    ): Response<Unit>
}
