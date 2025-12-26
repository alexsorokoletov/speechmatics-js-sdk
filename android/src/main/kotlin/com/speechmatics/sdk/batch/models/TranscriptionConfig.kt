package com.speechmatics.sdk.batch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for transcription
 */
@Serializable
data class TranscriptionConfig(
    /** Language model to process the audio input, normally specified as an ISO language code */
    val language: String,

    /** Request a specialized model based on 'language' but optimized for a particular field, e.g. "finance" or "medical" */
    val domain: String? = null,

    /** Language locale to be used when generating the transcription output */
    @SerialName("output_locale")
    val outputLocale: String? = null,

    /** Operating point for accuracy/speed tradeoff */
    @SerialName("operating_point")
    val operatingPoint: OperatingPoint? = null,

    /** List of custom words or phrases that should be recognized */
    @SerialName("additional_vocab")
    val additionalVocab: List<AdditionalVocab>? = null,

    /** Punctuation overrides */
    @SerialName("punctuation_overrides")
    val punctuationOverrides: PunctuationOverrides? = null,

    /** Specify whether speaker or channel labels are added to the transcript */
    val diarization: Diarization? = null,

    /** Transcript labels to use when using collating separate input channels */
    @SerialName("channel_diarization_labels")
    val channelDiarizationLabels: List<String>? = null,

    /** Include additional 'entity' objects in the transcription results */
    @SerialName("enable_entities")
    val enableEntities: Boolean? = null,

    /** Whether or not to enable flexible endpointing */
    @SerialName("max_delay_mode")
    val maxDelayMode: MaxDelayMode? = null,

    /** Transcript filtering configuration */
    @SerialName("transcript_filtering_config")
    val transcriptFilteringConfig: TranscriptFilteringConfig? = null,

    /** Speaker diarization configuration */
    @SerialName("speaker_diarization_config")
    val speakerDiarizationConfig: SpeakerDiarizationConfig? = null
)

@Serializable
enum class OperatingPoint {
    @SerialName("standard")
    STANDARD,
    @SerialName("enhanced")
    ENHANCED
}

@Serializable
data class AdditionalVocab(
    val content: String,
    val sounds_like: List<String>? = null
)

@Serializable
data class PunctuationOverrides(
    @SerialName("permitted_marks")
    val permittedMarks: List<String>? = null,
    val sensitivity: Double? = null
)

@Serializable
enum class Diarization {
    @SerialName("none")
    NONE,
    @SerialName("speaker")
    SPEAKER,
    @SerialName("channel")
    CHANNEL
}

@Serializable
enum class MaxDelayMode {
    @SerialName("fixed")
    FIXED,
    @SerialName("flexible")
    FLEXIBLE
}

@Serializable
data class TranscriptFilteringConfig(
    @SerialName("remove_disfluencies")
    val removeDisfluencies: Boolean? = null
)

@Serializable
data class SpeakerDiarizationConfig(
    @SerialName("max_speakers")
    val maxSpeakers: Int? = null
)
