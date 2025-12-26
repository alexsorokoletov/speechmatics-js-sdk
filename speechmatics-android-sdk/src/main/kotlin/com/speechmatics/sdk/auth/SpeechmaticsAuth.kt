package com.speechmatics.sdk.auth

import com.speechmatics.sdk.common.AuthErrorType
import com.speechmatics.sdk.common.SpeechmaticsAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * API type for temporary key generation
 */
enum class ApiType(val value: String) {
    BATCH("batch"),
    REALTIME("rt"),
    FLOW("flow"),
    TTS("tts")
}

/**
 * Region for API endpoints
 */
enum class Region(val value: String) {
    EU("eu"),
    USA("usa"),
    AU("au")
}

/**
 * Authentication utility for Speechmatics APIs.
 * Generates temporary JWT tokens for client-side API access.
 *
 * See documentation at https://docs.speechmatics.com/introduction/authentication#temporary-key-configuration
 */
class SpeechmaticsAuth(
    private val apiKey: String,
    private val managementPlatformUrl: String = "https://mp.speechmatics.com/v1",
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Generate a temporary JWT token for API access.
     *
     * @param type The type of API to generate a token for
     * @param ttl Time to live in seconds (minimum 60)
     * @param region The region for the API endpoint
     * @param clientRef Required for batch transcription - a reference for the client
     * @return The generated JWT token
     * @throws SpeechmaticsAuthException if token generation fails
     */
    suspend fun generateToken(
        type: ApiType,
        ttl: Int = 60,
        region: Region = Region.EU,
        clientRef: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (type == ApiType.BATCH && clientRef == null) {
            throw SpeechmaticsAuthException(
                AuthErrorType.VALIDATION_FAILED,
                "Must set the `clientRef` parameter when using temporary keys for batch transcription. See documentation at https://docs.speechmatics.com/introduction/authentication#batch-transcription"
            )
        }

        if (ttl < 60) {
            throw SpeechmaticsAuthException(
                AuthErrorType.VALIDATION_FAILED,
                "TTL must be at least 60 seconds"
            )
        }

        val requestBody = TokenRequest(
            ttl = ttl,
            region = region.value,
            clientRef = clientRef
        )

        val request = Request.Builder()
            .url("$managementPlatformUrl/api_keys?type=${type.value}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()

        if (response.isSuccessful) {
            val body = response.body?.string()
                ?: throw SpeechmaticsAuthException(
                    AuthErrorType.UNKNOWN_ERROR,
                    "Empty response body"
                )
            val tokenResponse = json.decodeFromString<TokenResponse>(body)
            return@withContext tokenResponse.keyValue
        }

        when (response.code) {
            403 -> throw SpeechmaticsAuthException(
                AuthErrorType.UNAUTHORIZED,
                "Unauthorized"
            )
            422 -> {
                val body = response.body?.string()
                val errorResponse = try {
                    body?.let { json.decodeFromString<ErrorResponse>(it) }
                } catch (e: Exception) {
                    null
                }
                throw SpeechmaticsAuthException(
                    AuthErrorType.VALIDATION_FAILED,
                    errorResponse?.message ?: "Validation failed"
                )
            }
            else -> throw SpeechmaticsAuthException(
                AuthErrorType.UNKNOWN_ERROR,
                "Got response with status ${response.code}"
            )
        }
    }
}

@Serializable
internal data class TokenRequest(
    val ttl: Int,
    val region: String,
    @SerialName("client_ref")
    val clientRef: String? = null
)

@Serializable
internal data class TokenResponse(
    @SerialName("key_value")
    val keyValue: String
)

@Serializable
internal data class ErrorResponse(
    val code: String? = null,
    val message: String
)
