package com.speechmatics.sdk.common

import com.google.common.truth.Truth.assertThat
import com.speechmatics.sdk.batch.models.*
import com.speechmatics.sdk.realtime.models.*
import com.speechmatics.sdk.flow.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `TranscriptionConfig serializes correctly`() {
        val config = TranscriptionConfig(
            language = "en",
            domain = "finance",
            enableEntities = true,
            diarization = Diarization.SPEAKER
        )

        val serialized = json.encodeToString(config)

        assertThat(serialized).contains("\"language\":\"en\"")
        assertThat(serialized).contains("\"domain\":\"finance\"")
        assertThat(serialized).contains("\"enable_entities\":true")
        assertThat(serialized).contains("\"diarization\":\"speaker\"")
    }

    @Test
    fun `TranscriptionConfig deserializes correctly`() {
        val jsonStr = """
            {
                "language": "en",
                "domain": "medical",
                "enable_entities": true,
                "diarization": "channel"
            }
        """.trimIndent()

        val config = json.decodeFromString<TranscriptionConfig>(jsonStr)

        assertThat(config.language).isEqualTo("en")
        assertThat(config.domain).isEqualTo("medical")
        assertThat(config.enableEntities).isTrue()
        assertThat(config.diarization).isEqualTo(Diarization.CHANNEL)
    }

    @Test
    fun `JobConfig serializes correctly`() {
        val config = JobConfig(
            type = JobType.TRANSCRIPTION,
            transcriptionConfig = TranscriptionConfig(language = "en")
        )

        val serialized = json.encodeToString(config)

        assertThat(serialized).contains("\"type\":\"transcription\"")
        assertThat(serialized).contains("\"transcription_config\"")
    }

    @Test
    fun `JobDetails deserializes correctly`() {
        val jsonStr = """
            {
                "id": "job-123",
                "created_at": "2024-01-01T00:00:00Z",
                "status": "done",
                "duration": 120.5
            }
        """.trimIndent()

        val details = json.decodeFromString<JobDetails>(jsonStr)

        assertThat(details.id).isEqualTo("job-123")
        assertThat(details.status).isEqualTo(JobStatus.DONE)
        assertThat(details.duration).isEqualTo(120.5)
    }

    @Test
    fun `RealtimeTranscriptionConfig serializes correctly`() {
        val config = RealtimeTranscriptionConfig(
            language = "en",
            enablePartials = true,
            maxDelay = 2.0
        )

        val serialized = json.encodeToString(config)

        assertThat(serialized).contains("\"language\":\"en\"")
        assertThat(serialized).contains("\"enable_partials\":true")
        assertThat(serialized).contains("\"max_delay\":2.0")
    }

    @Test
    fun `RecognitionResult deserializes correctly`() {
        val jsonStr = """
            {
                "type": "word",
                "start_time": 0.5,
                "end_time": 1.0,
                "alternatives": [
                    {"content": "hello", "confidence": 0.99}
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString<RecognitionResult>(jsonStr)

        assertThat(result.type).isEqualTo(RecognitionResultType.WORD)
        assertThat(result.startTime).isEqualTo(0.5)
        assertThat(result.alternatives).hasSize(1)
        assertThat(result.alternatives!![0].content).isEqualTo("hello")
    }

    @Test
    fun `FlowConversationConfig serializes correctly`() {
        val config = ConversationConfig(
            templateId = "my-template",
            templateVariables = mapOf("name" to "John")
        )

        val serialized = json.encodeToString(config)

        assertThat(serialized).contains("\"template_id\":\"my-template\"")
        assertThat(serialized).contains("\"template_variables\"")
        assertThat(serialized).contains("\"name\":\"John\"")
    }

    @Test
    fun `StartConversationMessage serializes correctly`() {
        val message = StartConversationMessage(
            conversationConfig = ConversationConfig(
                templateId = "test",
                templateVariables = emptyMap()
            ),
            audioFormat = FlowAudioFormat()
        )

        val serialized = json.encodeToString(message)

        assertThat(serialized).contains("\"message\":\"StartConversation\"")
        assertThat(serialized).contains("\"audio_format\"")
    }

    @Test
    fun `AudioFormatConfig has correct defaults`() {
        val config = AudioFormatConfig()

        assertThat(config.type).isEqualTo(AudioType.RAW)
        assertThat(config.encoding).isEqualTo(AudioEncoding.PCM_S16LE)
        assertThat(config.sampleRate).isEqualTo(16000)
    }

    @Test
    fun `AudioFormatConfig serializes correctly`() {
        val config = AudioFormatConfig(
            type = AudioType.RAW,
            encoding = AudioEncoding.PCM_F32LE,
            sampleRate = 48000
        )

        val serialized = json.encodeToString(config)

        assertThat(serialized).contains("\"type\":\"raw\"")
        assertThat(serialized).contains("\"encoding\":\"pcm_f32le\"")
        assertThat(serialized).contains("\"sample_rate\":48000")
    }

    @Test
    fun `SocketState enum values are correct`() {
        assertThat(SocketState.CONNECTING.name).isEqualTo("CONNECTING")
        assertThat(SocketState.OPEN.name).isEqualTo("OPEN")
        assertThat(SocketState.CLOSING.name).isEqualTo("CLOSING")
        assertThat(SocketState.CLOSED.name).isEqualTo("CLOSED")
    }

    @Test
    fun `Exception types have correct structure`() {
        val authException = SpeechmaticsAuthException(
            type = AuthErrorType.UNAUTHORIZED,
            message = "Test error"
        )
        assertThat(authException.type).isEqualTo(AuthErrorType.UNAUTHORIZED)
        assertThat(authException.message).isEqualTo("Test error")

        val batchException = SpeechmaticsBatchException(
            message = "Batch error",
            statusCode = 400
        )
        assertThat(batchException.statusCode).isEqualTo(400)

        val realtimeException = SpeechmaticsRealtimeException("Realtime error")
        assertThat(realtimeException.message).isEqualTo("Realtime error")

        val flowException = SpeechmaticsFlowException(
            type = FlowErrorType.TIMEOUT,
            message = "Flow timeout"
        )
        assertThat(flowException.type).isEqualTo(FlowErrorType.TIMEOUT)
    }
}
