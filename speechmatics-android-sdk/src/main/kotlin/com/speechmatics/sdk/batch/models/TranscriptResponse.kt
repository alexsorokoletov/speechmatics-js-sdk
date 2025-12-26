package com.speechmatics.sdk.batch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Response from retrieving a transcript
 */
@Serializable
data class RetrieveTranscriptResponse(
    val format: String? = null,
    val metadata: TranscriptMetadata? = null,
    val results: List<TranscriptResult>? = null,
    @SerialName("job")
    val jobInfo: TranscriptJobInfo? = null
)

@Serializable
data class TranscriptMetadata(
    @SerialName("created_at")
    val createdAt: String? = null,
    val type: String? = null,
    @SerialName("transcription_config")
    val transcriptionConfig: TranscriptionConfig? = null
)

@Serializable
data class TranscriptResult(
    val type: ResultType,
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null,
    val channel: String? = null,
    val alternatives: List<ResultAlternative>? = null,
    @SerialName("is_eos")
    val isEos: Boolean? = null,
    val attaches_to: String? = null,
    val content: String? = null,
    val score: Double? = null,
    val speaker: String? = null,
    val language: String? = null,
    val confidence: Double? = null,
    @SerialName("spoken_form")
    val spokenForm: List<SpokenFormWord>? = null,
    @SerialName("written_form")
    val writtenForm: List<WrittenFormWord>? = null
)

@Serializable
enum class ResultType {
    @SerialName("word")
    WORD,
    @SerialName("punctuation")
    PUNCTUATION,
    @SerialName("speaker_change")
    SPEAKER_CHANGE,
    @SerialName("entity")
    ENTITY
}

@Serializable
data class ResultAlternative(
    val content: String,
    val confidence: Double? = null,
    val language: String? = null,
    val speaker: String? = null,
    val tags: List<String>? = null,
    val display: DisplayConfig? = null
)

@Serializable
data class DisplayConfig(
    val direction: String? = null
)

@Serializable
data class SpokenFormWord(
    val content: String,
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null
)

@Serializable
data class WrittenFormWord(
    val content: String,
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null
)

@Serializable
data class TranscriptJobInfo(
    val id: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("data_name")
    val dataName: String? = null,
    val duration: Double? = null
)
