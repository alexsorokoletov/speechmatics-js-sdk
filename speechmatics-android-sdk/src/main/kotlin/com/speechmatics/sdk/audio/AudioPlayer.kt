package com.speechmatics.sdk.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Audio player configuration
 */
data class AudioPlayerConfig(
    /** Sample rate in Hz (default: 16000) */
    val sampleRate: Int = 16000,

    /** Audio stream type */
    val streamType: Int = AudioManager.STREAM_MUSIC,

    /** Initial volume percentage (0-100) */
    val initialVolume: Int = 100
)

/**
 * PCM audio player for Android.
 * Plays PCM audio data received from the Flow API or other sources.
 */
class AudioPlayer(
    private val config: AudioPlayerConfig = AudioPlayerConfig()
) {
    private var audioTrack: AudioTrack? = null
    private var playbackTime: Long = 0

    private val _volume = MutableStateFlow(config.initialVolume)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /**
     * Initialize the audio player
     */
    fun initialize() {
        if (audioTrack != null) return

        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(
            config.sampleRate,
            channelConfig,
            audioEncoding
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(config.sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(audioEncoding)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        setVolumeInternal(_volume.value)
    }

    /**
     * Play PCM 16-bit audio data.
     *
     * @param data Audio data as ShortArray
     */
    fun play(data: ShortArray) {
        val track = audioTrack ?: run {
            initialize()
            audioTrack
        } ?: return

        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
            _isPlaying.value = true
        }

        // Convert ShortArray to ByteArray
        val byteBuffer = ByteBuffer.allocate(data.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        data.forEach { byteBuffer.putShort(it) }

        track.write(byteBuffer.array(), 0, byteBuffer.array().size)
    }

    /**
     * Play PCM 16-bit audio data.
     *
     * @param data Audio data as ByteArray (little-endian PCM 16-bit)
     */
    fun play(data: ByteArray) {
        val track = audioTrack ?: run {
            initialize()
            audioTrack
        } ?: return

        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
            _isPlaying.value = true
        }

        track.write(data, 0, data.size)
    }

    /**
     * Play Float32 audio data.
     * Note: This converts Float32 to PCM16 before playback.
     *
     * @param data Audio data as FloatArray (values in range -1.0 to 1.0)
     */
    fun play(data: FloatArray) {
        // Convert Float32 to PCM16
        val shortData = ShortArray(data.size) { i ->
            (data[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        play(shortData)
    }

    /**
     * Set the volume level.
     *
     * @param volumePercent Volume percentage (0-100)
     */
    fun setVolume(volumePercent: Int) {
        val clampedVolume = volumePercent.coerceIn(0, 100)
        _volume.value = clampedVolume
        setVolumeInternal(clampedVolume)
    }

    /**
     * Pause playback.
     */
    fun pause() {
        audioTrack?.pause()
        _isPlaying.value = false
    }

    /**
     * Stop playback and clear buffers.
     */
    fun stop() {
        audioTrack?.apply {
            stop()
            flush()
        }
        _isPlaying.value = false
        playbackTime = 0
    }

    /**
     * Release all resources.
     */
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun setVolumeInternal(volumePercent: Int) {
        val volume = volumePercent / 100f
        audioTrack?.setVolume(volume)
    }
}

/**
 * Extension to convert Int16Array to Float32Array
 */
fun ShortArray.toFloatArray(): FloatArray {
    return FloatArray(size) { i ->
        this[i].toFloat() / Short.MAX_VALUE
    }
}

/**
 * Extension to convert Float32Array to Int16Array
 */
fun FloatArray.toShortArray(): ShortArray {
    return ShortArray(size) { i ->
        (this[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
