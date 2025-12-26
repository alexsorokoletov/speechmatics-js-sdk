package com.speechmatics.sdk.flow

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A jitter buffer that accumulates audio data and flushes when a threshold is reached.
 * Used to smooth out audio playback by buffering incoming audio data.
 *
 * @param maxByteLength Maximum byte length before automatic flush
 */
class JitterBuffer(
    private val maxByteLength: Int
) {
    private val buffer = mutableListOf<ShortArray>()
    private var currentByteLength = 0

    private val _flushEvents = MutableSharedFlow<List<ShortArray>>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val flushEvents: SharedFlow<List<ShortArray>> = _flushEvents.asSharedFlow()

    /**
     * Current byte length of buffered data
     */
    val byteLength: Int
        get() = currentByteLength

    /**
     * Add data to the buffer. If the buffer exceeds maxByteLength, it will be flushed.
     *
     * @param data Audio data as ShortArray (PCM 16-bit)
     */
    suspend fun enqueue(data: ShortArray) {
        buffer.add(data)
        currentByteLength += data.size * 2 // Each short is 2 bytes

        if (currentByteLength >= maxByteLength) {
            flush()
        }
    }

    /**
     * Flush all buffered data
     */
    suspend fun flush() {
        if (buffer.isNotEmpty()) {
            val flushedData = buffer.toList()
            buffer.clear()
            currentByteLength = 0
            _flushEvents.emit(flushedData)
        }
    }

    /**
     * Clear the buffer without emitting
     */
    fun clear() {
        buffer.clear()
        currentByteLength = 0
    }
}
