package com.speechmatics.sdk.batch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Job configuration for batch transcription
 */
@Serializable
data class JobConfig(
    /** Type of job, typically "transcription" */
    val type: JobType = JobType.TRANSCRIPTION,

    /** Configuration for fetching data from a URL */
    @SerialName("fetch_data")
    val fetchData: DataFetchConfig? = null,

    /** Configuration for fetching text from a URL (for alignment) */
    @SerialName("fetch_text")
    val fetchText: DataFetchConfig? = null,

    /** Alignment configuration */
    @SerialName("alignment_config")
    val alignmentConfig: AlignmentConfig? = null,

    /** Transcription configuration */
    @SerialName("transcription_config")
    val transcriptionConfig: TranscriptionConfig? = null,

    /** Notification callbacks */
    @SerialName("notification_config")
    val notificationConfig: List<NotificationConfig>? = null,

    /** Custom tracking data */
    val tracking: TrackingData? = null,

    /** Output configuration */
    @SerialName("output_config")
    val outputConfig: OutputConfig? = null,

    /** Translation configuration */
    @SerialName("translation_config")
    val translationConfig: TranslationConfig? = null,

    /** Language identification configuration */
    @SerialName("language_identification_config")
    val languageIdentificationConfig: LanguageIdentificationConfig? = null,

    /** Summarization configuration */
    @SerialName("summarization_config")
    val summarizationConfig: SummarizationConfig? = null,

    /** Sentiment analysis configuration */
    @SerialName("sentiment_analysis_config")
    val sentimentAnalysisConfig: JsonObject? = null,

    /** Topic detection configuration */
    @SerialName("topic_detection_config")
    val topicDetectionConfig: TopicDetectionConfig? = null,

    /** Auto chapters configuration */
    @SerialName("auto_chapters_config")
    val autoChaptersConfig: JsonObject? = null,

    /** Audio events configuration */
    @SerialName("audio_events_config")
    val audioEventsConfig: AudioEventsConfig? = null
)

@Serializable
enum class JobType {
    @SerialName("transcription")
    TRANSCRIPTION,
    @SerialName("alignment")
    ALIGNMENT
}

@Serializable
data class DataFetchConfig(
    val url: String,
    @SerialName("auth_headers")
    val authHeaders: List<String>? = null
)

@Serializable
data class AlignmentConfig(
    val language: String
)

@Serializable
data class NotificationConfig(
    val url: String,
    val contents: List<String>? = null,
    @SerialName("auth_headers")
    val authHeaders: List<String>? = null
)

@Serializable
data class TrackingData(
    val title: String? = null,
    val reference: String? = null,
    val tags: List<String>? = null,
    val details: JsonObject? = null
)

@Serializable
data class OutputConfig(
    @SerialName("srt_max_line_length")
    val srtMaxLineLength: Int? = null,
    @SerialName("srt_include_speaker_labels")
    val srtIncludeSpeakerLabels: Boolean? = null
)

@Serializable
data class TranslationConfig(
    @SerialName("target_languages")
    val targetLanguages: List<String>,
    @SerialName("enable_partials")
    val enablePartials: Boolean? = null
)

@Serializable
data class LanguageIdentificationConfig(
    @SerialName("expected_languages")
    val expectedLanguages: List<String>? = null
)

@Serializable
data class SummarizationConfig(
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("summary_length")
    val summaryLength: String? = null,
    @SerialName("summary_type")
    val summaryType: String? = null
)

@Serializable
data class TopicDetectionConfig(
    val topics: List<String>? = null
)

@Serializable
data class AudioEventsConfig(
    val types: List<String>? = null
)
