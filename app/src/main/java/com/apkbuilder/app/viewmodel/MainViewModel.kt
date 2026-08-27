package com.apkbuilder.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkbuilder.app.APKBuilderApp
import com.apkbuilder.app.data.db.BuildHistoryEntity
import com.apkbuilder.app.data.github.Artifact
import com.apkbuilder.app.data.github.GitHubRepo
import com.apkbuilder.app.data.github.WorkflowRun
import com.apkbuilder.app.data.storage.ProjectFile
import com.apkbuilder.app.data.storage.ZipValidationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val githubConnected: Boolean = false,
    val githubUsername: String? = null,
    val repoOwner: String? = null,
    val repoName: String? = null,
    val branch: String = "main",
    val selectedZipName: String? = null,
    val selectedZipSize: Long = 0,
    val zipValidation: ZipValidationResult? = null,
    val extractedFiles: List<ProjectFile> = emptyList(),
    val uploadProgress: Float = 0f,
    val uploadCurrentFile: String? = null,
    val filesUploaded: Int = 0,
    val totalFiles: Int = 0,
    val currentRun: WorkflowRun? = null,
    val isPolling: Boolean = false,
    val artifacts: List<Artifact> = emptyList(),
    val downloadedApkPath: String? = null,
    val repos: List<GitHubRepo> = emptyList(),
    val showWelcome: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as APKBuilderApp
    private val storage = app.secureStorage
    private val gitHub = app.gitHubManager
    private val fileManager = app.fileManager
    private val historyDao = app.database.buildHistoryDao()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val history: StateFlow<List<BuildHistoryEntity>> = historyDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _darkMode = MutableStateFlow(storage.isDarkMode())
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private var pollingJob: Job? = null
    private var selectedUri: Uri? = null

    init {
        refreshConnectionState()
        val hasCreds = storage.hasCredentials()
        _uiState.update { it.copy(showWelcome = !hasCreds) }
    }

    fun dismissWelcome() {
        _uiState.update { it.copy(showWelcome = false) }
    }

    fun setDarkMode(enabled: Boolean) {
        storage.setDarkMode(enabled)
        _darkMode.value = enabled
    }

    private fun refreshConnectionState() {
        _uiState.update {
            it.copy(
                githubConnected = storage.hasCredentials(),
                githubUsername = storage.getUsername(),
                repoOwner = storage.getRepoOwner(),
                repoName = storage.getRepoName(),
                branch = storage.getBranch()
            )
        }
    }

    fun saveGitHubCredentials(token: String, username: String, owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            storage.saveToken(token.trim())
            storage.saveUsername(username.trim())
            storage.saveRepoOwner(owner.trim().ifBlank { username.trim() })
            storage.saveRepoName(repo.trim())
            storage.saveBranch(branch.trim().ifBlank { "main" })
            gitHub.invalidateApi()

            val result = gitHub.testConnection()
            result.fold(
                onSuccess = { user ->
                    storage.saveUsername(user.login)
                    refreshConnectionState()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "Connected as ${user.login}",
                            isError = false,
                            githubConnected = true
                        )
                    }
                },
                onFailure = { e ->
                    storage.clearToken()
                    gitHub.invalidateApi()
                    refreshConnectionState()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "Connection failed: ${e.message}",
                            isError = true
                        )
                    }
                }
            )
        }
    }

    fun disconnectGitHub() {
        storage.clearToken()
        gitHub.invalidateApi()
        refreshConnectionState()
        _uiState.update { it.copy(message = "Disconnected", isError = false) }
    }

    fun loadRepos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            gitHub.getRepos().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(isLoading = false, repos = list) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, message = e.message, isError = true) }
                }
            )
        }
    }

    fun selectZip(uri: Uri) {
        selectedUri = uri
        val name = fileManager.getFileName(uri)
        val size = fileManager.getFileSize(uri)
        _uiState.update {
            it.copy(
                selectedZipName = name,
                selectedZipSize = size,
                zipValidation = null,
                extractedFiles = emptyList(),
                message = null
            )
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Validating ZIP...") }
            val result = fileManager.validateAndExtractZip(uri)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    zipValidation = result,
                    extractedFiles = result.files,
                    message = result.message,
                    isError = !result.isValid
                )
            }
        }
    }

    fun uploadProject() {
        val state = _uiState.value
        val owner = state.repoOwner ?: return showError("Set repository owner first")
        val repo = state.repoName ?: return showError("Set repository name first")
        val branch = state.branch
        val files = state.extractedFiles
        if (files.isEmpty()) return showError("No files to upload. Select a valid ZIP first.")

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    uploadProgress = 0f,
                    filesUploaded = 0,
                    totalFiles = files.size,
                    message = "Uploading files..."
                )
            }

            var successCount = 0
            for ((index, file) in files.withIndex()) {
                _uiState.update {
                    it.copy(
                        uploadCurrentFile = file.path,
                        uploadProgress = (index + 1).toFloat() / files.size,
                        filesUploaded = index
                    )
                }
                val result = gitHub.uploadFile(
                    owner = owner,
                    repo = repo,
                    path = file.path,
                    contentBytes = file.content,
                    message = "Upload ${file.path} via APK Builder",
                    branch = branch
                )
                if (result.isSuccess) {
                    successCount++
                } else {
                    // Continue but note failure
                }
                delay(150) // gentle rate limit
            }

            // Create workflow
            _uiState.update { it.copy(message = "Creating workflow file...") }
            val wfResult = gitHub.createWorkflowFile(owner, repo, branch, storage.getBuildType())
            if (wfResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "Files uploaded ($successCount/${files.size}) but workflow creation failed: ${wfResult.exceptionOrNull()?.message}",
                        isError = true
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = "Upload complete: $successCount/${files.size} files + workflow ready",
                    isError = false,
                    filesUploaded = successCount
                )
            }
        }
    }

    fun startBuild() {
        val state = _uiState.value
        val owner = state.repoOwner ?: return showError("Repository not set")
        val repo = state.repoName ?: return showError("Repository not set")
        val branch = state.branch

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Triggering GitHub Actions...") }
            val result = gitHub.triggerBuild(owner, repo, branch)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, message = "Build triggered! Polling status...") }
                    startPolling()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, message = "Trigger failed: ${e.message}", isError = true)
                    }
                }
            )
        }
    }

    fun startPolling() {
        stopPolling()
        val owner = _uiState.value.repoOwner ?: return
        val repo = _uiState.value.repoName ?: return
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isPolling = true) }
            // Wait a bit for run to appear
            delay(3000)
            repeat(120) { // ~10 minutes max
                val runs = gitHub.getLatestRuns(owner, repo, _uiState.value.branch)
                runs.onSuccess { list ->
                    val latest = list.firstOrNull()
                    if (latest != null) {
                        _uiState.update { it.copy(currentRun = latest) }
                        if (latest.status == "completed") {
                            _uiState.update { it.copy(isPolling = false) }
                            if (latest.conclusion == "success") {
                                fetchArtifacts(owner, repo, latest.id)
                                saveHistory(latest, "success")
                            } else {
                                saveHistory(latest, latest.conclusion ?: "failed")
                                _uiState.update {
                                    it.copy(
                                        message = "Build Failed: ${latest.conclusion}",
                                        isError = true
                                    )
                                }
                            }
                            return@launch
                        }
                    }
                }
                delay(5000)
            }
            _uiState.update { it.copy(isPolling = false, message = "Polling timed out") }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    private fun fetchArtifacts(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            gitHub.getArtifacts(owner, repo, runId).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            artifacts = list,
                            message = if (list.isNotEmpty()) "APK Build Successful" else "Build succeeded but no artifact found",
                            isError = list.isEmpty()
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = "Could not fetch artifacts: ${e.message}", isError = true) }
                }
            )
        }
    }

    fun downloadApk(artifact: Artifact) {
        val url = artifact.archiveDownloadUrl ?: return showError("No download URL")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Downloading APK...") }
            gitHub.downloadArtifactBytes(url).fold(
                onSuccess = { bytes ->
                    try {
                        val dir = File(getApplication<Application>().cacheDir, "apks").apply { mkdirs() }
                        val outFile = File(dir, "${artifact.name}.zip")
                        outFile.writeBytes(bytes)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                downloadedApkPath = outFile.absolutePath,
                                message = "Downloaded to cache. You can share/save it."
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isLoading = false, message = "Save failed: ${e.message}", isError = true) }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, message = "Download failed: ${e.message}", isError = true) }
                }
            )
        }
    }

    private fun saveHistory(run: WorkflowRun, status: String) {
        viewModelScope.launch {
            historyDao.insert(
                BuildHistoryEntity(
                    projectName = _uiState.value.selectedZipName ?: "Unknown",
                    repository = "${_uiState.value.repoOwner}/${_uiState.value.repoName}",
                    buildDate = System.currentTimeMillis(),
                    status = status,
                    apkName = _uiState.value.artifacts.firstOrNull()?.name,
                    workflowRunId = run.id,
                    branch = run.headBranch ?: "main"
                )
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
            _uiState.update { it.copy(message = "History cleared") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private fun showError(msg: String) {
        _uiState.update { it.copy(message = msg, isError = true) }
    }
}

class MainViewModelFactory(private val app: APKBuilderApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(app) as T
    }
}
