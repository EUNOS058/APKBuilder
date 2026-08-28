package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String = "offline",
    @Json(name = "workerAvailable") val workerAvailable: Boolean = false,
    @Json(name = "sdkConfigured") val sdkConfigured: Boolean = false,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class UploadResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "buildId") val buildId: String = "",
    @Json(name = "fileName") val fileName: String = "",
    @Json(name = "projectRoot") val projectRoot: String = "",
    @Json(name = "gradleFound") val gradleFound: Boolean = false,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class BuildRequest(
    @Json(name = "buildId") val buildId: String,
    @Json(name = "task") val task: String = "assembleDebug"
)

@JsonClass(generateAdapter = true)
data class BuildResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class BuildStatusResponse(
    @Json(name = "status") val status: String = "QUEUED",
    @Json(name = "step") val step: String? = null,
    @Json(name = "progressPercent") val progressPercent: Int? = 0,
    @Json(name = "isFinished") val isFinished: Boolean = false,
    @Json(name = "isSuccess") val isSuccess: Boolean = false,
    @Json(name = "apkName") val apkName: String? = null,
    @Json(name = "apkSize") val apkSize: Long? = null,
    @Json(name = "downloadUrl") val downloadUrl: String? = null,
    @Json(name = "errorMessage") val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class BuildLogsResponse(
    @Json(name = "logs") val logs: List<String> = emptyList()
)
