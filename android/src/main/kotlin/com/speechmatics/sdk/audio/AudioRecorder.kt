package com.speechmatics.sdk.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio encoding format for recording
 */
enum class AudioEncodingFormat {
    /** PCM 16-bit signed little-endian */
    PCM_16BIT,
    /** PCM 32-bit float */
    PCM_FLOAT
}

/**
 * Audio recorder configuration
 */
data class AudioRecorderConfig(
    /** Sample rate in Hz (default: 16000) */
    val sampleRate: Int = 16000,

    /** Audio encoding format */
    val encoding: AudioEncodingFormat = AudioEncodingFormat.PCM_16BIT,

    /** Enable echo cancellation if available */
    val echoCancellation: Boolean = true,

    /** Enable noise suppression if available */
    val noiseSuppression: Boolean = true,

    /** Enable automatic gain control if available */
    val autoGainControl: Boolean = true,

    /** Buffer size multiplier (default: 2x minimum buffer) */
    val bufferSizeMultiplier: Int = 2
)

/**
 * Audio recorder for Android that captures PCM audio data.
 * Supports both PCM 16-bit and Float32 formats.
 *
 * Requires RECORD_AUDIO permission.
 */
class AudioRecorder(
    private val config: AudioRecorderConfig = AudioRecorderConfig()
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioData = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val audioData: SharedFlow<ByteArray> = _audioData.asSharedFlow()

    private val _audioDataPcm16 = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val audioDataPcm16: SharedFlow<ShortArray> = _audioDataPcm16.asSharedFlow()

    private val _audioDataFloat = MutableSharedFlow<FloatArray>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val audioDataFloat: SharedFlow<FloatArray> = _audioDataFloat.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Check if audio recording permission is granted
     */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording audio.
     *
     * @throws SecurityException if RECORD_AUDIO permission is not granted
     * @throws IllegalStateException if AudioRecord cannot be initialized
     */
    @SuppressLint("MissingPermission")
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        if (_isRecording.value) {
            return@withContext
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = when (config.encoding) {
            AudioEncodingFormat.PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
            AudioEncodingFormat.PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            channelConfig,
            audioEncoding
        )

        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            throw IllegalStateException("Invalid audio configuration")
        }

        val bufferSize = minBufferSize * config.bufferSizeMultiplier

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            config.sampleRate,
            channelConfig,
            audioEncoding,
            bufferSize
        )

        val record = audioRecord ?: throw IllegalStateException("Failed to create AudioRecord")

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        // Apply audio effects
        setupAudioEffects(record.audioSessionId)

        record.startRecording()
        _isRecording.value = true

        // Start reading audio data
        recordingJob = scope.launch {
            readAudioData(record, bufferSize, audioEncoding)
        }
    }

    /**
     * Stop recording audio.
     */
    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.apply {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null

        releaseAudioEffects()

        _isRecording.value = false
    }

    /**
     * Release all resources.
     */
    fun release() {
        stopRecording()
        scope.cancel()
    }

    private suspend fun readAudioData(
        record: AudioRecord,
        bufferSize: Int,
        audioEncoding: Int
    ) {
        when (audioEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> readPcm16Data(record, bufferSize)
            AudioFormat.ENCODING_PCM_FLOAT -> readFloatData(record, bufferSize)
        }
    }

    private suspend fun readPcm16Data(record: AudioRecord, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)

        while (_isRecording.value && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val readCount = record.read(buffer, 0, buffer.size)

            if (readCount > 0) {
                val data = if (readCount == buffer.size) {
                    buffer.copyOf()
                } else {
                    buffer.copyOfRange(0, readCount)
                }

                // Emit as ShortArray
                _audioDataPcm16.emit(data)

                // Also emit as ByteArray for raw access
                val byteBuffer = ByteBuffer.allocate(data.size * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                data.forEach { byteBuffer.putShort(it) }
                _audioData.emit(byteBuffer.array())
            } else if (readCount < 0) {
                // Error occurred
                break
            }

            yield() // Allow coroutine cancellation
        }
    }

    private suspend fun readFloatData(record: AudioRecord, bufferSize: Int) {
        val buffer = FloatArray(bufferSize / 4)

        while (_isRecording.value && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val readCount = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)

            if (readCount > 0) {
                val data = if (readCount == buffer.size) {
                    buffer.copyOf()
                } else {
                    buffer.copyOfRange(0, readCount)
                }

                // Emit as FloatArray
                _audioDataFloat.emit(data)

                // Also emit as ByteArray for raw access
                val byteBuffer = ByteBuffer.allocate(data.size * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                data.forEach { byteBuffer.putFloat(it) }
                _audioData.emit(byteBuffer.array())
            } else if (readCount < 0) {
                // Error occurred
                break
            }

            yield() // Allow coroutine cancellation
        }
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        // Echo cancellation
        if (config.echoCancellation && AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            } catch (e: Exception) {
                // Effect not available or failed to create
            }
        }

        // Noise suppression
        if (config.noiseSuppression && NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            } catch (e: Exception) {
                // Effect not available or failed to create
            }
        }

        // Automatic gain control
        if (config.autoGainControl && AutomaticGainControl.isAvailable()) {
            try {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
            } catch (e: Exception) {
                // Effect not available or failed to create
            }
        }
    }

    private fun releaseAudioEffects() {
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        gainControl?.release()
        gainControl = null
    }

    companion object {
        /**
         * Check if echo cancellation is available on this device
         */
        fun isEchoCancellationAvailable(): Boolean = AcousticEchoCanceler.isAvailable()

        /**
         * Check if noise suppression is available on this device
         */
        fun isNoiseSuppressionAvailable(): Boolean = NoiseSuppressor.isAvailable()

        /**
         * Check if automatic gain control is available on this device
         */
        fun isAutoGainControlAvailable(): Boolean = AutomaticGainControl.isAvailable()
    }
}
