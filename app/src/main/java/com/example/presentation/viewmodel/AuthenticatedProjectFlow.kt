package com.example.presentation.viewmodel

import com.example.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Loads projects only for a known authenticated account.
 * A signed-out state must not fall back to the repository's unscoped project query.
 */
internal fun projectsForAuthenticatedUser(
    userId: String?,
    loadForUser: (String) -> Flow<List<Project>>
): Flow<List<Project>> {
    val authenticatedUserId = userId?.takeIf { it.isNotBlank() }
        ?: return flowOf(emptyList())

    return loadForUser(authenticatedUserId)
}
