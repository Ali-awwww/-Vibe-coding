package com.example.domain.repository

import com.example.domain.model.*

interface SdkIntegrationRepository {
    /**
     * True only when a real authenticated diagnostic-event receiver can verify SDK delivery.
     * Default false keeps local setup instructions from being treated as a successful integration.
     */
    val isVerificationTransportAvailable: Boolean
        get() = false

    suspend fun detectTechnology(fileNames: List<String>): ProjectScanResult
    suspend fun getSetupGuide(tech: ProjectTechnology, service: IntegrationType, projectId: String): SdkSetupGuide
    suspend fun verifyIntegration(applicationId: String): Result<Boolean>
}
