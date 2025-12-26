package com.speechmatics.sdk.flow

import com.speechmatics.sdk.common.FlowErrorType
import com.speechmatics.sdk.common.SocketState
import com.speechmatics.sdk.common.SpeechmaticsFlowException
import com.speechmatics.sdk.flow.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Options for the FlowClient
 */
data class FlowClientOptions(
    /**
     * Application ID for tracking
     */
    val appId: String,

    /**
     * Audio buffering time in milliseconds (default: 10ms)
     */
    val audioBufferingMs: Int = 10,

    /**
     * Connection timeout in milliseconds (default: 10 seconds)
     */
    val connectionTimeout: Long = 10_000
)

/**
 * Flow client for Speechmatics conversational AI WebSocket API.
 *
 * @param serverUrl WebSocket server URL
 * @param options Client options
 */
class FlowClient(
    private val serverUrl: String,
    private val options: FlowClientOptions
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(options.connectionTimeout, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var serverSeqNo = 0
    private var clientSeqNo = 0

    // TTS from the server uses fixed sample rate of 16_000 samples/sec
    // The encoding is always pcm16sle (2 bytes per sample)
    private val ttsSampleRate = 16_000
    private val ttsBytesPerSample = 2
    private val ttsBytesPerMs = (ttsSampleRate * ttsBytesPerSample) / 1000

    private var jitterBuffer: JitterBuffer? = null

    private val _socketState = MutableStateFlow<SocketState?>(null)
    val socketState: StateFlow<SocketState?> = _socketState.asStateFlow()

    private val _agentAudio = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val agentAudio: SharedFlow<ShortArray> = _agentAudio.asSharedFlow()

    private val _messages = MutableSharedFlow<FlowClientIncomingMessage>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val messages: SharedFlow<FlowClientIncomingMessage> = _messages.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start a conversation session.
     *
     * @param jwt JWT token for authentication
     * @param conversationConfig Conversation configuration
     * @param audioFormat Audio format (defaults to PCM 16-bit LE at 16kHz)
     */
    suspend fun startConversation(
        jwt: String,
        conversationConfig: ConversationConfig,
        audioFormat: FlowAudioFormat = FlowAudioFormat()
    ): Unit = withContext(Dispatchers.IO) {
        val currentState = _socketState.value
        if (currentState != null && currentState != SocketState.CLOSED) {
            throw SpeechmaticsFlowException(
                FlowErrorType.SOCKET_NOT_CLOSED,
                "Cannot start connection while socket is $currentState"
            )
        }

        // Reset sequence numbers
        serverSeqNo = 0
        clientSeqNo = 0

        // Setup jitter buffer
        jitterBuffer = JitterBuffer(ttsBytesPerMs * options.audioBufferingMs)
        jitterBuffer?.let { buffer ->
            scope.launch {
                buffer.flushEvents.collect { audioBuffers ->
                    audioBuffers.forEach { audioData ->
                        _agentAudio.emit(audioData)
                    }
                }
            }
        }

        // Connect WebSocket
        connect(jwt)

        // Add timezone to template variables
        val configWithTimezone = conversationConfig.copy(
            templateVariables = conversationConfig.templateVariables + mapOf(
                "timezone" to TimeZone.getDefault().id
            )
        )

        // Wait for ConversationStarted
        suspendCancellableCoroutine<Unit> { cont ->
            val job = scope.launch {
                val startMessage = StartConversationMessage(
                    conversationConfig = configWithTimezone,
                    audioFormat = audioFormat
                )
                sendMessage(startMessage)

                withTimeout(options.connectionTimeout) {
                    messages.collect { message ->
                        when (message) {
                            is ConversationStartedMessage -> {
                                cont.resume(Unit)
                                return@collect
                            }
                            is FlowErrorMessage -> {
                                cont.resumeWithException(
                                    SpeechmaticsFlowException(
                                        FlowErrorType.SERVER_ERROR,
                                        "Error waiting for conversation start: ${message.reason}"
                                    )
                                )
                                return@collect
                            }
                            else -> { /* Continue waiting */ }
                        }
                    }
                }
            }

            // Also reject if socket closes
            val socketJob = scope.launch {
                socketState.collect { state ->
                    if (state == SocketState.CLOSED && cont.isActive) {
                        cont.resumeWithException(
                            SpeechmaticsFlowException(
                                FlowErrorType.SOCKET_CLOSED_PREMATURELY,
                                "Socket closed before conversation started"
                            )
                        )
                    }
                }
            }

            cont.invokeOnCancellation {
                job.cancel()
                socketJob.cancel()
            }
        }
    }

    /**
     * Send audio data to the conversation.
     *
     * @param pcmData Audio data as ShortArray (PCM 16-bit)
     */
    fun sendAudio(pcmData: ShortArray) {
        if (_socketState.value != SocketState.OPEN) return

        val buffer = ByteBuffer.allocate(pcmData.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        pcmData.forEach { buffer.putShort(it) }
        webSocket?.send(buffer.array().toByteString())
    }

    /**
     * Send audio data to the conversation.
     *
     * @param pcmData Audio data as FloatArray
     */
    fun sendAudio(pcmData: FloatArray) {
        if (_socketState.value != SocketState.OPEN) return

        val buffer = ByteBuffer.allocate(pcmData.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        pcmData.forEach { buffer.putFloat(it) }
        webSocket?.send(buffer.array().toByteString())
    }

    /**
     * Send raw audio bytes.
     *
     * @param data Audio data as ByteArray
     */
    fun sendAudio(data: ByteArray) {
        if (_socketState.value != SocketState.OPEN) return
        webSocket?.send(data.toByteString())
    }

    /**
     * End the conversation.
     */
    fun endConversation() {
        sendMessage(AudioEndedMessage(lastSeqNo = clientSeqNo))
        disconnectSocket()
    }

    /**
     * Close the client and release resources.
     */
    fun close() {
        disconnectSocket()
        scope.cancel()
    }

    private suspend fun connect(jwt: String): Unit = suspendCancellableCoroutine { cont ->
        val urlBuilder = StringBuilder("$serverUrl/v1/flow")
        urlBuilder.append("?jwt=$jwt")
        urlBuilder.append("&sm-app=${options.appId}")

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
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes)
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
                        SpeechmaticsFlowException(
                            FlowErrorType.SOCKET_ERROR,
                            "WebSocket connection failed",
                            t
                        )
                    )
                }
            }
        }

        webSocket = httpClient.newWebSocket(request, listener)

        cont.invokeOnCancellation {
            webSocket?.cancel()
        }
    }

    private fun handleTextMessage(text: String) {
        try {
            val message = parseIncomingMessage(text)
            if (message != null) {
                when (message) {
                    is FlowAudioAddedMessage -> {
                        clientSeqNo = message.seqNo
                    }
                    is ResponseCompletedMessage, is ResponseInterruptedMessage -> {
                        scope.launch {
                            jitterBuffer?.flush()
                        }
                    }
                    else -> { /* Other messages */ }
                }
                scope.launch {
                    _messages.emit(message)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleBinaryMessage(bytes: ByteString) {
        // Acknowledge received audio
        serverSeqNo++
        sendMessage(
            AudioReceivedMessage(
                seqNo = serverSeqNo,
                buffering = options.audioBufferingMs / 1000.0
            )
        )

        // Convert to ShortArray and enqueue
        val byteArray = bytes.toByteArray()
        val shortBuffer = ByteBuffer.wrap(byteArray)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val shortArray = ShortArray(shortBuffer.remaining())
        shortBuffer.get(shortArray)

        scope.launch {
            jitterBuffer?.enqueue(shortArray)
        }
    }

    private fun sendMessage(message: FlowClientOutgoingMessage) {
        val jsonString = when (message) {
            is StartConversationMessage -> json.encodeToString(message)
            is AudioReceivedMessage -> json.encodeToString(message)
            is AudioEndedMessage -> json.encodeToString(message)
        }
        webSocket?.send(jsonString)
    }

    private fun parseIncomingMessage(text: String): FlowClientIncomingMessage? {
        val jsonElement = json.parseToJsonElement(text)
        val messageType = jsonElement.jsonObject["message"]?.toString()?.trim('"')

        return when (messageType) {
            "ConversationStarted" -> json.decodeFromString<ConversationStartedMessage>(text)
            "AudioAdded" -> json.decodeFromString<FlowAudioAddedMessage>(text)
            "ResponseStarted" -> json.decodeFromString<ResponseStartedMessage>(text)
            "ResponseCompleted" -> json.decodeFromString<ResponseCompletedMessage>(text)
            "ResponseInterrupted" -> json.decodeFromString<ResponseInterruptedMessage>(text)
            "AddTranscript" -> json.decodeFromString<FlowAddTranscriptMessage>(text)
            "AddPartialTranscript" -> json.decodeFromString<FlowAddPartialTranscriptMessage>(text)
            "ConversationEnding" -> json.decodeFromString<ConversationEndingMessage>(text)
            "ConversationEnded" -> json.decodeFromString<ConversationEndedMessage>(text)
            "Info" -> json.decodeFromString<FlowInfoMessage>(text)
            "Warning" -> json.decodeFromString<FlowWarningMessage>(text)
            "Error" -> json.decodeFromString<FlowErrorMessage>(text)
            else -> null
        }
    }

    private fun disconnectSocket() {
        _socketState.value = SocketState.CLOSING
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }
}
