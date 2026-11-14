package com.example.data.repository

/**
 * Keeps project data scoped to the Supabase identity used by the Android application.
 *
 * Legacy Firestore is accepted only when its Firebase principal is exactly the same
 * identity. A different or missing Firebase principal must never be treated as the
 * active Supabase account.
 */
internal object ProjectIdentityGuard {
    fun requireSupabaseUserId(
        currentUserId: String?,
        requestedUserId: String? = null
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

        return authenticatedUserId
    }

    fun requireLegacyFirebaseIdentityMatch(
        supabaseUserId: String,
        firebaseUserId: String?
    ) {
        if (firebaseUserId != supabaseUserId) {
            throw IllegalStateException(
                "Legacy Firestore access is disabled until the Firebase identity matches the active Supabase account."
            )
        }
    }
}
