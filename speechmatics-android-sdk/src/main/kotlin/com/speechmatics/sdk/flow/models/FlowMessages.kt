package com.speechmatics.sdk.flow.models

import com.speechmatics.sdk.common.AudioEncoding
import com.speechmatics.sdk.realtime.models.RecognitionMetadata
import com.speechmatics.sdk.realtime.models.RecognitionResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

//////////////////////////////////////////////
// Client -> Server Messages
//////////////////////////////////////////////

@Serializable
sealed interface FlowClientOutgoingMessage {
    val message: String
}

@Serializable
data class StartConversationMessage(
    override val message: String = "StartConversation",
    @SerialName("conversation_config")
    val conversationConfig: ConversationConfig,
    @SerialName("audio_format")
    val audioFormat: FlowAudioFormat = FlowAudioFormat()
) : FlowClientOutgoingMessage

@Serializable
data class ConversationConfig(
    @SerialName("template_id")
    val templateId: String,
    @SerialName("template_variables")
    val templateVariables: Map<String, String> = emptyMap()
)

@Serializable
data class FlowAudioFormat(
    val type: String = "raw",
    val encoding: String = "pcm_s16le",
    @SerialName("sample_rate")
    val sampleRate: Int = 16000
)

@Serializable
data class AudioReceivedMessage(
    override val message: String = "AudioReceived",
    @SerialName("seq_no")
    val seqNo: Int,
    val buffering: Double
) : FlowClientOutgoingMessage

@Serializable
data class AudioEndedMessage(
    override val message: String = "AudioEnded",
    @SerialName("last_seq_no")
    val lastSeqNo: Int
) : FlowClientOutgoingMessage

//////////////////////////////////////////////
// Server -> Client Messages
//////////////////////////////////////////////

@Serializable
sealed interface FlowClientIncomingMessage {
    val message: String
}

@Serializable
@SerialName("ConversationStarted")
data class ConversationStartedMessage(
    override val message: String = "ConversationStarted",
    val id: String,
    @SerialName("asr_session_id")
    val asrSessionId: String? = null,
    @SerialName("language_pack_info")
    val languagePackInfo: FlowLanguagePackInfo? = null
) : FlowClientIncomingMessage

@Serializable
data class FlowLanguagePackInfo(
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
data class FlowAudioAddedMessage(
    override val message: String = "AudioAdded",
    @SerialName("seq_no")
    val seqNo: Int
) : FlowClientIncomingMessage

@Serializable
@SerialName("ResponseStarted")
data class ResponseStartedMessage(
    override val message: String = "ResponseStarted",
    val content: String? = null,
    @SerialName("start_time")
    val startTime: Double? = null
) : FlowClientIncomingMessage

@Serializable
@SerialName("ResponseCompleted")
data class ResponseCompletedMessage(
    override val message: String = "ResponseCompleted",
    val content: String? = null,
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null
) : FlowClientIncomingMessage

@Serializable
@SerialName("ResponseInterrupted")
data class ResponseInterruptedMessage(
    override val message: String = "ResponseInterrupted",
    val content: String? = null,
    @SerialName("start_time")
    val startTime: Double? = null,
    @SerialName("end_time")
    val endTime: Double? = null
) : FlowClientIncomingMessage

@Serializable
@SerialName("AddTranscript")
data class FlowAddTranscriptMessage(
    override val message: String = "AddTranscript",
    val results: List<RecognitionResult> = emptyList(),
    val metadata: RecognitionMetadata? = null
) : FlowClientIncomingMessage

@Serializable
@SerialName("AddPartialTranscript")
data class FlowAddPartialTranscriptMessage(
    override val message: String = "AddPartialTranscript",
    val format: String? = null,
    val metadata: RecognitionMetadata? = null,
    val results: List<RecognitionResult> = emptyList()
) : FlowClientIncomingMessage

@Serializable
@SerialName("ConversationEnding")
data class ConversationEndingMessage(
    override val message: String = "ConversationEnding"
) : FlowClientIncomingMessage

@Serializable
@SerialName("ConversationEnded")
data class ConversationEndedMessage(
    override val message: String = "ConversationEnded"
) : FlowClientIncomingMessage

@Serializable
@SerialName("Info")
data class FlowInfoMessage(
    override val message: String = "Info",
    val type: String? = null,
    val reason: String? = null,
    val code: Int? = null
) : FlowClientIncomingMessage

@Serializable
@SerialName("Warning")
data class FlowWarningMessage(
    override val message: String = "Warning",
    val type: String? = null,
    val reason: String? = null
) : FlowClientIncomingMessage

@Serializable
@SerialName("Error")
data class FlowErrorMessage(
    override val message: String = "Error",
    val type: String? = null,
    val reason: String? = null
) : FlowClientIncomingMessage
