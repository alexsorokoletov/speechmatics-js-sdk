package com.speechmatics.sdk.audio

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AudioUtilsTest {

    @Test
    fun `ShortArray toFloatArray converts correctly`() {
        val shorts = shortArrayOf(0, 16384, -16384, Short.MAX_VALUE, Short.MIN_VALUE)
        val floats = shorts.toFloatArray()

        assertThat(floats[0]).isWithin(0.001f).of(0f)
        assertThat(floats[1]).isWithin(0.001f).of(0.5f)
        assertThat(floats[2]).isWithin(0.001f).of(-0.5f)
        assertThat(floats[3]).isWithin(0.001f).of(1f)
        assertThat(floats[4]).isWithin(0.001f).of(-1f)
    }

    @Test
    fun `FloatArray toShortArray converts correctly`() {
        val floats = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f)
        val shorts = floats.toShortArray()

        assertThat(shorts[0]).isEqualTo(0)
        assertThat(shorts[1]).isEqualTo(16384.toShort())
        assertThat(shorts[2]).isEqualTo((-16384).toShort())
        assertThat(shorts[3]).isEqualTo(Short.MAX_VALUE)
        assertThat(shorts[4]).isEqualTo((-Short.MAX_VALUE).toShort())
    }

    @Test
    fun `FloatArray toShortArray clips values above 1`() {
        val floats = floatArrayOf(1.5f, 2f)
        val shorts = floats.toShortArray()

        assertThat(shorts[0]).isEqualTo(Short.MAX_VALUE)
        assertThat(shorts[1]).isEqualTo(Short.MAX_VALUE)
    }

    @Test
    fun `FloatArray toShortArray clips values below -1`() {
        val floats = floatArrayOf(-1.5f, -2f)
        val shorts = floats.toShortArray()

        assertThat(shorts[0]).isEqualTo(Short.MIN_VALUE)
        assertThat(shorts[1]).isEqualTo(Short.MIN_VALUE)
    }

    @Test
    fun `AudioRecorderConfig has correct defaults`() {
        val config = AudioRecorderConfig()

        assertThat(config.sampleRate).isEqualTo(16000)
        assertThat(config.encoding).isEqualTo(AudioEncodingFormat.PCM_16BIT)
        assertThat(config.echoCancellation).isTrue()
        assertThat(config.noiseSuppression).isTrue()
        assertThat(config.autoGainControl).isTrue()
        assertThat(config.bufferSizeMultiplier).isEqualTo(2)
    }

    @Test
    fun `AudioPlayerConfig has correct defaults`() {
        val config = AudioPlayerConfig()

        assertThat(config.sampleRate).isEqualTo(16000)
        assertThat(config.initialVolume).isEqualTo(100)
    }

    @Test
    fun `AudioEncodingFormat enum values are correct`() {
        assertThat(AudioEncodingFormat.PCM_16BIT).isNotNull()
        assertThat(AudioEncodingFormat.PCM_FLOAT).isNotNull()
    }
}
