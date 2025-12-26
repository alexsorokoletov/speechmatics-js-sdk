package com.speechmatics.sdk.realtime

import com.speechmatics.sdk.common.AudioFormatConfig
import com.speechmatics.sdk.common.SocketState
import com.speechmatics.sdk.common.SpeechmaticsRealtimeException
import com.speechmatics.sdk.realtime.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Options for the RealtimeClient
 */
data class RealtimeClientOptions(
    /**
     * URL of the Speechmatics Realtime API
     * Defaults to EU endpoint: wss://eu2.rt.speechmatics.com/v2
     */
    val url: String = "wss://eu2.rt.speechmatics.com/v2",

    /**
     * String identifying your app to the Speechmatics API
     */
    val appId: String? = null,

    /**
     * Enable legacy mode (opts out of incremental rescoring)
     */
    val enableLegacy: Boolean = false,

    /**
     * Connection timeout in milliseconds (default: 10 seconds)
     */
    val connectionTimeout: Long = 10_000
)

/**
 * Real-time transcription client for Speechmatics WebSocket API.
 */
class RealtimeClient(
    private val options: RealtimeClientOptions = RealtimeClientOptions()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false  // Don't send null values
        classDiscriminator = "message"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(options.connectionTimeout, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var lastAudioAddedSeqNo = 0

    private val _socketState = MutableStateFlow<SocketState?>(null)
    val socketState: StateFlow<SocketState?> = _socketState.asStateFlow()

    private val _messages = MutableSharedFlow<RealtimeServerMessage>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val messages: SharedFlow<RealtimeServerMessage> = _messages.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start real-time transcription session.
     *
     * @param jwt JWT token for authentication
     * @param transcriptionConfig Transcription configuration
     * @param audioFormat Audio format configuration (defaults to raw PCM)
     * @return RecognitionStarted message from server
     */
    suspend fun start(
        jwt: String,
        transcriptionConfig: RealtimeTranscriptionConfig,
        audioFormat: AudioFormatConfig = AudioFormatConfig()
    ): RecognitionStarted = withContext(Dispatchers.IO) {
        // Connect WebSocket
        connect(jwt)

        // Wait for RecognitionStarted
        suspendCancellableCoroutine { cont ->
            val job = scope.launch {
                val startMessage = StartRecognition(
                    audioFormat = audioFormat,
                    transcriptionConfig = transcriptionConfig
                )
                sendMessage(startMessage)

                withTimeout(options.connectionTimeout) {
                    messages.collect { message ->
                        when (message) {
                            is RecognitionStarted -> {
                                cont.resume(message)
                                return@collect
                            }
                            is RealtimeError -> {
                                cont.resumeWithException(
                                    SpeechmaticsRealtimeException("Error: ${message.type} - ${message.reason}")
                                )
                                return@collect
                            }
                            else -> { /* Continue waiting */ }
                        }
                    }
                }
            }

            cont.invokeOnCancellation { job.cancel() }
        }
    }

    /**
     * Send audio data to the server.
     *
     * @param data Audio data as ByteArray
     */
    fun sendAudio(data: ByteArray) {
        val socket = webSocket
            ?: throw SpeechmaticsRealtimeException("Socket not ready to receive audio")

        if (_socketState.value != SocketState.OPEN) {
            throw SpeechmaticsRealtimeException("Socket not ready to receive audio")
        }

        socket.send(data.toByteString())
    }

    /**
     * Send audio data as PCM 16-bit samples.
     *
     * @param data Audio data as ShortArray (PCM 16-bit)
     */
    fun sendAudio(data: ShortArray) {
        val buffer = ByteBuffer.allocate(data.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        data.forEach { buffer.putShort(it) }
        sendAudio(buffer.array())
    }

    /**
     * Send audio data as Float32 samples.
     *
     * @param data Audio data as FloatArray
     */
    fun sendAudio(data: FloatArray) {
        val buffer = ByteBuffer.allocate(data.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        data.forEach { buffer.putFloat(it) }
        sendAudio(buffer.array())
    }

    /**
     * Stop the recognition session gracefully.
     *
     * @param noTimeout If true, don't wait for EndOfTranscript acknowledgement
     */
    suspend fun stopRecognition(noTimeout: Boolean = false): Unit = withContext(Dispatchers.IO) {
        if (noTimeout) {
            sendMessage(EndOfStream(lastSeqNo = lastAudioAddedSeqNo))
            return@withContext
        }

        suspendCancellableCoroutine { cont ->
            val job = scope.launch {
                sendMessage(EndOfStream(lastSeqNo = lastAudioAddedSeqNo))

                withTimeout(options.connectionTimeout) {
                    messages.collect { message ->
                        if (message is EndOfTranscript) {
                            webSocket?.close(1000, "Normal closure")
                            cont.resume(Unit)
                            return@collect
                        }
                    }
                }
            }

            cont.invokeOnCancellation { job.cancel() }
        }
    }

    /**
     * Update transcription configuration mid-session.
     */
    fun setRecognitionConfig(config: MidSessionTranscriptionConfig) {
        sendMessage(SetRecognitionConfig(transcriptionConfig = config))
    }

    /**
     * Get speaker information.
     *
     * @param final If true, returns final speaker assignments
     * @param timeout Timeout in milliseconds (optional)
     * @return SpeakersResult from server
     */
    suspend fun getSpeakers(
        final: Boolean = false,
        timeout: Long? = null
    ): SpeakersResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val job = scope.launch {
                sendMessage(GetSpeakers(final = final))

                val effectiveTimeout = timeout ?: options.connectionTimeout

                withTimeout(effectiveTimeout) {
                    messages.collect { message ->
                        when (message) {
                            is SpeakersResult -> {
                                cont.resume(message)
                                return@collect
                            }
                            is RealtimeError -> {
                                cont.resumeWithException(
                                    SpeechmaticsRealtimeException("Error: ${message.type}")
                                )
                                return@collect
                            }
                            else -> { /* Continue waiting */ }
                        }
                    }
                }
            }

            cont.invokeOnCancellation { job.cancel() }
        }
    }

    /**
     * Close the WebSocket connection.
     */
    fun close() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
        scope.cancel()
    }

    private suspend fun connect(jwt: String): Unit = suspendCancellableCoroutine { cont ->
        val urlBuilder = StringBuilder(options.url)
        urlBuilder.append("?jwt=$jwt")
        options.appId?.let { urlBuilder.append("&sm-app=$it") }
        if (options.enableLegacy) {
            urlBuilder.append("&sm-enable-legacy-rt=true")
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .build()

        _socketState.value = SocketState.CONNECTING

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _socketState.value = SocketState.OPEN
                cont.resume(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = parseServerMessage(text)
                    if (message != null) {
                        if (message is AudioAdded) {
                            lastAudioAddedSeqNo = message.seqNo
                        }
                        scope.launch {
                            _messages.emit(message)
                        }
                    }
                } catch (e: Exception) {
                    // Log but don't crash on parse errors
                    e.printStackTrace()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary messages are not expected from server
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _socketState.value = SocketState.CLOSING
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _socketState.value = SocketState.CLOSED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _socketState.value = SocketState.CLOSED
                if (cont.isActive) {
                    cont.resumeWithException(
                        SpeechmaticsRealtimeException("WebSocket connection failed", t)
                    )
                }
            }
        }

        webSocket = httpClient.newWebSocket(request, listener)

        cont.invokeOnCancellation {
            webSocket?.cancel()
        }
    }

    private fun sendMessage(message: RealtimeClientMessage) {
        val jsonString = when (message) {
            is StartRecognition -> json.encodeToString(message)
            is EndOfStream -> json.encodeToString(message)
            is SetRecognitionConfig -> json.encodeToString(message)
            is GetSpeakers -> json.encodeToString(message)
            is AddAudio -> json.encodeToString(message)
        }
        webSocket?.send(jsonString)
    }

    private fun parseServerMessage(text: String): RealtimeServerMessage? {
        // Parse based on message type
        val jsonElement = json.parseToJsonElement(text)
        val messageType = jsonElement.jsonObject["message"]?.toString()?.trim('"')

        return when (messageType) {
            "RecognitionStarted" -> json.decodeFromString<RecognitionStarted>(text)
            "AudioAdded" -> json.decodeFromString<AudioAdded>(text)
            "AddPartialTranscript" -> json.decodeFromString<AddPartialTranscript>(text)
            "AddTranscript" -> json.decodeFromString<AddTranscript>(text)
            "EndOfTranscript" -> json.decodeFromString<EndOfTranscript>(text)
            "SpeakersResult" -> json.decodeFromString<SpeakersResult>(text)
            "Warning" -> json.decodeFromString<RealtimeWarning>(text)
            "Error" -> json.decodeFromString<RealtimeError>(text)
            "Info" -> json.decodeFromString<RealtimeInfo>(text)
            else -> null
        }
    }
}
