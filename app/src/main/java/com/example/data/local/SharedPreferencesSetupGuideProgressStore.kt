package com.example.data.local

import android.content.Context
import com.example.domain.model.IntegrationType
import com.example.domain.model.ProjectTechnology
import com.example.domain.repository.SetupGuideProgress
import com.example.domain.repository.SetupGuideProgressStore
import java.security.MessageDigest

class SharedPreferencesSetupGuideProgressStore(context: Context) : SetupGuideProgressStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(projectId: String, applicationId: String): SetupGuideProgress? {
        if (projectId.isBlank() || applicationId.isBlank()) return null
        val key = entryKey(projectId, applicationId)
        val service = preferences.getString("$key.service", null)
            ?.let { value -> runCatching { IntegrationType.valueOf(value) }.getOrNull() }
            ?: return null
        val tech = preferences.getString("$key.tech", null)
            ?.let { value -> runCatching { ProjectTechnology.valueOf(value) }.getOrNull() }
            ?.takeUnless { it == ProjectTechnology.UNKNOWN }
        return SetupGuideProgress(tech = tech, service = service)
    }

    override fun save(
        projectId: String,
        applicationId: String,
        tech: ProjectTechnology?,
        service: IntegrationType
    ) {
        if (projectId.isBlank() || applicationId.isBlank()) return
        val key = entryKey(projectId, applicationId)
        preferences.edit().apply {
            putString("$key.service", service.name)
            if (tech == null || tech == ProjectTechnology.UNKNOWN) {
                remove("$key.tech")
            } else {
                putString("$key.tech", tech.name)
            }
        }.apply()
    }

    private fun entryKey(projectId: String, applicationId: String): String {
        val input = "$projectId\u0000$applicationId".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        val hash = digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return "entry_$hash"
    }

    private companion object {
        const val PREFERENCES_NAME = "setup_guide_progress_v1"
    }
}
