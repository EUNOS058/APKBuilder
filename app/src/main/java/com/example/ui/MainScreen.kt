package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ApkResultCard
import com.example.ui.components.AppHeader
import com.example.ui.components.BuildHistorySheet
import com.example.ui.components.BuildLogsTerminal
import com.example.ui.components.BuildProgressCard
import com.example.ui.components.ErrorCard
import com.example.ui.components.ServerSettingsDialog
import com.example.ui.components.ZipSelectorCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BuildViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val historyList by viewModel.buildHistory.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Storage Access Framework Launcher for picking ZIP files
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.selectFile(uri)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_screen_scaffold")
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header with Hero Banner and Server Status
                AppHeader(
                    serverHealth = uiState.serverHealth,
                    isCheckingServer = uiState.isCheckingServer,
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenHistory = { showHistorySheet = true },
                    onRefreshHealth = { viewModel.checkServerHealth() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Zip File Selector Card
                ZipSelectorCard(
                    selectedUri = uiState.selectedUri,
                    selectedFileName = uiState.selectedFileName,
                    selectedFileSize = uiState.selectedFileSize,
                    zipAnalysis = uiState.zipAnalysis,
                    selectedTask = uiState.selectedTask,
                    onTaskSelected = { viewModel.setTask(it) },
                    onSelectZipClicked = { zipPickerLauncher.launch("application/zip") },
                    onRemoveFileClicked = { viewModel.removeSelectedFile() },
                    onStartBuildClicked = { viewModel.startBuildProcess() },
                    isBuilding = uiState.buildStatus == "UPLOADING" || uiState.buildStatus == "BUILDING"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Real Build Progress Card
                AnimatedVisibility(
                    visible = uiState.buildStatus == "UPLOADING" || uiState.buildStatus == "BUILDING"
                ) {
                    Column {
                        BuildProgressCard(
                            currentStep = uiState.currentStep,
                            progressPercent = uiState.progressPercent,
                            buildStatus = uiState.buildStatus
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // APK Result Card (Shown only when real APK build succeeds)
                AnimatedVisibility(visible = uiState.buildStatus == "SUCCESS") {
                    Column {
                        ApkResultCard(
                            apkFileName = uiState.apkFileName,
                            apkSizeBytes = uiState.apkSize,
                            apkDownloadUrl = uiState.apkDownloadUrl,
                            buildDurationMs = uiState.buildDurationMs,
                            onStartNewBuild = { viewModel.startNewBuild() }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Error Card (Shown when build fails or server unavailable)
                AnimatedVisibility(
                    visible = uiState.buildStatus == "FAILED" || uiState.buildStatus == "SERVER_UNAVAILABLE"
                ) {
                    Column {
                        ErrorCard(
                            isServerUnavailable = uiState.buildStatus == "SERVER_UNAVAILABLE",
                            errorMessage = uiState.errorMessage,
                            onRetryClicked = { viewModel.startBuildProcess() },
                            onConfigureServerClicked = { showSettingsDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Real Terminal Logs Console
                AnimatedVisibility(visible = uiState.logs.isNotEmpty()) {
                    BuildLogsTerminal(
                        logs = uiState.logs,
                        isExpanded = uiState.isLogsExpanded,
                        onToggleExpanded = { viewModel.toggleLogsExpanded() }
                    )
                }
            }
        }
    }

    // Server Settings Dialog
    if (showSettingsDialog) {
        ServerSettingsDialog(
            currentUrl = uiState.serverUrl,
            buildMode = uiState.buildMode,
            githubRepo = uiState.githubRepo,
            githubToken = uiState.githubToken,
            serverHealth = uiState.serverHealth,
            isChecking = uiState.isCheckingServer,
            onSaveSettings = { url, mode, repo, token ->
                viewModel.updateServerSettings(url, mode, repo, token)
            },
            onTestConnection = { viewModel.checkServerHealth() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Build History Bottom Sheet
    if (showHistorySheet) {
        BuildHistorySheet(
            historyList = historyList,
            onDeleteHistoryItem = { viewModel.deleteHistoryItem(it) },
            onClearAll = { viewModel.clearHistory() },
            onDismiss = { showHistorySheet = false },
            sheetState = historySheetState
        )
    }
}
