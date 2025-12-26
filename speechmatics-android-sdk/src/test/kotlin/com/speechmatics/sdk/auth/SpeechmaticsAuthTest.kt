package com.speechmatics.sdk.auth

import com.google.common.truth.Truth.assertThat
import com.speechmatics.sdk.common.AuthErrorType
import com.speechmatics.sdk.common.SpeechmaticsAuthException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertThrows

class SpeechmaticsAuthTest {

    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `generateToken returns JWT on successful response`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"key_value": "test-jwt-token"}""")
        )

        val auth = SpeechmaticsAuth(
            apiKey = "test-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        val token = auth.generateToken(type = ApiType.REALTIME)

        assertThat(token).isEqualTo("test-jwt-token")

        val request = mockServer.takeRequest()
        assertThat(request.path).contains("type=rt")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key")
    }

    @Test
    fun `generateToken with custom TTL sends correct value`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"key_value": "test-jwt-token"}""")
        )

        val auth = SpeechmaticsAuth(
            apiKey = "test-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        auth.generateToken(type = ApiType.FLOW, ttl = 120)

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("\"ttl\":120")
    }

    @Test
    fun `generateToken throws exception for TTL less than 60`() = runBlocking {
        val auth = SpeechmaticsAuth(
            apiKey = "test-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        val exception = assertThrows(SpeechmaticsAuthException::class.java) {
            runBlocking {
                auth.generateToken(type = ApiType.REALTIME, ttl = 30)
            }
        }

        assertThat(exception.type).isEqualTo(AuthErrorType.VALIDATION_FAILED)
        assertThat(exception.message).contains("TTL must be at least 60 seconds")
    }

    @Test
    fun `generateToken throws exception for batch without clientRef`() {
        val auth = SpeechmaticsAuth(
            apiKey = "test-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        val exception = assertThrows(SpeechmaticsAuthException::class.java) {
            runBlocking {
                auth.generateToken(type = ApiType.BATCH)
            }
        }

        assertThat(exception.type).isEqualTo(AuthErrorType.VALIDATION_FAILED)
        assertThat(exception.message).contains("clientRef")
    }

    @Test
    fun `generateToken with batch and clientRef succeeds`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"key_value": "batch-jwt-token"}""")
        )

        val auth = SpeechmaticsAuth(
            apiKey = "test-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        val token = auth.generateToken(
            type = ApiType.BATCH,
            clientRef = "my-client-ref"
        )

        assertThat(token).isEqualTo("batch-jwt-token")

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("\"client_ref\":\"my-client-ref\"")
    }

    @Test
    fun `generateToken throws unauthorized on 403`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(403)
        )

        val auth = SpeechmaticsAuth(
            apiKey = "invalid-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        val exception = assertThrows(SpeechmaticsAuthException::class.java) {
            runBlocking {
                auth.generateToken(type = ApiType.REALTIME)
            }
        }

        assertThat(exception.type).isEqualTo(AuthErrorType.UNAUTHORIZED)
    }

    @Test
    fun `generateToken throws validation error on 422`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"code": "validation_error", "message": "Invalid parameter"}""")
        )

        val auth = SpeechmaticsAuth(
            apiKey = "test-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        val exception = assertThrows(SpeechmaticsAuthException::class.java) {
            runBlocking {
                auth.generateToken(type = ApiType.REALTIME)
            }
        }

        assertThat(exception.type).isEqualTo(AuthErrorType.VALIDATION_FAILED)
        assertThat(exception.message).contains("Invalid parameter")
    }

    @Test
    fun `generateToken sends correct region`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"key_value": "test-jwt-token"}""")
        )

        val auth = SpeechmaticsAuth(
            apiKey = "test-api-key",
            managementPlatformUrl = mockServer.url("/v1").toString()
        )

        auth.generateToken(type = ApiType.REALTIME, region = Region.USA)

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("\"region\":\"usa\"")
    }
}
