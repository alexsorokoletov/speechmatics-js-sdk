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
        url = uri("https://maven.pkg.github.com/dreamteam-oss/speechmatics-android")
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

- Issues: https://github.com/dreamteam-oss/speechmatics-android/issues
- Speechmatics API Docs: https://docs.speechmatics.com
