# Example: Real-Time Transcription with Jetpack Compose

This document provides a complete example of how to build a real-time transcription UI using the Speechmatics Android SDK with Jetpack Compose, including speaker diarization and proper text formatting.

## Prerequisites

1. Add the SDK dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.dreamteam:speechmatics-android:1.0.0")
}
```

2. Add required permissions to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

## Architecture Overview

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Your Backend  │────▶│  Android App    │────▶│  Speechmatics   │
│  (API Key here) │     │  (JWT token)    │     │  WebSocket API  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        │ POST /v1/api_keys     │ start(jwt, config)    │
        │ Returns JWT           │ sendAudio(bytes)      │
        │                       │◀──────────────────────│
        │                       │ AddTranscript         │
        │                       │ AddPartialTranscript  │
        │                       │ EndOfTranscript       │
```

## Message Types

| Message | Description |
|---------|-------------|
| `AddPartialTranscript` | Interim results that may change as more audio is processed |
| `AddTranscript` | Final, confirmed transcript segment |
| `EndOfTranscript` | All audio has been processed, safe to disconnect |
| `RealtimeError` | Error occurred during transcription |

## Complete Example

```kotlin
package com.example.transcription

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speechmatics.sdk.audio.AudioRecorder
import com.speechmatics.sdk.audio.AudioRecorderConfig
import com.speechmatics.sdk.audio.AudioEncodingFormat
import com.speechmatics.sdk.common.AudioEncoding
import com.speechmatics.sdk.common.AudioFormatConfig
import com.speechmatics.sdk.common.AudioType
import com.speechmatics.sdk.realtime.RealtimeClient
import com.speechmatics.sdk.realtime.RealtimeClientOptions
import com.speechmatics.sdk.realtime.models.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for managing real-time transcription with speaker diarization
 */
class TranscriptionViewModel(
    private val tokenProvider: suspend () -> String  // Inject your backend token fetcher
) : ViewModel() {

    // Clients
    private val realtimeClient = RealtimeClient(
        RealtimeClientOptions(
            url = "wss://eu2.rt.speechmatics.com/v2",
            appId = "my-app"
        )
    )
    private val audioRecorder = AudioRecorder(
        AudioRecorderConfig(
            sampleRate = 16000,
            encoding = AudioEncodingFormat.PCM_16BIT,
            echoCancellation = true,
            noiseSuppression = true
        )
    )

    // UI State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Accumulated final transcript (confirmed text)
    private val _finalTranscript = MutableStateFlow("")
    val finalTranscript: StateFlow<String> = _finalTranscript.asStateFlow()

    // Current partial (unconfirmed, may change)
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    // Track current speaker for formatting
    private var lastFinalSpeaker: String? = null
    private var endOfTranscriptDeferred: CompletableDeferred<Unit>? = null

    init {
        // Collect messages from the realtime client
        viewModelScope.launch {
            realtimeClient.messages.collect { message ->
                handleMessage(message)
            }
        }
    }

    private fun handleMessage(message: RealtimeMessage) {
        when (message) {
            is AddTranscript -> {
                // Final transcript - append to accumulated text
                val (text, speaker) = buildTranscriptText(message.results, lastFinalSpeaker)
                _finalTranscript.value += text
                _partialTranscript.value = ""
                lastFinalSpeaker = speaker
            }
            is AddPartialTranscript -> {
                // Partial - show but don't accumulate (will be replaced)
                val (text, _) = buildTranscriptText(message.results, lastFinalSpeaker)
                _partialTranscript.value = text
            }
            is EndOfTranscript -> {
                // All audio processed
                endOfTranscriptDeferred?.complete(Unit)
            }
            is RealtimeError -> {
                _error.value = "Error: ${message.type} - ${message.reason}"
            }
            else -> { /* RecognitionStarted, AudioAdded, etc. */ }
        }
    }

    /**
     * Build formatted transcript text with speaker labels.
     * Returns the formatted text and the last speaker encountered.
     */
    private fun buildTranscriptText(
        results: List<RecognitionResult>,
        previousSpeaker: String?
    ): Pair<String, String?> {
        val sb = StringBuilder()
        var currentSpeaker = previousSpeaker

        for (result in results) {
            // Get speaker from alternatives or result
            val speaker = result.alternatives?.firstOrNull()?.speaker ?: result.speaker

            // Add speaker label on change
            if (speaker != null && speaker != currentSpeaker) {
                if (sb.isNotEmpty() || _finalTranscript.value.isNotEmpty()) {
                    sb.append("\n\n")
                }
                sb.append("$speaker: ")
                currentSpeaker = speaker
            }

            // Get word content
            val content = result.alternatives?.firstOrNull()?.content ?: result.content ?: continue

            // Add spacing for words (not punctuation)
            if (result.type == RecognitionResultType.WORD) {
                if (sb.isNotEmpty() && !sb.endsWith(": ") && !sb.endsWith("\n")) {
                    sb.append(" ")
                }
            }

            sb.append(content)
        }

        return Pair(sb.toString(), currentSpeaker)
    }

    fun startTranscription() {
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _error.value = null
                _finalTranscript.value = ""
                _partialTranscript.value = ""
                lastFinalSpeaker = null

                // Get JWT from backend (keeps API key secure)
                val jwt = tokenProvider()

                // Start transcription with speaker diarization
                realtimeClient.start(
                    jwt = jwt,
                    transcriptionConfig = RealtimeTranscriptionConfig(
                        language = "en",
                        enablePartials = true,
                        operatingPoint = "enhanced",
                        diarization = "speaker",
                        speakerDiarizationConfig = RealtimeSpeakerDiarizationConfig(
                            maxSpeakers = 10
                        )
                    ),
                    audioFormat = AudioFormatConfig(
                        type = AudioType.RAW,
                        encoding = AudioEncoding.PCM_S16LE,
                        sampleRate = 16000
                    )
                )

                // Start recording
                audioRecorder.startRecording()
                _isRecording.value = true
                _isConnecting.value = false

                // Stream audio to transcription service
                audioRecorder.audioDataPcm16.collect { audioData ->
                    realtimeClient.sendAudio(audioData)
                }

            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                _isConnecting.value = false
                _isRecording.value = false
            }
        }
    }

    fun stopTranscription() {
        viewModelScope.launch {
            try {
                // Stop recording first
                audioRecorder.stopRecording()

                // Signal end of audio and wait for final transcripts
                endOfTranscriptDeferred = CompletableDeferred()
                realtimeClient.stopRecognition(noTimeout = true)

                // Wait for EndOfTranscript (with timeout)
                withTimeoutOrNull(3000) {
                    endOfTranscriptDeferred?.await()
                }

                _isRecording.value = false
                endOfTranscriptDeferred = null

            } catch (e: Exception) {
                _error.value = e.message
                _isRecording.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        realtimeClient.close()
    }
}

/**
 * Composable UI for real-time transcription with styled output
 */
@Composable
fun RealtimeTranscriptionScreen(
    viewModel: TranscriptionViewModel
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val finalTranscript by viewModel.finalTranscript.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val error by viewModel.error.collectAsState()
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when transcript updates
    LaunchedEffect(finalTranscript, partialTranscript) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Real-Time Transcription",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error display
        error?.let { errorMessage ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Transcript display with styled text
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                // Styled transcript with speaker labels bold
                Text(
                    text = buildAnnotatedString {
                        // Final transcript (confirmed) - normal styling
                        appendStyledTranscript(finalTranscript, isPartial = false)

                        // Partial transcript (unconfirmed) - gray/italic
                        if (partialTranscript.isNotEmpty()) {
                            appendStyledTranscript(partialTranscript, isPartial = true)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons
        if (!hasPermission) {
            Button(
                onClick = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            ) {
                Text("Grant Microphone Permission")
            }
        } else {
            Button(
                onClick = {
                    if (isRecording) {
                        viewModel.stopTranscription()
                    } else {
                        viewModel.startTranscription()
                    }
                },
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isRecording) "Stop Recording" else "Start Recording")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status indicator
        Text(
            text = when {
                isConnecting -> "Connecting..."
                isRecording -> "Recording and transcribing..."
                else -> "Ready to record"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Extension to append styled transcript text.
 * Speaker labels (e.g., "S1:") are bold, partials are gray.
 */
@Composable
private fun AnnotatedString.Builder.appendStyledTranscript(
    text: String,
    isPartial: Boolean
) {
    val speakerPattern = Regex("(S\\d+):")
    var lastEnd = 0

    speakerPattern.findAll(text).forEach { match ->
        // Append text before speaker label
        if (match.range.first > lastEnd) {
            val beforeText = text.substring(lastEnd, match.range.first)
            if (isPartial) {
                withStyle(SpanStyle(color = Color.Gray)) {
                    append(beforeText)
                }
            } else {
                append(beforeText)
            }
        }

        // Append speaker label in bold
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = if (isPartial) Color.Gray else Color.Unspecified
            )
        ) {
            append(match.value)
        }

        lastEnd = match.range.last + 1
    }

    // Append remaining text
    if (lastEnd < text.length) {
        val remaining = text.substring(lastEnd)
        if (isPartial) {
            withStyle(SpanStyle(color = Color.Gray)) {
                append(remaining)
            }
        } else {
            append(remaining)
        }
    }
}
```

## UI Styling

The example above demonstrates:

1. **Final vs Partial text**: Final transcript is shown in normal color, partial (unconfirmed) text is shown in gray
2. **Speaker labels**: Speaker labels like "S1:" are rendered in bold
3. **Auto-scroll**: Transcript automatically scrolls to show newest text
4. **Speaker separation**: Double newlines between different speakers for readability

**Visual Example:**

```
┌──────────────────────────────────────┐
│ S1: Hello, welcome to the meeting.   │  ← Final (black, bold label)
│                                      │
│ S2: Thanks for having me today.      │  ← Final (black, bold label)
│                                      │
│ S1: Let's discuss the project        │  ← Partial (gray, still typing)
└──────────────────────────────────────┘
```

## Flow Conversational AI Example

For using the Flow API (conversational AI), here's a similar example:

```kotlin
class FlowViewModel(
    private val tokenProvider: suspend () -> String
) : ViewModel() {

    private val flowClient = FlowClient(
        serverUrl = "wss://flow.api.speechmatics.com",
        options = FlowClientOptions(
            appId = "android-example",
            audioBufferingMs = 10
        )
    )
    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()

    private val _isConversing = MutableStateFlow(false)
    val isConversing: StateFlow<Boolean> = _isConversing.asStateFlow()

    init {
        // Play agent audio responses
        viewModelScope.launch {
            flowClient.agentAudio.collect { audioData ->
                audioPlayer.play(audioData)
            }
        }

        // Handle messages
        viewModelScope.launch {
            flowClient.messages.collect { message ->
                when (message) {
                    is ResponseStartedMessage -> {
                        // Agent started speaking
                    }
                    is ResponseCompletedMessage -> {
                        // Agent finished speaking
                    }
                    is FlowAddTranscriptMessage -> {
                        // User transcript received
                    }
                    else -> {}
                }
            }
        }
    }

    fun startConversation() {
        viewModelScope.launch {
            try {
                val jwt = tokenProvider()

                flowClient.startConversation(
                    jwt = jwt,
                    conversationConfig = ConversationConfig(
                        templateId = "your-template-id",
                        templateVariables = mapOf(
                            "user_name" to "Android User"
                        )
                    )
                )

                audioRecorder.startRecording()
                audioPlayer.initialize()
                _isConversing.value = true

                // Send audio to Flow
                audioRecorder.audioDataPcm16.collect { audioData ->
                    flowClient.sendAudio(audioData)
                }

            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun endConversation() {
        audioRecorder.stopRecording()
        flowClient.endConversation()
        audioPlayer.stop()
        _isConversing.value = false
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        audioPlayer.release()
        flowClient.close()
    }
}
```

## Key Features

1. **Audio Recording**: The `AudioRecorder` class captures audio with configurable format (PCM16 or Float32) and applies audio effects (echo cancellation, noise suppression, auto gain control).

2. **Real-time Streaming**: Audio data is streamed via WebSocket using Kotlin Flows for reactive programming.

3. **Partial Transcripts**: The SDK supports partial (interim) transcripts for real-time feedback.

4. **Flow API**: Full support for conversational AI with bidirectional audio streaming.

5. **Audio Playback**: The `AudioPlayer` class handles playing agent audio responses with volume control.

## Tips

- Always handle the `RECORD_AUDIO` permission before starting recording
- Use coroutines and Flow for handling async operations
- Clean up resources in `onCleared()` to prevent memory leaks
- Handle network errors gracefully with retry logic if needed
