package com.speechmatics.sdk.realtime.models

import com.speechmatics.sdk.batch.models.TranscriptionConfig
import com.speechmatics.sdk.common.AudioFormatConfig
import com.speechmatics.sdk.common.FileAudioFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

//////////////////////////////////////////////
// Client -> Server Messages
//////////////////////////////////////////////

@Serializable
sealed interface RealtimeClientMessage {
    val message: String
}

@Serializable
@SerialName("StartRecognition")
data class StartRecognition(
    override val message: String = "StartRecognition",
    @SerialName("audio_format")
    val audioFormat: AudioFormatConfig = AudioFormatConfig(),
    @SerialName("transcription_config")
    val transcriptionConfig: RealtimeTranscriptionConfig
) : RealtimeClientMessage

@Serializable
@SerialName("AddAudio")
data class AddAudio(
    override val message: String = "AddAudio",
    @SerialName("seq_no")
    val seqNo: Int
) : RealtimeClientMessage

@Serializable
@SerialName("EndOfStream")
data class EndOfStream(
    override val message: String = "EndOfStream",
    @SerialName("last_seq_no")
    val lastSeqNo: Int
) : RealtimeClientMessage

@Serializable
@SerialName("SetRecognitionConfig")
data class SetRecognitionConfig(
    override val message: String = "SetRecognitionConfig",
    @SerialName("transcription_config")
    val transcriptionConfig: MidSessionTranscriptionConfig
) : RealtimeClientMessage

@Serializable
@SerialName("GetSpeakers")
data class GetSpeakers(
    override val message: String = "GetSpeakers",
    val final: Boolean? = null
) : RealtimeClientMessage

//////////////////////////////////////////////
// Server -> Client Messages
//////////////////////////////////////////////

@Serializable
sealed interface RealtimeServerMessage {
    val message: String
}

@Serializable
@SerialName("RecognitionStarted")
data class RecognitionStarted(
    override val message: String = "RecognitionStarted",
    val id: String? = null,
    @SerialName("language_pack_info")
    val languagePackInfo: LanguagePackInfo? = null
) : RealtimeServerMessage

@Serializable
data class LanguagePackInfo(
    val adapted: Boolean? = null,
    val itn: Boolean? = null,
    @SerialName("language_description")
    val languageDescription: String? = null,
    @SerialName("word_delimiter")
    val wordDelimiter: String? = null,
    @SerialName("writing_direction")
    val writingDirection: String? = null
)

@Serializable
@SerialName("AudioAdded")
data class AudioAdded(
    override val message: String = "AudioAdded",
    @SerialName("seq_no")
    val seqNo: Int
) : RealtimeServerMessage

@Serializable
@SerialName("AddPartialTranscript")
data class AddPartialTranscript(
    override val message: String = "AddPartialTranscript",
    val format: String? = null,
    val metadata: RecognitionMetadata? = null,
    val results: List<RecognitionResult> = emptyList()
) : RealtimeServerMessage

@Serializable
@SerialName("AddTranscript")
data class AddTranscript(
    override val message: String = "AddTranscript",
    val format: String? = null,
    val metadata: RecognitionMetadata? = null,
    val results: List<RecognitionResult> = emptyList()
) : RealtimeServerMessage

@Serializable
@SerialName("EndOfTranscript")
data class EndOfTranscript(
    override val message: String = "EndOfTranscript"
) : RealtimeServerMessage

@Serializable
@SerialName("SpeakersResult")
data class SpeakersResult(
    override val message: String = "SpeakersResult",
    val speakers: List<SpeakerInfo> = emptyList()
) : RealtimeServerMessage

@Serializable
data class SpeakerInfo(
    val id: String,
    val name: String? = null
)

@Serializable
@SerialName("Warning")
data class RealtimeWarning(
    override val message: String = "Warning",
    val type: String? = null,
    val reason: String? = null
) : RealtimeServerMessage

@Serializable
@SerialName("Error")
data class RealtimeError(
    override val message: String = "Error",
    val type: String? = null,
    val reason: String? = null
) : RealtimeServerMessage

@Serializable
@SerialName("Info")
data class RealtimeInfo(
    override val message: String = "Info",
    val type: String? = null,
    val reason: String? = null
) : RealtimeServerMessage

//////////////////////////////////////////////
// Supporting Types
//////////////////////////////////////////////

@Serializable
data class RecognitionMetadata(
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null,
    @SerialName("transcript")
    val transcript: String? = null
)

@Serializable
data class RecognitionResult(
    val type: RecognitionResultType,
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null,
    val channel: String? = null,
    @SerialName("is_eos")
    val isEos: Boolean? = null,
    @SerialName("attaches_to")
    val attachesTo: String? = null,
    val alternatives: List<RecognitionAlternative>? = null,
    val content: String? = null,
    val confidence: Double? = null,
    val speaker: String? = null,
    val language: String? = null
)

@Serializable
enum class RecognitionResultType {
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
data class RecognitionAlternative(
    val content: String,
    val confidence: Double? = null,
    val language: String? = null,
    val speaker: String? = null,
    val tags: List<String>? = null,
    val display: RecognitionDisplay? = null
)

@Serializable
data class RecognitionDisplay(
    val direction: String? = null
)

@Serializable
data class RealtimeTranscriptionConfig(
    val language: String,
    val domain: String? = null,
    @SerialName("output_locale")
    val outputLocale: String? = null,
    @SerialName("additional_vocab")
    val additionalVocab: List<AdditionalVocabItem>? = null,
    val diarization: String? = null,
    @SerialName("speaker_diarization_config")
    val speakerDiarizationConfig: RealtimeSpeakerDiarizationConfig? = null,
    @SerialName("punctuation_overrides")
    val punctuationOverrides: RealtimePunctuationOverrides? = null,
    @SerialName("max_delay")
    val maxDelay: Double? = null,
    @SerialName("max_delay_mode")
    val maxDelayMode: String? = null,
    @SerialName("enable_partials")
    val enablePartials: Boolean? = null,
    @SerialName("enable_entities")
    val enableEntities: Boolean? = null,
    @SerialName("operating_point")
    val operatingPoint: String? = null,
    @SerialName("translation_config")
    val translationConfig: RealtimeTranslationConfig? = null
)

@Serializable
data class AdditionalVocabItem(
    val content: String,
    @SerialName("sounds_like")
    val soundsLike: List<String>? = null
)

@Serializable
data class RealtimeSpeakerDiarizationConfig(
    @SerialName("max_speakers")
    val maxSpeakers: Int? = null
)

@Serializable
data class RealtimePunctuationOverrides(
    @SerialName("permitted_marks")
    val permittedMarks: List<String>? = null,
    val sensitivity: Double? = null
)

@Serializable
data class RealtimeTranslationConfig(
    @SerialName("target_languages")
    val targetLanguages: List<String>? = null,
    @SerialName("enable_partials")
    val enablePartials: Boolean? = null
)

@Serializable
data class MidSessionTranscriptionConfig(
    @SerialName("max_delay")
    val maxDelay: Double? = null,
    @SerialName("max_delay_mode")
    val maxDelayMode: String? = null
)
