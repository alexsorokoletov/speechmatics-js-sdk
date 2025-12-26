package com.speechmatics.sdk.batch

import com.speechmatics.sdk.batch.models.*
import com.speechmatics.sdk.common.SpeechmaticsBatchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Transcription output format
 */
enum class TranscriptionFormat(val value: String, val queryParam: String) {
    JSON_V2("application/json", "json-v2"),
    TEXT("text/plain", "txt"),
    SRT("text/plain", "srt")
}

/**
 * Job input types
 */
sealed class JobInput {
    data class FileInput(val file: File, val fileName: String? = null) : JobInput()
    data class ByteArrayInput(val data: ByteArray, val fileName: String) : JobInput()
    data class UrlInput(val url: String, val authHeaders: List<String>? = null) : JobInput()
}

/**
 * Filters for listing jobs
 */
data class RetrieveJobsFilters(
    val createdBefore: String? = null,
    val limit: Int? = null,
    val includeDeleted: Boolean? = null
)

/**
 * Batch transcription client for Speechmatics REST API.
 *
 * @param apiKey The API key for authentication
 * @param apiUrl Base URL for the API (defaults to EU endpoint)
 * @param appId Application identifier for tracking
 */
@OptIn(ExperimentalSerializationApi::class)
class BatchClient(
    private val apiKey: String,
    private val apiUrl: String = "https://asr.api.speechmatics.com/v2",
    private val appId: String,
    httpClient: OkHttpClient? = null
) {
    private val httpClient: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    companion object {
        private const val SM_APP_PARAM_NAME = "sm-app"
        private const val DEFAULT_POLL_INTERVAL_MS = 3000L
        private const val DEFAULT_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes
    }

    /**
     * Main method for transcribing audio files.
     * Creates a job, polls until complete, and returns the transcript.
     *
     * @param input The audio input (file, byte array, or URL)
     * @param transcriptionConfig Transcription configuration
     * @param format Output format (defaults to JSON-V2)
     * @param timeoutMs Maximum time to wait for transcription (defaults to 15 minutes)
     * @return The transcription result
     */
    suspend fun transcribe(
        input: JobInput,
        transcriptionConfig: TranscriptionConfig,
        format: TranscriptionFormat = TranscriptionFormat.JSON_V2,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val createResponse = createTranscriptionJob(input, transcriptionConfig)

        pollUntilComplete(createResponse.id, timeoutMs)

        getJobResult(createResponse.id, format)
    }

    /**
     * Create a new transcription job
     */
    suspend fun createTranscriptionJob(
        input: JobInput,
        transcriptionConfig: TranscriptionConfig,
        jobConfig: JobConfig? = null
    ): CreateJobResponse = withContext(Dispatchers.IO) {
        val config = (jobConfig ?: JobConfig()).copy(
            type = JobType.TRANSCRIPTION,
            transcriptionConfig = transcriptionConfig,
            fetchData = if (input is JobInput.UrlInput) {
                DataFetchConfig(url = input.url, authHeaders = input.authHeaders)
            } else null
        )

        val configJson = json.encodeToString(config)

        val requestBody = when (input) {
            is JobInput.FileInput -> {
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "data_file",
                        input.fileName ?: input.file.name,
                        input.file.asRequestBody("application/octet-stream".toMediaType())
                    )
                    .addFormDataPart("config", configJson)
                    .build()
            }
            is JobInput.ByteArrayInput -> {
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "data_file",
                        input.fileName,
                        input.data.toRequestBody("application/octet-stream".toMediaType())
                    )
                    .addFormDataPart("config", configJson)
                    .build()
            }
            is JobInput.UrlInput -> {
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("config", configJson)
                    .build()
            }
        }

        val request = Request.Builder()
            .url("$apiUrl/v2/jobs?$SM_APP_PARAM_NAME=$appId")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw SpeechmaticsBatchException(
                "Failed to create job: ${response.code}",
                response.code
            )
        }

        val body = response.body?.string()
            ?: throw SpeechmaticsBatchException("Empty response body")

        json.decodeFromString<CreateJobResponse>(body)
    }

    /**
     * Get job details
     */
    suspend fun getJob(jobId: String): RetrieveJobResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$apiUrl/v2/jobs/$jobId?$SM_APP_PARAM_NAME=$appId")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw SpeechmaticsBatchException(
                "Failed to get job: ${response.code}",
                response.code
            )
        }

        val body = response.body?.string()
            ?: throw SpeechmaticsBatchException("Empty response body")

        json.decodeFromString<RetrieveJobResponse>(body)
    }

    /**
     * Get job result/transcript
     */
    suspend fun getJobResult(
        jobId: String,
        format: TranscriptionFormat = TranscriptionFormat.JSON_V2
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$apiUrl/v2/jobs/$jobId/transcript?format=${format.queryParam}&$SM_APP_PARAM_NAME=$appId")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", format.value)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw SpeechmaticsBatchException(
                "Failed to get transcript: ${response.code}",
                response.code
            )
        }

        val body = response.body?.string()
            ?: throw SpeechmaticsBatchException("Empty response body")

        when (format) {
            TranscriptionFormat.JSON_V2 -> TranscriptionResult.JsonResult(
                json.decodeFromString<RetrieveTranscriptResponse>(body)
            )
            TranscriptionFormat.TEXT, TranscriptionFormat.SRT -> TranscriptionResult.TextResult(body)
        }
    }

    /**
     * List jobs
     */
    suspend fun listJobs(
        filters: RetrieveJobsFilters? = null
    ): RetrieveJobsResponse = withContext(Dispatchers.IO) {
        val queryParams = buildString {
            append("$SM_APP_PARAM_NAME=$appId")
            filters?.let {
                it.createdBefore?.let { append("&created_before=$it") }
                it.limit?.let { append("&limit=$it") }
                it.includeDeleted?.let { append("&include_deleted=$it") }
            }
        }

        val request = Request.Builder()
            .url("$apiUrl/v2/jobs?$queryParams")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw SpeechmaticsBatchException(
                "Failed to list jobs: ${response.code}",
                response.code
            )
        }

        val body = response.body?.string()
            ?: throw SpeechmaticsBatchException("Empty response body")

        json.decodeFromString<RetrieveJobsResponse>(body)
    }

    /**
     * Delete a job
     */
    suspend fun deleteJob(
        jobId: String,
        force: Boolean = false
    ): DeleteJobResponse = withContext(Dispatchers.IO) {
        val queryParams = buildString {
            append("$SM_APP_PARAM_NAME=$appId")
            if (force) append("&force=true")
        }

        val request = Request.Builder()
            .url("$apiUrl/v2/jobs/$jobId?$queryParams")
            .addHeader("Authorization", "Bearer $apiKey")
            .delete()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw SpeechmaticsBatchException(
                "Failed to delete job: ${response.code}",
                response.code
            )
        }

        val body = response.body?.string()
            ?: throw SpeechmaticsBatchException("Empty response body")

        json.decodeFromString<DeleteJobResponse>(body)
    }

    private suspend fun pollUntilComplete(jobId: String, timeoutMs: Long) {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val jobResponse = getJob(jobId)

            when (jobResponse.job.status) {
                JobStatus.DONE -> return
                JobStatus.REJECTED -> {
                    throw SpeechmaticsBatchException(
                        "Job rejected: ${jobResponse.job.errors?.joinToString { it.message }}"
                    )
                }
                JobStatus.DELETED -> {
                    throw SpeechmaticsBatchException("Job was deleted")
                }
                JobStatus.RUNNING -> {
                    delay(DEFAULT_POLL_INTERVAL_MS)
                }
            }
        }

        throw SpeechmaticsBatchException("Transcription timed out after ${timeoutMs}ms")
    }
}

/**
 * Result from transcription
 */
sealed class TranscriptionResult {
    data class JsonResult(val response: RetrieveTranscriptResponse) : TranscriptionResult()
    data class TextResult(val text: String) : TranscriptionResult()
}
