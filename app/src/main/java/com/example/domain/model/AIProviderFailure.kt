package com.example.domain.model

enum class AIProviderFailureKind {
    MISCONFIGURED,
    INVALID_CREDENTIALS,
    ACCESS_DENIED,
    QUOTA_EXCEEDED,
    RATE_LIMITED,
    TIMEOUT,
    EMPTY_RESPONSE,
    NETWORK,
    SERVICE_UNAVAILABLE,
    UNSUPPORTED,
    UNKNOWN
}

class AIProviderOperationException(
    val kind: AIProviderFailureKind,
    cause: Throwable? = null
) : Exception(kind.name, cause)
