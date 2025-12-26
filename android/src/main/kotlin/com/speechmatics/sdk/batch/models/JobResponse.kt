package com.speechmatics.sdk.batch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from creating a job
 */
@Serializable
data class CreateJobResponse(
    val id: String
)

/**
 * Response from retrieving a job
 */
@Serializable
data class RetrieveJobResponse(
    val job: JobDetails
)

/**
 * Job details
 */
@Serializable
data class JobDetails(
    val id: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("data_name")
    val dataName: String? = null,
    val status: JobStatus,
    val duration: Double? = null,
    val config: JobConfig? = null,
    val errors: List<JobError>? = null
)

@Serializable
enum class JobStatus {
    @SerialName("running")
    RUNNING,
    @SerialName("done")
    DONE,
    @SerialName("rejected")
    REJECTED,
    @SerialName("deleted")
    DELETED
}

@Serializable
data class JobError(
    val message: String,
    val timestamp: String? = null
)

/**
 * Response from listing jobs
 */
@Serializable
data class RetrieveJobsResponse(
    val jobs: List<JobDetails>
)

/**
 * Response from deleting a job
 */
@Serializable
data class DeleteJobResponse(
    val job: JobDetails
)
