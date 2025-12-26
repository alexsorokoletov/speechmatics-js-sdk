package com.speechmatics.sdk.batch

import com.google.common.truth.Truth.assertThat
import com.speechmatics.sdk.batch.models.*
import com.speechmatics.sdk.common.SpeechmaticsBatchException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertThrows
import java.io.File

class BatchClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: BatchClient

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        client = BatchClient(
            apiKey = "test-api-key",
            apiUrl = mockServer.url("").toString().removeSuffix("/"),
            appId = "test-app"
        )
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `createTranscriptionJob with URL input succeeds`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": "job-123"}""")
        )

        val response = client.createTranscriptionJob(
            input = JobInput.UrlInput(url = "https://example.com/audio.wav"),
            transcriptionConfig = TranscriptionConfig(language = "en")
        )

        assertThat(response.id).isEqualTo("job-123")

        val request = mockServer.takeRequest()
        assertThat(request.path).contains("v2/jobs")
        assertThat(request.path).contains("sm-app=test-app")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key")
    }

    @Test
    fun `getJob returns job details`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "job": {
                            "id": "job-123",
                            "created_at": "2024-01-01T00:00:00Z",
                            "status": "done",
                            "duration": 60.5
                        }
                    }
                """.trimIndent())
        )

        val response = client.getJob("job-123")

        assertThat(response.job.id).isEqualTo("job-123")
        assertThat(response.job.status).isEqualTo(JobStatus.DONE)
        assertThat(response.job.duration).isEqualTo(60.5)
    }

    @Test
    fun `getJob throws exception on 404`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
        )

        val exception = assertThrows(SpeechmaticsBatchException::class.java) {
            runBlocking {
                client.getJob("non-existent-job")
            }
        }

        assertThat(exception.statusCode).isEqualTo(404)
    }

    @Test
    fun `getJobResult returns JSON transcript`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "format": "2.9",
                        "metadata": {
                            "created_at": "2024-01-01T00:00:00Z",
                            "type": "transcription"
                        },
                        "results": [
                            {
                                "type": "word",
                                "start_time": 0.0,
                                "end_time": 0.5,
                                "alternatives": [
                                    {"content": "Hello", "confidence": 0.99}
                                ]
                            }
                        ]
                    }
                """.trimIndent())
        )

        val result = client.getJobResult("job-123", TranscriptionFormat.JSON_V2)

        assertThat(result).isInstanceOf(TranscriptionResult.JsonResult::class.java)
        val jsonResult = result as TranscriptionResult.JsonResult
        assertThat(jsonResult.response.format).isEqualTo("2.9")
        assertThat(jsonResult.response.results).hasSize(1)
    }

    @Test
    fun `getJobResult returns text transcript`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Hello, this is a test transcript.")
        )

        val result = client.getJobResult("job-123", TranscriptionFormat.TEXT)

        assertThat(result).isInstanceOf(TranscriptionResult.TextResult::class.java)
        val textResult = result as TranscriptionResult.TextResult
        assertThat(textResult.text).isEqualTo("Hello, this is a test transcript.")
    }

    @Test
    fun `listJobs returns job list`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "jobs": [
                            {"id": "job-1", "created_at": "2024-01-01T00:00:00Z", "status": "done"},
                            {"id": "job-2", "created_at": "2024-01-02T00:00:00Z", "status": "running"}
                        ]
                    }
                """.trimIndent())
        )

        val response = client.listJobs()

        assertThat(response.jobs).hasSize(2)
        assertThat(response.jobs[0].id).isEqualTo("job-1")
        assertThat(response.jobs[1].status).isEqualTo(JobStatus.RUNNING)
    }

    @Test
    fun `listJobs with filters sends correct query params`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jobs": []}""")
        )

        client.listJobs(
            RetrieveJobsFilters(
                limit = 10,
                includeDeleted = true
            )
        )

        val request = mockServer.takeRequest()
        assertThat(request.path).contains("limit=10")
        assertThat(request.path).contains("include_deleted=true")
    }

    @Test
    fun `deleteJob succeeds`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "job": {
                            "id": "job-123",
                            "created_at": "2024-01-01T00:00:00Z",
                            "status": "deleted"
                        }
                    }
                """.trimIndent())
        )

        val response = client.deleteJob("job-123")

        assertThat(response.job.status).isEqualTo(JobStatus.DELETED)
    }

    @Test
    fun `deleteJob with force sends correct param`() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "job": {
                            "id": "job-123",
                            "created_at": "2024-01-01T00:00:00Z",
                            "status": "deleted"
                        }
                    }
                """.trimIndent())
        )

        client.deleteJob("job-123", force = true)

        val request = mockServer.takeRequest()
        assertThat(request.path).contains("force=true")
    }
}
