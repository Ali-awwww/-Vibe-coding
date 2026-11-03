package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectTechnology {
    ANDROID_KOTLIN,
    ANDROID_JAVA,
    IOS_SWIFT,
    IOS_OBJC,
    FLUTTER,
    REACT_NATIVE,
    UNITY,
    UNKNOWN
}

@Serializable
data class IntegrationStep(
    val title: String,
    val description: String,
    val codeSnippet: String? = null,
    val fileToModify: String? = null,
    val isOptional: Boolean = false
)

@Serializable
data class SdkSetupGuide(
    val tech: ProjectTechnology,
    val service: IntegrationType,
    val steps: List<IntegrationStep>,
    val troubleshooting: List<String>
)

data class ProjectScanResult(
    val tech: ProjectTechnology,
    val detectedFiles: List<String>,
    val appId: String? = null,
    val appVersion: String? = null
)
