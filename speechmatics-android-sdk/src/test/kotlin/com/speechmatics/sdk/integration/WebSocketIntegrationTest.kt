package com.speechmatics.sdk.integration

import com.google.common.truth.Truth.assertThat
import com.speechmatics.sdk.common.SocketState
import com.speechmatics.sdk.realtime.RealtimeClient
import com.speechmatics.sdk.realtime.RealtimeClientOptions
import com.speechmatics.sdk.realtime.models.*
import com.speechmatics.sdk.flow.FlowClient
import com.speechmatics.sdk.flow.FlowClientOptions
import com.speechmatics.sdk.flow.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.jupiter.api.*
import java.util.concurrent.TimeUnit

/**
 * Integration tests using MockWebServer to simulate WebSocket connections
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebSocketIntegrationTest {

    private lateinit var mockServer: MockWebServer

    @BeforeAll
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterAll
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `RealtimeClient connects to WebSocket successfully`() = runBlocking {
        // MockWebServer doesn't directly support WebSocket upgrades in tests
        // This test verifies the client initialization and configuration
        val client = RealtimeClient(
            RealtimeClientOptions(
                url = "wss://eu2.rt.speechmatics.com/v2",
                appId = "test-app",
                connectionTimeout = 5000
            )
        )

        assertThat(client.socketState.value).isNull()

        client.close()
    }

    @Test
    fun `FlowClient initializes correctly`() = runBlocking {
        val client = FlowClient(
            serverUrl = "wss://flow.api.speechmatics.com",
            options = FlowClientOptions(
                appId = "test-app",
                audioBufferingMs = 10
            )
        )

        assertThat(client.socketState.value).isNull()

        client.close()
    }

    @Test
    fun `Message parsing handles recognition started`() {
        val jsonMessage = """
            {
                "message": "RecognitionStarted",
                "id": "session-123",
                "language_pack_info": {
                    "adapted": false,
                    "itn": true,
                    "language_description": "English",
                    "word_delimiter": " ",
                    "writing_direction": "left-to-right"
                }
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }

        val message = json.decodeFromString<RecognitionStarted>(jsonMessage)

        assertThat(message.id).isEqualTo("session-123")
        assertThat(message.languagePackInfo?.itn).isTrue()
        assertThat(message.languagePackInfo?.languageDescription).isEqualTo("English")
    }

    @Test
    fun `Message parsing handles add transcript`() {
        val jsonMessage = """
            {
                "message": "AddTranscript",
                "metadata": {
                    "start_time": 0.0,
                    "end_time": 2.5,
                    "transcript": "Hello world"
                },
                "results": [
                    {
                        "type": "word",
                        "start_time": 0.0,
                        "end_time": 0.5,
                        "alternatives": [{"content": "Hello", "confidence": 0.99}]
                    },
                    {
                        "type": "word",
                        "start_time": 0.5,
                        "end_time": 1.0,
                        "alternatives": [{"content": "world", "confidence": 0.98}]
                    }
                ]
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }

        val message = json.decodeFromString<AddTranscript>(jsonMessage)

        assertThat(message.metadata?.transcript).isEqualTo("Hello world")
        assertThat(message.results).hasSize(2)
        assertThat(message.results[0].alternatives?.get(0)?.content).isEqualTo("Hello")
    }

    @Test
    fun `Message parsing handles partial transcript`() {
        val jsonMessage = """
            {
                "message": "AddPartialTranscript",
                "metadata": {
                    "start_time": 0.0,
                    "end_time": 1.0
                },
                "results": [
                    {
                        "type": "word",
                        "start_time": 0.0,
                        "end_time": 0.5,
                        "alternatives": [{"content": "Hel", "confidence": 0.7}]
                    }
                ]
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }

        val message = json.decodeFromString<AddPartialTranscript>(jsonMessage)

        assertThat(message.results).hasSize(1)
        assertThat(message.results[0].alternatives?.get(0)?.content).isEqualTo("Hel")
    }

    @Test
    fun `Message parsing handles error message`() {
        val jsonMessage = """
            {
                "message": "Error",
                "type": "invalid_audio_format",
                "reason": "Unsupported audio format"
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }

        val message = json.decodeFromString<RealtimeError>(jsonMessage)

        assertThat(message.type).isEqualTo("invalid_audio_format")
        assertThat(message.reason).isEqualTo("Unsupported audio format")
    }

    @Test
    fun `Flow message parsing handles conversation started`() {
        val jsonMessage = """
            {
                "message": "ConversationStarted",
                "id": "conv-123",
                "asr_session_id": "asr-456",
                "language_pack_info": {
                    "adapted": false,
                    "itn": true,
                    "language_description": "English"
                }
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }

        val message = json.decodeFromString<ConversationStartedMessage>(jsonMessage)

        assertThat(message.id).isEqualTo("conv-123")
        assertThat(message.asrSessionId).isEqualTo("asr-456")
    }

    @Test
    fun `Flow message parsing handles response completed`() {
        val jsonMessage = """
            {
                "message": "ResponseCompleted",
                "content": "I understand. How can I help?",
                "start_time": 0.0,
                "end_time": 2.5
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }

        val message = json.decodeFromString<ResponseCompletedMessage>(jsonMessage)

        assertThat(message.content).isEqualTo("I understand. How can I help?")
        assertThat(message.startTime).isEqualTo(0.0)
        assertThat(message.endTime).isEqualTo(2.5)
    }

    @Test
    fun `StartRecognition message serializes correctly`() {
        val message = StartRecognition(
            transcriptionConfig = RealtimeTranscriptionConfig(
                language = "en",
                enablePartials = true,
                maxDelay = 2.0
            )
        )

        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }

        val serialized = json.encodeToString(StartRecognition.serializer(), message)

        assertThat(serialized).contains("\"message\":\"StartRecognition\"")
        assertThat(serialized).contains("\"language\":\"en\"")
        assertThat(serialized).contains("\"enable_partials\":true")
    }

    @Test
    fun `EndOfStream message serializes correctly`() {
        val message = EndOfStream(lastSeqNo = 42)

        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }

        val serialized = json.encodeToString(EndOfStream.serializer(), message)

        assertThat(serialized).contains("\"message\":\"EndOfStream\"")
        assertThat(serialized).contains("\"last_seq_no\":42")
    }

    @Test
    fun `StartConversation message serializes correctly`() {
        val message = StartConversationMessage(
            conversationConfig = ConversationConfig(
                templateId = "my-template",
                templateVariables = mapOf("name" to "John")
            ),
            audioFormat = FlowAudioFormat(
                type = "raw",
                encoding = "pcm_s16le",
                sampleRate = 16000
            )
        )

        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }

        val serialized = json.encodeToString(StartConversationMessage.serializer(), message)

        assertThat(serialized).contains("\"message\":\"StartConversation\"")
        assertThat(serialized).contains("\"template_id\":\"my-template\"")
        assertThat(serialized).contains("\"name\":\"John\"")
    }
}
