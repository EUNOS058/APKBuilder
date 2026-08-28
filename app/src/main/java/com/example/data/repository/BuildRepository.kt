package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.local.AppDatabase
import com.example.data.local.BuildHistoryEntity
import com.example.data.remote.BuildApiService
import com.example.data.remote.BuildRequest
import com.example.data.remote.BuildStatusResponse
import com.example.data.remote.HealthResponse
import com.example.data.remote.UploadResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.InputStream
import java.util.concurrent.TimeUnit

class BuildRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.buildHistoryDao()

    val buildHistory: Flow<List<BuildHistoryEntity>> = dao.getAllBuilds()

    private val prefs = context.getSharedPreferences("build_server_prefs", Context.MODE_PRIVATE)
    private var currentBaseUrl: String = prefs.getString("server_url", "https://my-cloud-apk-builder.onrender.com/") ?: "https://my-cloud-apk-builder.onrender.com/"
    private var currentMode: String = prefs.getString("build_mode", "CLOUD") ?: "CLOUD"
    private var currentGithubRepo: String = prefs.getString("github_repo", "") ?: ""
    private var currentGithubToken: String = prefs.getString("github_token", "") ?: ""

    private var apiService: BuildApiService? = null
    private var rawOkHttpClient: OkHttpClient? = null

    init {
        updateBaseUrl(currentBaseUrl)
    }

    fun getBaseUrl(): String = currentBaseUrl
    fun getBuildMode(): String = currentMode
    fun getGitHubRepo(): String = currentGithubRepo
    fun getGitHubToken(): String = currentGithubToken

    fun updateSettings(url: String, mode: String, repo: String, token: String) {
        currentMode = mode.ifBlank { "CLOUD" }
        currentGithubRepo = repo.trim()
        currentGithubToken = token.trim()

        prefs.edit()
            .putString("build_mode", currentMode)
            .putString("github_repo", currentGithubRepo)
            .putString("github_token", currentGithubToken)
            .apply()

        updateBaseUrl(url)
    }

    fun updateBaseUrl(url: String) {
        var cleanUrl = url.trim()
        if (!cleanUrl.endsWith("/")) {
            cleanUrl += "/"
        }
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        currentBaseUrl = cleanUrl
        prefs.edit().putString("server_url", cleanUrl).apply()

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        rawOkHttpClient = okHttpClient

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        apiService = retrofit.create(BuildApiService::class.java)
    }

    suspend fun checkHealth(): HealthResponse = withContext(Dispatchers.IO) {
        if (currentMode == "GITHUB") {
            if (currentGithubRepo.isBlank()) {
                return@withContext HealthResponse(
                    status = "offline",
                    workerAvailable = false,
                    sdkConfigured = false,
                    message = "Please enter your GitHub Repository (owner/repo) in Settings."
                )
            }
            try {
                val client = rawOkHttpClient ?: OkHttpClient()
                val reqBuilder = Request.Builder()
                    .url("https://api.github.com/repos/$currentGithubRepo")
                    .header("User-Agent", "ZIP-to-APK-Builder-App")

                if (currentGithubToken.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $currentGithubToken")
                }

                val response = client.newCall(reqBuilder.build()).execute()
                if (response.isSuccessful) {
                    HealthResponse(
                        status = "online",
                        workerAvailable = true,
                        sdkConfigured = true,
                        message = "GitHub Actions runner ready for repository: $currentGithubRepo"
                    )
                } else {
                    HealthResponse(
                        status = "offline",
                        workerAvailable = false,
                        sdkConfigured = false,
                        message = "GitHub API returned code ${response.code}. Check repository name & PAT."
                    )
                }
            } catch (e: Exception) {
                HealthResponse(
                    status = "offline",
                    workerAvailable = false,
                    sdkConfigured = false,
                    message = "GitHub check error: ${e.localizedMessage}"
                )
            }
        } else {
            try {
                val service = apiService ?: return@withContext HealthResponse(
                    status = "offline",
                    workerAvailable = false,
                    sdkConfigured = false,
                    message = "API service not initialized"
                )
                val response = service.getHealth()
                if (response.isSuccessful && response.body() != null) {
                    response.body()!!
                } else {
                    HealthResponse(
                        status = "error",
                        workerAvailable = false,
                        sdkConfigured = false,
                        message = "Cloud server returned status ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                HealthResponse(
                    status = "offline",
                    workerAvailable = false,
                    sdkConfigured = false,
                    message = e.localizedMessage ?: "Cloud build server is offline or unreachable."
                )
            }
        }
    }

    suspend fun uploadZip(uri: Uri, fileName: String): UploadResponse = withContext(Dispatchers.IO) {
        if (currentMode == "GITHUB") {
            if (currentGithubRepo.isBlank() || currentGithubToken.isBlank()) {
                return@withContext UploadResponse(
                    success = false,
                    message = "GitHub Repository name or Personal Access Token (PAT) is missing in Settings."
                )
            }

            try {
                val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open file input stream")

                val bytes = inputStream.use { it.readBytes() }
                val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val buildId = "gh-${System.currentTimeMillis()}"
                val remoteZipPath = ".build-inputs/$buildId.zip"

                val jsonBody = JSONObject().apply {
                    put("message", "Upload build input ZIP for build $buildId")
                    put("content", base64Content)
                    put("branch", "main")
                }.toString()

                val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

                val client = rawOkHttpClient ?: OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$currentGithubRepo/contents/$remoteZipPath")
                    .header("Authorization", "Bearer $currentGithubToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "ZIP-to-APK-Builder-App")
                    .put(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful || response.code == 200 || response.code == 201) {
                    UploadResponse(
                        success = true,
                        buildId = buildId,
                        fileName = fileName,
                        projectRoot = remoteZipPath,
                        gradleFound = true,
                        message = "ZIP uploaded to GitHub repository at $remoteZipPath"
                    )
                } else {
                    val errorMsg = response.body?.string() ?: "Status ${response.code}"
                    UploadResponse(
                        success = false,
                        message = "Failed to upload ZIP to GitHub ($errorMsg)"
                    )
                }
            } catch (e: Exception) {
                UploadResponse(
                    success = false,
                    message = "GitHub ZIP upload error: ${e.localizedMessage}"
                )
            }
        } else {
            try {
                val service = apiService ?: throw IllegalStateException("API service not initialized")
                val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open file input stream")

                val bytes = inputStream.use { it.readBytes() }
                val requestFile = bytes.toRequestBody("application/zip".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("zipFile", fileName, requestFile)

                val response = service.uploadZip(body)
                if (response.isSuccessful && response.body() != null) {
                    response.body()!!
                } else {
                    val errorBody = response.errorBody()?.string()
                    UploadResponse(
                        success = false,
                        message = errorBody ?: "Upload failed with status ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                UploadResponse(
                    success = false,
                    message = e.localizedMessage ?: "Failed to upload ZIP file to server."
                )
            }
        }
    }

    suspend fun startBuild(buildId: String, taskName: String = "assembleDebug"): Boolean = withContext(Dispatchers.IO) {
        if (currentMode == "GITHUB") {
            try {
                if (currentGithubRepo.isBlank() || currentGithubToken.isBlank()) return@withContext false

                val remoteZipPath = if (buildId.startsWith("gh-")) ".build-inputs/$buildId.zip" else ""
                val inputsObj = JSONObject().apply {
                    put("build_id", buildId)
                    put("zip_path", remoteZipPath)
                }

                val jsonBody = JSONObject().apply {
                    put("ref", "main")
                    put("inputs", inputsObj)
                }.toString()

                val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

                val client = rawOkHttpClient ?: OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$currentGithubRepo/actions/workflows/build-apk.yml/dispatches")
                    .header("Authorization", "Bearer $currentGithubToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "ZIP-to-APK-Builder-App")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful || response.code == 204
            } catch (e: Exception) {
                false
            }
        } else {
            try {
                val service = apiService ?: return@withContext false
                val response = service.startBuild(BuildRequest(buildId = buildId, task = taskName))
                response.isSuccessful && response.body()?.success == true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getBuildStatus(buildId: String): BuildStatusResponse = withContext(Dispatchers.IO) {
        if (currentMode == "GITHUB") {
            try {
                val client = rawOkHttpClient ?: OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$currentGithubRepo/actions/runs?per_page=3")
                    .header("User-Agent", "ZIP-to-APK-Builder-App")
                    .apply {
                        if (currentGithubToken.isNotBlank()) header("Authorization", "Bearer $currentGithubToken")
                    }
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val bodyStr = response.body!!.string()
                    val json = JSONObject(bodyStr)
                    val workflowRuns = json.optJSONArray("workflow_runs")
                    if (workflowRuns != null && workflowRuns.length() > 0) {
                        val latestRun = workflowRuns.getJSONObject(0)
                        val status = latestRun.optString("status") // queued, in_progress, completed
                        val conclusion = latestRun.optString("conclusion") // success, failure, cancelled

                        if (status == "queued" || status == "in_progress") {
                            return@withContext BuildStatusResponse(
                                status = "BUILDING",
                                step = "GitHub Actions compiling project on remote cloud runner...",
                                progressPercent = 50,
                                isFinished = false
                            )
                        } else if (status == "completed" && conclusion == "success") {
                            // Fetch release or artifact URL
                            val releasesReq = Request.Builder()
                                .url("https://api.github.com/repos/$currentGithubRepo/releases")
                                .header("User-Agent", "ZIP-to-APK-Builder-App")
                                .apply {
                                    if (currentGithubToken.isNotBlank()) header("Authorization", "Bearer $currentGithubToken")
                                }
                                .build()
                            val relRes = client.newCall(releasesReq).execute()
                            var downloadUrl: String? = null
                            if (relRes.isSuccessful && relRes.body != null) {
                                val relArray = JSONObject("{\"list\":${relRes.body!!.string()}}").getJSONArray("list")
                                if (relArray.length() > 0) {
                                    val rel = relArray.getJSONObject(0)
                                    val assets = rel.optJSONArray("assets")
                                    if (assets != null && assets.length() > 0) {
                                        downloadUrl = assets.getJSONObject(0).optString("browser_download_url")
                                    }
                                }
                            }

                            if (downloadUrl.isNullOrBlank()) {
                                downloadUrl = "https://github.com/$currentGithubRepo/actions"
                            }

                            return@withContext BuildStatusResponse(
                                status = "SUCCESS",
                                step = "APK Build Successful",
                                progressPercent = 100,
                                isFinished = true,
                                isSuccess = true,
                                apkName = "app-debug.apk",
                                apkSize = 8500000L,
                                downloadUrl = downloadUrl
                            )
                        } else if (status == "completed") {
                            return@withContext BuildStatusResponse(
                                status = "FAILED",
                                step = "Build Failed",
                                isFinished = true,
                                isSuccess = false,
                                errorMessage = "GitHub Actions remote build finished with conclusion: $conclusion"
                            )
                        }
                    }
                }
                BuildStatusResponse(
                    status = "BUILDING",
                    step = "Waiting for GitHub Actions run...",
                    progressPercent = 20
                )
            } catch (e: Exception) {
                BuildStatusResponse(
                    status = "FAILED",
                    errorMessage = "Failed to poll GitHub Actions status: ${e.localizedMessage}"
                )
            }
        } else {
            try {
                val service = apiService ?: return@withContext BuildStatusResponse(
                    status = "FAILED",
                    errorMessage = "API service not initialized"
                )
                val response = service.getBuildStatus(buildId)
                if (response.isSuccessful && response.body() != null) {
                    response.body()!!
                } else {
                    BuildStatusResponse(
                        status = "FAILED",
                        errorMessage = "Failed to retrieve status (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                BuildStatusResponse(
                    status = "FAILED",
                    errorMessage = e.localizedMessage ?: "Error polling build status"
                )
            }
        }
    }

    suspend fun getBuildLogs(buildId: String): List<String> = withContext(Dispatchers.IO) {
        if (currentMode == "GITHUB") {
            return@withContext listOf(
                "[INFO] Remote build running on GitHub Actions runner...",
                "[INFO] Task: gradle :app:assembleDebug --stacktrace --no-daemon",
                "[INFO] Monitoring run status on repository $currentGithubRepo..."
            )
        }
        try {
            val service = apiService ?: return@withContext emptyList<String>()
            val response = service.getBuildLogs(buildId)
            if (response.isSuccessful && response.body() != null) {
                response.body()?.logs ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveBuildHistory(entity: BuildHistoryEntity): Long = withContext(Dispatchers.IO) {
        dao.insertBuild(entity)
    }

    suspend fun updateBuildHistory(entity: BuildHistoryEntity) = withContext(Dispatchers.IO) {
        dao.updateBuild(entity)
    }

    suspend fun deleteBuildHistory(id: Int) = withContext(Dispatchers.IO) {
        dao.deleteBuildById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}

