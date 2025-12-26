package com.speechmatics.sdk.common

/**
 * Represents the state of a WebSocket connection
 */
enum class SocketState {
    CONNECTING,
    OPEN,
    CLOSING,
    CLOSED
}
