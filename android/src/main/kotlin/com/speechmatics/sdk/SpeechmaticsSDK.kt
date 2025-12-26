package com.speechmatics.sdk

/**
 * Speechmatics Android SDK
 *
 * This SDK provides access to Speechmatics speech recognition APIs including:
 * - Batch transcription (REST API)
 * - Real-time transcription (WebSocket API)
 * - Flow conversational AI (WebSocket API)
 * - Audio recording and playback utilities
 *
 * ## Quick Start
 *
 * ### Real-time Transcription
 * ```kotlin
 * val client = RealtimeClient(RealtimeClientOptions(
 *     appId = "my-app"
 * ))
 *
 * // Start transcription
 * val started = client.start(
 *     jwt = "your-jwt-token",
 *     transcriptionConfig = RealtimeTranscriptionConfig(language = "en")
 * )
 *
 * // Listen for transcripts
 * client.messages.collect { message ->
 *     when (message) {
 *         is AddTranscript -> println(message.results)
 *         is AddPartialTranscript -> println("Partial: ${message.results}")
 *     }
 * }
 *
 * // Send audio
 * audioRecorder.audioDataPcm16.collect { audio ->
 *     client.sendAudio(audio)
 * }
 * ```
 *
 * ### Batch Transcription
 * ```kotlin
 * val client = BatchClient(
 *     apiKey = "your-api-key",
 *     appId = "my-app"
 * )
 *
 * val result = client.transcribe(
 *     input = JobInput.FileInput(audioFile),
 *     transcriptionConfig = TranscriptionConfig(language = "en")
 * )
 * ```
 *
 * ### Flow Conversational AI
 * ```kotlin
 * val client = FlowClient(
 *     serverUrl = "wss://flow.api.speechmatics.com",
 *     options = FlowClientOptions(appId = "my-app")
 * )
 *
 * client.startConversation(
 *     jwt = "your-jwt-token",
 *     conversationConfig = ConversationConfig(
 *         templateId = "your-template-id"
 *     )
 * )
 *
 * // Send audio
 * client.sendAudio(audioData)
 *
 * // Receive agent audio
 * client.agentAudio.collect { audio ->
 *     audioPlayer.play(audio)
 * }
 * ```
 *
 * ## Required Permissions
 *
 * Add to your AndroidManifest.xml:
 * ```xml
 * <uses-permission android:name="android.permission.INTERNET" />
 * <uses-permission android:name="android.permission.RECORD_AUDIO" />
 * ```
 */
object SpeechmaticsSDK {
    const val VERSION = "1.0.0"
    const val SDK_NAME = "speechmatics-android-sdk"
}
