package com.example.domain.repository

import com.example.domain.model.IntegrationType
import com.example.domain.model.ProjectTechnology

data class SetupGuideProgress(
    val tech: ProjectTechnology?,
    val service: IntegrationType
)

/**
 * Stores the selected platform/provider so a user can resume a guide.
 * Selection and copied instructions are never treated as validation evidence.
 */
interface SetupGuideProgressStore {
    fun load(projectId: String, applicationId: String): SetupGuideProgress?

    fun save(
        projectId: String,
        applicationId: String,
        tech: ProjectTechnology?,
        service: IntegrationType
    )
}
