package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BuildHistoryEntity
import com.example.data.remote.HealthResponse
import com.example.data.repository.BuildRepository
import com.example.utils.ZipAnalysisResult
import com.example.utils.ZipInspector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuildUiState(
    val selectedUri: Uri? = null,
    val selectedFileName: String? = null,
    val selectedFileSize: Long = 0L,
    val zipAnalysis: ZipAnalysisResult? = null,
    val serverUrl: String = "https://my-cloud-apk-builder.onrender.com/",
    val buildMode: String = "CLOUD", // "CLOUD" or "GITHUB"
    val githubRepo: String = "",
    val githubToken: String = "",
    val serverHealth: HealthResponse? = null,
    val isCheckingServer: Boolean = false,
    val buildId: String? = null,
    val buildStatus: String = "IDLE", // IDLE, ANALYZING, UPLOADING, QUEUED, BUILDING, SUCCESS, FAILED, SERVER_UNAVAILABLE
    val currentStep: String = "Ready",
    val progressPercent: Int = 0,
    val logs: List<String> = emptyList(),
    val apkFileName: String? = null,
    val apkSize: Long? = null,
    val apkDownloadUrl: String? = null,
    val errorMessage: String? = null,
    val startTimeMs: Long = 0L,
    val buildDurationMs: Long = 0L,
    val selectedTask: String = "assembleDebug",
    val isLogsExpanded: Boolean = true
)

class BuildViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BuildRepository(application.applicationContext)
    val buildHistory: StateFlow<List<BuildHistoryEntity>> = repository.buildHistory
        .let { flow ->
            val mutableState = MutableStateFlow<List<BuildHistoryEntity>>(emptyList())
            viewModelScope.launch {
                flow.collect { mutableState.value = it }
            }
            mutableState.asStateFlow()
        }

    private val _uiState = MutableStateFlow(
        BuildUiState(
            serverUrl = repository.getBaseUrl(),
            buildMode = repository.getBuildMode(),
            githubRepo = repository.getGitHubRepo(),
            githubToken = repository.getGitHubToken()
        )
    )
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        checkServerHealth()
    }

    fun updateServerSettings(url: String, mode: String, repo: String, token: String) {
        repository.updateSettings(url, mode, repo, token)
        _uiState.update {
            it.copy(
                serverUrl = repository.getBaseUrl(),
                buildMode = repository.getBuildMode(),
                githubRepo = repository.getGitHubRepo(),
                githubToken = repository.getGitHubToken()
            )
        }
        checkServerHealth()
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingServer = true) }
            val health = repository.checkHealth()
            _uiState.update {
                it.copy(
                    serverHealth = health,
                    isCheckingServer = false
                )
            }
        }
    }

    fun selectFile(uri: Uri) {
        val context = getApplication<Application>().applicationContext
        var fileName = "project.zip"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "project.zip"
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val analysis = ZipInspector.inspectZipUri(context, uri)

        _uiState.update {
            it.copy(
                selectedUri = uri,
                selectedFileName = fileName,
                selectedFileSize = fileSize,
                zipAnalysis = analysis,
                buildStatus = "IDLE",
                errorMessage = null,
                apkFileName = null,
                apkDownloadUrl = null,
                logs = emptyList(),
                progressPercent = 0
            )
        }
    }

    fun removeSelectedFile() {
        _uiState.update {
            it.copy(
                selectedUri = null,
                selectedFileName = null,
                selectedFileSize = 0L,
                zipAnalysis = null,
                buildStatus = "IDLE",
                errorMessage = null,
                apkFileName = null,
                apkDownloadUrl = null,
                logs = emptyList(),
                progressPercent = 0
            )
        }
    }

    fun setTask(task: String) {
        _uiState.update { it.copy(selectedTask = task) }
    }

    fun toggleLogsExpanded() {
        _uiState.update { it.copy(isLogsExpanded = !it.isLogsExpanded) }
    }

    fun startBuildProcess() {
        val state = _uiState.value
        val uri = state.selectedUri ?: return
        val fileName = state.selectedFileName ?: "project.zip"

        viewModelScope.launch {
            // First check server health
            _uiState.update {
                it.copy(
                    buildStatus = "UPLOADING",
                    currentStep = "Connecting to build server...",
                    progressPercent = 5,
                    errorMessage = null,
                    logs = listOf("[INFO] Initiating connection to Android Build Server..."),
                    startTimeMs = System.currentTimeMillis()
                )
            }

            val health = repository.checkHealth()
            _uiState.update { it.copy(serverHealth = health) }

            if (health.status == "offline" || !health.workerAvailable) {
                val error = "Build server is unavailable. ${health.message ?: "Please ensure the Docker build worker backend is running."}"
                _uiState.update {
                    it.copy(
                        buildStatus = "SERVER_UNAVAILABLE",
                        currentStep = "Build server unavailable",
                        errorMessage = error,
                        logs = it.logs + listOf(
                            "[ERROR] $error",
                            "[HINT] Follow README instructions to run Docker container or Node.js backend server."
                        )
                    )
                }
                saveHistoryRecord("SERVER_UNAVAILABLE", error)
                return@launch
            }

            // Upload ZIP
            _uiState.update {
                it.copy(
                    currentStep = "Uploading project ZIP...",
                    progressPercent = 15,
                    logs = it.logs + listOf("[INFO] Uploading '$fileName' (${state.selectedFileSize / 1024} KB)...")
                )
            }

            val uploadRes = repository.uploadZip(uri, fileName)
            if (!uploadRes.success || uploadRes.buildId.isBlank()) {
                val error = uploadRes.message ?: "Failed to upload project ZIP to server."
                _uiState.update {
                    it.copy(
                        buildStatus = "FAILED",
                        currentStep = "Upload failed",
                        errorMessage = error,
                        logs = it.logs + listOf("[ERROR] Upload failed: $error")
                    )
                }
                saveHistoryRecord("FAILED", error)
                return@launch
            }

            val buildId = uploadRes.buildId
            _uiState.update {
                it.copy(
                    buildId = buildId,
                    currentStep = "Project uploaded. Preparing workspace...",
                    progressPercent = 25,
                    logs = it.logs + listOf(
                        "[SUCCESS] ZIP uploaded successfully. Build ID: $buildId",
                        "[INFO] Detected project root: ${uploadRes.projectRoot}",
                        "[INFO] Gradle wrapper present: ${uploadRes.gradleFound}"
                    )
                )
            }

            // Trigger real build
            val started = repository.startBuild(buildId, state.selectedTask)
            if (!started) {
                val error = "Failed to trigger build worker process."
                _uiState.update {
                    it.copy(
                        buildStatus = "FAILED",
                        currentStep = "Build initiation failed",
                        errorMessage = error,
                        logs = it.logs + listOf("[ERROR] $error")
                    )
                }
                saveHistoryRecord("FAILED", error)
                return@launch
            }

            _uiState.update {
                it.copy(
                    buildStatus = "BUILDING",
                    currentStep = "Compilation started...",
                    progressPercent = 30
                )
            }

            // Start polling status and logs
            startPollingStatus(buildId)
        }
    }

    private fun startPollingStatus(buildId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var finished = false
            while (!finished) {
                delay(1500)
                val statusRes = repository.getBuildStatus(buildId)
                val newLogs = repository.getBuildLogs(buildId)

                _uiState.update { current ->
                    current.copy(
                        currentStep = statusRes.step ?: current.currentStep,
                        progressPercent = statusRes.progressPercent ?: current.progressPercent,
                        logs = if (newLogs.isNotEmpty()) newLogs else current.logs
                    )
                }

                if (statusRes.isFinished) {
                    finished = true
                    val duration = System.currentTimeMillis() - _uiState.value.startTimeMs

                    if (statusRes.isSuccess && !statusRes.apkName.isNullOrBlank()) {
                        val fullDownloadUrl = if (statusRes.downloadUrl?.startsWith("http") == true) {
                            statusRes.downloadUrl
                        } else {
                            "${repository.getBaseUrl()}api/download/$buildId"
                        }

                        _uiState.update {
                            it.copy(
                                buildStatus = "SUCCESS",
                                currentStep = "APK Build Successful",
                                progressPercent = 100,
                                apkFileName = statusRes.apkName,
                                apkSize = statusRes.apkSize,
                                apkDownloadUrl = fullDownloadUrl,
                                buildDurationMs = duration,
                                logs = it.logs + listOf("[SUCCESS] APK generated: ${statusRes.apkName} (${(statusRes.apkSize ?: 0) / 1024 / 1024} MB)")
                            )
                        }
                        saveHistoryRecord("SUCCESS", null)
                    } else {
                        val error = statusRes.errorMessage ?: "Build completed but APK file was not found."
                        _uiState.update {
                            it.copy(
                                buildStatus = "FAILED",
                                currentStep = "Build Failed",
                                errorMessage = error,
                                buildDurationMs = duration,
                                logs = it.logs + listOf("[ERROR] $error")
                            )
                        }
                        saveHistoryRecord("FAILED", error)
                    }
                }
            }
        }
    }

    private fun saveHistoryRecord(status: String, errorMsg: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            val entity = BuildHistoryEntity(
                buildId = state.buildId ?: "LOCAL-${System.currentTimeMillis()}",
                projectName = state.selectedFileName?.removeSuffix(".zip") ?: "Android Project",
                fileName = state.selectedFileName ?: "project.zip",
                fileSizeBytes = state.selectedFileSize,
                status = status,
                buildDurationMs = state.buildDurationMs,
                apkFileName = state.apkFileName,
                apkSizeBytes = state.apkSize,
                apkDownloadUrl = state.apkDownloadUrl,
                logSummary = state.logs.takeLast(5).joinToString("\n"),
                errorMessage = errorMsg
            )
            repository.saveBuildHistory(entity)
        }
    }

    fun startNewBuild() {
        pollingJob?.cancel()
        _uiState.update {
            it.copy(
                buildId = null,
                buildStatus = "IDLE",
                currentStep = "Ready",
                progressPercent = 0,
                logs = emptyList(),
                apkFileName = null,
                apkSize = null,
                apkDownloadUrl = null,
                errorMessage = null
            )
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteBuildHistory(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
