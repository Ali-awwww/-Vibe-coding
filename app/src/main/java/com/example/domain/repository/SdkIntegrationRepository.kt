package com.example.domain.repository

import com.example.domain.model.*

interface SdkIntegrationRepository {
    suspend fun detectTechnology(fileNames: List<String>): ProjectScanResult
    suspend fun getSetupGuide(tech: ProjectTechnology, service: IntegrationType, projectId: String): SdkSetupGuide
    suspend fun verifyIntegration(applicationId: String): Result<Boolean>
}
