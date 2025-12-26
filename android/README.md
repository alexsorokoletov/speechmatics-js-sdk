# Speechmatics Android SDK

Official Android SDK for Speechmatics speech recognition APIs. This SDK provides access to:

- **Batch Transcription** - Upload audio files for asynchronous transcription
- **Real-time Transcription** - Stream audio for live transcription via WebSocket
- **Flow API** - Conversational AI with bidirectional audio streaming
- **Audio Utilities** - Android-native audio recording and playback

## Requirements

- Android SDK 30+ (Android 11+)
- Kotlin 1.9+
- Gradle 8.x

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.speechmatics:speechmatics-android-sdk:1.0.0")
}
```

## Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

## Quick Start

### Authentication

Generate temporary JWT tokens for client-side API access:

```kotlin
val auth = SpeechmaticsAuth(apiKey = "YOUR_API_KEY")

// For real-time transcription
val rtToken = auth.generateToken(type = ApiType.REALTIME)

// For batch transcription (requires clientRef)
val batchToken = auth.generateToken(
    type = ApiType.BATCH,
    clientRef = "my-client-reference"
)

// For Flow API
val flowToken = auth.generateToken(type = ApiType.FLOW)
```

### Real-time Transcription

```kotlin
val client = RealtimeClient(
    RealtimeClientOptions(
        url = "wss://eu2.rt.speechmatics.com/v2",
        appId = "my-app"
    )
)

// Start transcription
val result = client.start(
    jwt = token,
    transcriptionConfig = RealtimeTranscriptionConfig(
        language = "en",
        enablePartials = true
    )
)

// Listen for transcripts
launch {
    client.messages.collect { message ->
        when (message) {
            is AddTranscript -> {
                println("Final: ${message.results}")
            }
            is AddPartialTranscript -> {
                println("Partial: ${message.results}")
            }
        }
    }
}

// Send audio data
client.sendAudio(audioData) // ShortArray, FloatArray, or ByteArray

// Stop transcription
client.stopRecognition()
```

### Batch Transcription

```kotlin
val client = BatchClient(
    apiKey = "YOUR_API_KEY",
    appId = "my-app"
)

// Transcribe a file
val result = client.transcribe(
    input = JobInput.FileInput(audioFile),
    transcriptionConfig = TranscriptionConfig(language = "en")
)

when (result) {
    is TranscriptionResult.JsonResult -> {
        println(result.response.results)
    }
    is TranscriptionResult.TextResult -> {
        println(result.text)
    }
}

// Or use URL input
val urlResult = client.transcribe(
    input = JobInput.UrlInput(url = "https://example.com/audio.wav"),
    transcriptionConfig = TranscriptionConfig(language = "en")
)
```

### Flow API (Conversational AI)

```kotlin
val client = FlowClient(
    serverUrl = "wss://flow.api.speechmatics.com",
    options = FlowClientOptions(
        appId = "my-app",
        audioBufferingMs = 10
    )
)

// Start conversation
client.startConversation(
    jwt = token,
    conversationConfig = ConversationConfig(
        templateId = "your-template-id",
        templateVariables = mapOf("name" to "User")
    )
)

// Listen for agent audio
launch {
    client.agentAudio.collect { audio ->
        audioPlayer.play(audio)
    }
}

// Send user audio
client.sendAudio(audioData)

// End conversation
client.endConversation()
```

### Audio Recording

```kotlin
val recorder = AudioRecorder(
    AudioRecorderConfig(
        sampleRate = 16000,
        encoding = AudioEncodingFormat.PCM_16BIT,
        echoCancellation = true,
        noiseSuppression = true,
        autoGainControl = true
    )
)

// Start recording
recorder.startRecording()

// Collect audio data
launch {
    recorder.audioDataPcm16.collect { data ->
        // Send to transcription
        realtimeClient.sendAudio(data)
    }
}

// Stop recording
recorder.stopRecording()

// Release resources
recorder.release()
```

### Audio Playback

```kotlin
val player = AudioPlayer(
    AudioPlayerConfig(
        sampleRate = 16000,
        initialVolume = 100
    )
)

// Play audio (ShortArray, FloatArray, or ByteArray)
player.play(audioData)

// Adjust volume
player.setVolume(80)

// Stop and release
player.stop()
player.release()
```

## API Reference

### SpeechmaticsAuth

| Method | Description |
|--------|-------------|
| `generateToken(type, ttl, region, clientRef)` | Generate a temporary JWT token |

### BatchClient

| Method | Description |
|--------|-------------|
| `transcribe(input, config, format, timeout)` | Transcribe audio file (blocking) |
| `createTranscriptionJob(input, config)` | Create transcription job |
| `getJob(jobId)` | Get job status |
| `getJobResult(jobId, format)` | Get transcription result |
| `listJobs(filters)` | List all jobs |
| `deleteJob(jobId, force)` | Delete a job |

### RealtimeClient

| Method | Description |
|--------|-------------|
| `start(jwt, config, audioFormat)` | Start transcription session |
| `sendAudio(data)` | Send audio data |
| `stopRecognition(noTimeout)` | Stop transcription |
| `setRecognitionConfig(config)` | Update config mid-session |
| `getSpeakers(final, timeout)` | Get speaker information |
| `close()` | Close connection |

### FlowClient

| Method | Description |
|--------|-------------|
| `startConversation(jwt, config, audioFormat)` | Start conversation |
| `sendAudio(data)` | Send audio data |
| `endConversation()` | End conversation |
| `close()` | Close connection |

### AudioRecorder

| Method | Description |
|--------|-------------|
| `hasPermission(context)` | Check if RECORD_AUDIO permission granted |
| `startRecording()` | Start recording |
| `stopRecording()` | Stop recording |
| `release()` | Release all resources |

### AudioPlayer

| Method | Description |
|--------|-------------|
| `initialize()` | Initialize AudioTrack |
| `play(data)` | Play audio data |
| `setVolume(percent)` | Set volume (0-100) |
| `pause()` | Pause playback |
| `stop()` | Stop playback |
| `release()` | Release all resources |

## Supported Audio Formats

| Format | Recording | Playback | WebSocket |
|--------|-----------|----------|-----------|
| PCM 16-bit LE | ✓ | ✓ | ✓ |
| PCM Float32 | ✓ | ✓ | ✓ |

## Example

See [docs/EXAMPLE_UI.md](docs/EXAMPLE_UI.md) for a complete Jetpack Compose example.

## License

MIT License - see LICENSE file for details.

## Support

- Documentation: https://docs.speechmatics.com
- Issues: https://github.com/speechmatics/speechmatics-android-sdk/issues
- Email: support@speechmatics.com
