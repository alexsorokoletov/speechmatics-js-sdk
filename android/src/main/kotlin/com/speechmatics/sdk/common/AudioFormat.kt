package com.speechmatics.sdk.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Audio format configuration for sending audio data
 */
@Serializable
data class AudioFormatConfig(
    val type: AudioType = AudioType.RAW,
    val encoding: AudioEncoding = AudioEncoding.PCM_S16LE,
    @SerialName("sample_rate")
    val sampleRate: Int = 16000
)

@Serializable
enum class AudioType {
    @SerialName("raw")
    RAW,
    @SerialName("file")
    FILE
}

@Serializable
enum class AudioEncoding {
    @SerialName("pcm_s16le")
    PCM_S16LE,
    @SerialName("pcm_f32le")
    PCM_F32LE
}

/**
 * File-based audio format (auto-detected)
 */
@Serializable
data class FileAudioFormat(
    val type: String = "file"
)
