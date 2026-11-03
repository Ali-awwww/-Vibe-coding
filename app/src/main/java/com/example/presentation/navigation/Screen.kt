package com.example.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable
    data object Login : Screen()
    @Serializable
    data object Dashboard : Screen()
    @Serializable
    data object Projects : Screen()
    @Serializable
    data class Applications(val projectId: String) : Screen()
    @Serializable
    data class Crashes(
        val projectId: String? = null,
        val applicationId: String? = null,
        val exceptionType: String? = null,
        val appVersion: String? = null,
        val severity: String? = null,
        val osName: String? = null,
        val filterTitle: String? = null
    ) : Screen()
    @Serializable
    data class CrashDetail(val crashId: String) : Screen()
    @Serializable
    data class FixAssistant(val crashId: String) : Screen()
    @Serializable
    data class FixHistory(val projectId: String? = null) : Screen()
    @Serializable
    data class Integrations(val projectId: String) : Screen()
    @Serializable
    data class Issues(val projectId: String? = null) : Screen()
    @Serializable
    data object Analytics : Screen()
    @Serializable
    data object LiveTesting : Screen()
    @Serializable
    data class LiveSession(val sessionId: String) : Screen()
    @Serializable
    data class SetupGuide(val projectId: String, val applicationId: String) : Screen()
    @Serializable
    data class GitHubConnect(val projectId: String) : Screen()
    @Serializable
    data class DebugSymbols(val projectId: String) : Screen()
    @Serializable
    data object AIModels : Screen()
    @Serializable
    data object VibeStudio : Screen()
    @Serializable
    data class DevDebugCenter(val workspaceId: String? = null) : Screen()
    @Serializable
    data object Settings : Screen()
    @Serializable
    data object ModelTeams : Screen()
    @Serializable
    data class TeamDetail(val teamId: String) : Screen()
    @Serializable
    data object CreateTeam : Screen()
    @Serializable
    data class TeamUsage(val teamId: String) : Screen()
    @Serializable
    data class TeamChat(val teamId: String? = null, val projectId: String? = null) : Screen()
    @Serializable
    data object TeamValidation : Screen()
    @Serializable
    data object ProductQualityCenter : Screen()
}

