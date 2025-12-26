# Example: Real-Time Transcription with Jetpack Compose

This document provides a complete example of how to build a real-time transcription UI using the Speechmatics Android SDK with Jetpack Compose.

## Prerequisites

1. Add the SDK dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.speechmatics:speechmatics-android-sdk:1.0.0")
}
```

2. Add required permissions to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

## Complete Example Component

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speechmatics.sdk.audio.AudioRecorder
import com.speechmatics.sdk.audio.AudioRecorderConfig
import com.speechmatics.sdk.audio.AudioEncodingFormat
import com.speechmatics.sdk.auth.ApiType
import com.speechmatics.sdk.auth.SpeechmaticsAuth
import com.speechmatics.sdk.common.AudioEncoding
import com.speechmatics.sdk.common.AudioFormatConfig
import com.speechmatics.sdk.common.AudioType
import com.speechmatics.sdk.realtime.RealtimeClient
import com.speechmatics.sdk.realtime.RealtimeClientOptions
import com.speechmatics.sdk.realtime.models.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for managing real-time transcription state
 */
class TranscriptionViewModel : ViewModel() {

    // Configuration - replace with your API key
    private val apiKey = "YOUR_API_KEY"

    // Clients
    private val auth = SpeechmaticsAuth(apiKey)
    private val realtimeClient = RealtimeClient(
        RealtimeClientOptions(
            url = "wss://eu2.rt.speechmatics.com/v2",
            appId = "android-example"
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

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    init {
        // Collect messages from the realtime client
        viewModelScope.launch {
            realtimeClient.messages.collect { message ->
                when (message) {
                    is AddTranscript -> {
                        val text = message.results
                            .mapNotNull { result ->
                                result.alternatives?.firstOrNull()?.content
                            }
                            .joinToString(" ")
                        _transcript.value += text + " "
                        _partialTranscript.value = ""
                    }
                    is AddPartialTranscript -> {
                        val text = message.results
                            .mapNotNull { result ->
                                result.alternatives?.firstOrNull()?.content
                            }
                            .joinToString(" ")
                        _partialTranscript.value = text
                    }
                    is RealtimeError -> {
                        _error.value = "Error: ${message.type} - ${message.reason}"
                    }
                    else -> { /* Handle other messages */ }
                }
            }
        }
    }

    fun startTranscription() {
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _error.value = null
                _transcript.value = ""
                _partialTranscript.value = ""

                // Generate JWT token
                val jwt = auth.generateToken(type = ApiType.REALTIME)

                // Start transcription
                realtimeClient.start(
                    jwt = jwt,
                    transcriptionConfig = RealtimeTranscriptionConfig(
                        language = "en",
                        enablePartials = true,
                        maxDelay = 2.0
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

                // Send audio to transcription service
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
                audioRecorder.stopRecording()
                realtimeClient.stopRecognition()
                _isRecording.value = false
            } catch (e: Exception) {
                _error.value = e.message
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
 * Composable UI for real-time transcription
 */
@Composable
fun RealtimeTranscriptionScreen(
    viewModel: TranscriptionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val error by viewModel.error.collectAsState()

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

        // Transcript display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (partialTranscript.isNotEmpty()) {
                    Text(
                        text = partialTranscript,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
```

## Flow Conversational AI Example

For using the Flow API (conversational AI), here's a similar example:

```kotlin
class FlowViewModel : ViewModel() {
    private val apiKey = "YOUR_API_KEY"

    private val auth = SpeechmaticsAuth(apiKey)
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
                val jwt = auth.generateToken(type = ApiType.FLOW)

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
