package com.apkbuilder.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkbuilder.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToGitHub: () -> Unit,
    onNavigateToBuilds: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectZip(it) }
    }

    if (state.showWelcome) {
        WelcomeDialog(onDismiss = { viewModel.dismissWelcome() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APK Builder", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = {
            state.message?.let { msg ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearMessage() }) {
                            Text("OK")
                        }
                    }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status cards
            StatusCard(
                title = "GitHub",
                value = if (state.githubConnected) "Connected as ${state.githubUsername}" else "Not connected",
                icon = Icons.Default.Cloud,
                isOk = state.githubConnected
            )
            StatusCard(
                title = "Repository",
                value = state.repoName?.let { "${state.repoOwner}/$it" } ?: "Not set",
                icon = Icons.Default.Folder,
                isOk = state.repoName != null
            )
            StatusCard(
                title = "Selected ZIP",
                value = state.selectedZipName ?: "None",
                icon = Icons.Default.Archive,
                isOk = state.selectedZipName != null
            )
            if (state.zipValidation != null) {
                StatusCard(
                    title = "Validation",
                    value = state.zipValidation!!.message,
                    icon = if (state.zipValidation!!.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                    isOk = state.zipValidation!!.isValid
                )
            }
            if (state.currentRun != null) {
                val run = state.currentRun!!
                StatusCard(
                    title = "Latest Build",
                    value = "${run.status} / ${run.conclusion ?: "-"} (#${run.runNumber})",
                    icon = Icons.Default.Build,
                    isOk = run.conclusion == "success"
                )
            }

            if (state.isLoading || state.isPolling) {
                LinearProgressIndicator(
                    progress = { if (state.uploadProgress > 0) state.uploadProgress else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                state.uploadCurrentFile?.let {
                    Text("Uploading: $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Button(
                onClick = { zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Select Project ZIP")
            }

            OutlinedButton(
                onClick = onNavigateToGitHub,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Link, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.githubConnected) "GitHub Settings" else "Connect GitHub")
            }

            Button(
                onClick = { viewModel.uploadProject() },
                enabled = state.githubConnected && state.zipValidation?.isValid == true && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Upload Project")
            }

            Button(
                onClick = { viewModel.startBuild() },
                enabled = state.githubConnected && state.repoName != null && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Build APK")
            }

            OutlinedButton(
                onClick = onNavigateToBuilds,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text("View Build Status")
            }

            if (state.artifacts.isNotEmpty()) {
                Text("APK Build Successful", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                state.artifacts.forEach { artifact ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(artifact.name, fontWeight = FontWeight.Bold)
                            Text("Size: ${artifact.size_in_bytes / 1024} KB")
                            Button(
                                onClick = { viewModel.downloadApk(artifact) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download APK")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isOk: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to APK Builder") },
        text = {
            Text(
                "1. Connect your GitHub account\n" +
                        "2. Select an Android project ZIP\n" +
                        "3. Upload it to GitHub\n" +
                        "4. Start GitHub Actions build\n" +
                        "5. Download the APK\n\n" +
                        "Your Personal Access Token is stored securely on this device and never uploaded."
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Get Started") }
        }
    )
}
