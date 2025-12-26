package com.speechmatics.sdk.common

/**
 * Base exception for all Speechmatics SDK errors
 */
open class SpeechmaticsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when authentication fails
 */
class SpeechmaticsAuthException(
    val type: AuthErrorType,
    message: String,
    cause: Throwable? = null
) : SpeechmaticsException(message, cause)

enum class AuthErrorType {
    VALIDATION_FAILED,
    UNAUTHORIZED,
    UNKNOWN_ERROR
}

/**
 * Exception thrown during batch API operations
 */
class SpeechmaticsBatchException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : SpeechmaticsException(message, cause)

/**
 * Exception thrown during real-time transcription operations
 */
class SpeechmaticsRealtimeException(
    message: String,
    cause: Throwable? = null
) : SpeechmaticsException(message, cause)

/**
 * Exception thrown during Flow API operations
 */
class SpeechmaticsFlowException(
    val type: FlowErrorType,
    message: String,
    cause: Throwable? = null
) : SpeechmaticsException(message, cause)

enum class FlowErrorType {
    SOCKET_NOT_CLOSED,
    SOCKET_CLOSED_PREMATURELY,
    SOCKET_ERROR,
    SERVER_ERROR,
    UNEXPECTED_MESSAGE,
    BAD_BINARY_MESSAGE,
    TIMEOUT
}
