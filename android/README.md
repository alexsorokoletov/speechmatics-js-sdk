# Speechmatics Android SDK (Unofficial)

> **Note:** This is an unofficial, community-maintained Android SDK for Speechmatics APIs. It is not affiliated with or endorsed by Speechmatics Ltd. For official SDKs, visit [speechmatics.com](https://speechmatics.com).

Kotlin Android SDK for Speechmatics speech recognition APIs. This SDK provides access to:

- **Batch Transcription** - Upload audio files for asynchronous transcription
- **Real-time Transcription** - Stream audio for live transcription via WebSocket
- **Flow API** - Conversational AI with bidirectional audio streaming
- **Audio Utilities** - Android-native audio recording and playback

## Requirements

- Android SDK 30+ (Android 11+)
- Kotlin 1.9+
- Gradle 8.x

## Installation

Add the GitHub Packages repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/alexsorokoletov/speechmatics-kotlin-sdk")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("dev.dreamteam:speechmatics-android:1.0.0")
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

> **Security Best Practice**: Never embed API keys in mobile apps. Use a backend server to generate short-lived JWT tokens.

#### Option 1: Backend Token Generation (Recommended)

Your backend server generates temporary tokens using the Speechmatics Management Platform API, keeping the long-lived API key secure on the server. The mobile app receives only short-lived JWT tokens.

See [Speechmatics Authentication Docs](https://docs.speechmatics.com/introduction/authentication) for backend implementation details.

```kotlin
// Fetch short-lived token from your backend, then use it
val token = yourBackendService.getTranscriptionToken()
realtimeClient.start(jwt = token, ...)
```

#### Option 2: Client-Side Token Generation (Development Only)

For local development/testing only - **do not use in production**:

```kotlin
val auth = SpeechmaticsAuth(apiKey = "YOUR_API_KEY")

// For real-time transcription
val rtToken = auth.generateToken(type = ApiType.REALTIME, ttl = 300)

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

// Start transcription with speaker diarization
client.start(
    jwt = token,
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

// Listen for transcripts
launch {
    client.messages.collect { message ->
        when (message) {
            is AddTranscript -> {
                // Final transcript with speaker labels
                val text = buildTranscriptText(message.results)
                println("Final: $text")
            }
            is AddPartialTranscript -> {
                // Interim results (may change)
                val text = buildTranscriptText(message.results)
                println("Partial: $text")
            }
            is EndOfTranscript -> {
                // All audio processed, safe to disconnect
                println("Transcription complete")
            }
            is RealtimeError -> {
                println("Error: ${message.type} - ${message.reason}")
            }
        }
    }
}

// Send audio data
client.sendAudio(audioData) // ShortArray, FloatArray, or ByteArray

// Stop transcription (waits for EndOfTranscript)
client.stopRecognition()
```

#### Processing Recognition Results

Each `AddTranscript` or `AddPartialTranscript` message contains a list of `RecognitionResult` objects:

```kotlin
fun buildTranscriptText(results: List<RecognitionResult>): String {
    val sb = StringBuilder()
    var currentSpeaker: String? = null

    for (result in results) {
        // Get speaker label (e.g., "S1", "S2")
        val speaker = result.alternatives?.firstOrNull()?.speaker

        // Add speaker label on change
        if (speaker != null && speaker != currentSpeaker) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("$speaker: ")
            currentSpeaker = speaker
        }

        // Get word content
        val content = result.alternatives?.firstOrNull()?.content ?: continue

        // Add spacing for words (not punctuation)
        if (result.type == RecognitionResultType.WORD && sb.isNotEmpty() &&
            !sb.endsWith(": ") && !sb.endsWith("\n")) {
            sb.append(" ")
        }

        sb.append(content)
    }

    return sb.toString()
}
```

**Example output:**
```
S1: Hello, how are you today?

S2: I'm doing great, thanks for asking.

S1: That's wonderful to hear.
```

#### Common Patterns

**Pattern 1: Accumulate Finals During Stream**

```kotlin
val transcriptStore = mutableListOf<AddTranscript>()

client.messages.collect { message ->
    when (message) {
        is AddTranscript -> {
            // Each message has speaker labels, timestamps, words
            transcriptStore.add(message)
        }
        is EndOfTranscript -> {
            // Meeting done - full diarized transcript ready
            saveFinalTranscript(transcriptStore)
        }
    }
}
```

**Pattern 2: Get Speaker IDs at End**

Enable `getSpeakers = true` in diarization config to receive speaker metadata only once at the end (more efficient for long recordings):

```kotlin
RealtimeTranscriptionConfig(
    language = "en",
    diarization = "speaker",
    speakerDiarizationConfig = RealtimeSpeakerDiarizationConfig(
        maxSpeakers = 10,
        getSpeakers = true  // Speaker IDs returned at end only
    )
)
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
        noiseSuppression = true
    )
)

recorder.startRecording()

launch {
    recorder.audioDataPcm16.collect { data ->
        realtimeClient.sendAudio(data)
    }
}

recorder.stopRecording()
recorder.release()
```

### Audio Playback

```kotlin
val player = AudioPlayer(AudioPlayerConfig(sampleRate = 16000))

player.play(audioData)
player.setVolume(80)
player.stop()
player.release()
```

## API Reference

See the source code for full API documentation.

## Example

See [docs/EXAMPLE_UI.md](docs/EXAMPLE_UI.md) for a Jetpack Compose example.

## License

MIT License

## Disclaimer

This project is not affiliated with, endorsed by, or connected to Speechmatics Ltd.
"Speechmatics" is a trademark of Speechmatics Ltd. This SDK is provided as-is for
community use.

## Support

- Issues: https://github.com/alexsorokoletov/speechmatics-kotlin-sdk/issues
- Speechmatics API Docs: https://docs.speechmatics.com
