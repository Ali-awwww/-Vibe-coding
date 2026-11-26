package com.example.data.repository

/**
 * Keeps project data scoped strictly to the active Firebase authenticated identity.
 * Missing or mismatched identities fail closed.
 */
internal object ProjectIdentityGuard {
    fun requireAuthenticatedUserId(
        currentUserId: String?,
        requestedUserId: String? = null,
        firebaseUserId: String? = currentUserId
    ): String {
        val authenticatedUserId = currentUserId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Authentication is required for project data operations.")

        if (requestedUserId != null) {
            require(requestedUserId.isNotBlank()) {
                "A non-blank user ID is required for project data operations."
            }
            if (requestedUserId != authenticatedUserId) {
                throw SecurityException("Project data can only be accessed for the active account.")
            }
        }

        if (firebaseUserId != null && firebaseUserId != authenticatedUserId) {
            throw SecurityException("Firestore access requires the active Firebase user identity.")
        }

        return authenticatedUserId
    }
}
