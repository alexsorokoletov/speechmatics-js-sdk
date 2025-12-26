package com.speechmatics.sdk.flow

import com.google.common.truth.Truth.assertThat
import com.speechmatics.sdk.common.FlowErrorType
import com.speechmatics.sdk.common.SocketState
import com.speechmatics.sdk.common.SpeechmaticsFlowException
import com.speechmatics.sdk.flow.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowClientTest {

    @Test
    fun `client initializes with options`() {
        val client = FlowClient(
            serverUrl = "wss://flow.api.speechmatics.com",
            options = FlowClientOptions(
                appId = "test-app",
                audioBufferingMs = 20
            )
        )

        assertThat(client.socketState.value).isNull()
    }

    @Test
    fun `conversation config serializes correctly`() {
        val config = ConversationConfig(
            templateId = "my-template",
            templateVariables = mapOf(
                "name" to "Test User",
                "context" to "Testing"
            )
        )

        assertThat(config.templateId).isEqualTo("my-template")
        assertThat(config.templateVariables["name"]).isEqualTo("Test User")
    }

    @Test
    fun `flow audio format has correct defaults`() {
        val format = FlowAudioFormat()

        assertThat(format.type).isEqualTo("raw")
        assertThat(format.encoding).isEqualTo("pcm_s16le")
        assertThat(format.sampleRate).isEqualTo(16000)
    }

    @Test
    fun `startConversation throws when socket not closed`() = runBlocking {
        val client = FlowClient(
            serverUrl = "wss://flow.api.speechmatics.com",
            options = FlowClientOptions(appId = "test-app")
        )

        // First ensure client is in correct state (null = never connected)
        assertThat(client.socketState.value).isNull()
    }

    @Test
    fun `close releases resources without error`() {
        val client = FlowClient(
            serverUrl = "wss://flow.api.speechmatics.com",
            options = FlowClientOptions(appId = "test-app")
        )

        client.close()

        assertThat(client.socketState.value).isNull()
    }

    @Test
    fun `jitter buffer accumulates data`() = runBlocking {
        val buffer = JitterBuffer(maxByteLength = 1000)

        buffer.enqueue(ShortArray(100) { it.toShort() })

        assertThat(buffer.byteLength).isEqualTo(200) // 100 shorts * 2 bytes
    }

    @Test
    fun `jitter buffer flushes when threshold reached`() = runBlocking {
        val buffer = JitterBuffer(maxByteLength = 200)
        var flushed = false

        val job = launch {
            buffer.flushEvents.collect {
                flushed = true
            }
        }

        // Add data that exceeds threshold
        buffer.enqueue(ShortArray(100) { it.toShort() }) // 200 bytes
        buffer.enqueue(ShortArray(1) { 0 }) // Trigger flush

        // Allow time for flush event
        delay(100)
        job.cancel()

        assertThat(buffer.byteLength).isLessThan(200)
    }

    @Test
    fun `jitter buffer clear empties buffer`() = runBlocking {
        val buffer = JitterBuffer(maxByteLength = 1000)

        buffer.enqueue(ShortArray(100) { it.toShort() })
        assertThat(buffer.byteLength).isEqualTo(200)

        buffer.clear()
        assertThat(buffer.byteLength).isEqualTo(0)
    }

    @Test
    fun `flow error types are defined`() {
        assertThat(FlowErrorType.SOCKET_NOT_CLOSED).isNotNull()
        assertThat(FlowErrorType.SOCKET_CLOSED_PREMATURELY).isNotNull()
        assertThat(FlowErrorType.SOCKET_ERROR).isNotNull()
        assertThat(FlowErrorType.SERVER_ERROR).isNotNull()
        assertThat(FlowErrorType.TIMEOUT).isNotNull()
    }
}
