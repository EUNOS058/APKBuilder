package com.apkbuilder.app.data.github

import com.google.gson.annotations.SerializedName

data class GitHubUser(
    val login: String,
    val id: Long,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String?
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val private: Boolean,
    @SerializedName("default_branch") val defaultBranch: String,
    @SerializedName("html_url") val htmlUrl: String
)

data class CreateRepoRequest(
    val name: String,
    val description: String = "Android project built with APK Builder",
    val private: Boolean = true,
    @SerializedName("auto_init") val autoInit: Boolean = true
)

data class ContentRequest(
    val message: String,
    val content: String, // base64
    val branch: String = "main",
    val sha: String? = null
)

data class ContentResponse(
    val content: ContentInfo?,
    val commit: CommitInfo?
)

data class ContentInfo(
    val name: String,
    val path: String,
    val sha: String,
    @SerializedName("html_url") val htmlUrl: String?
)

data class CommitInfo(
    val sha: String,
    val message: String?
)

data class WorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)

data class WorkflowRun(
    val id: Long,
    val name: String?,
    @SerializedName("head_branch") val headBranch: String?,
    @SerializedName("head_sha") val headSha: String?,
    val status: String?,          // queued, in_progress, completed
    val conclusion: String?,      // success, failure, cancelled, etc.
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("run_number") val runNumber: Int?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("run_started_at") val runStartedAt: String?
)

data class WorkflowRunsResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("workflow_runs") val workflowRuns: List<WorkflowRun>
)

data class Artifact(
    val id: Long,
    val name: String,
    val size_in_bytes: Long,
    @SerializedName("archive_download_url") val archiveDownloadUrl: String?,
    @SerializedName("expired") val expired: Boolean,
    @SerializedName("created_at") val createdAt: String?
)

data class ArtifactsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val artifacts: List<Artifact>
)

data class GitHubError(
    val message: String?,
    val documentation_url: String?
)
