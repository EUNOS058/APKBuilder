package com.apkbuilder.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkbuilder.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val run = state.currentRun

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Build Status") })
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (run == null) {
                Text("No active build. Trigger a build from Home.")
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Workflow: ${run.name ?: "Build APK"}", fontWeight = FontWeight.Bold)
                        Text("Run #${run.runNumber}")
                        Text("Branch: ${run.headBranch}")
                        Text("SHA: ${run.headSha?.take(7)}")
                        Text("Status: ${run.status}")
                        Text("Conclusion: ${run.conclusion ?: "-"}")
                        Text("Started: ${run.runStartedAt ?: run.createdAt}")
                        Text("Updated: ${run.updatedAt}")
                    }
                }

                if (state.isPolling) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Auto-refreshing every 5 seconds...")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.startPolling() }) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                    if (state.isPolling) {
                        OutlinedButton(onClick = { viewModel.stopPolling() }) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                }

                if (run.conclusion == "success" && state.artifacts.isNotEmpty()) {
                    Text("APK Build Successful", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    state.artifacts.forEach { art ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(art.name)
                                Text("${art.size_in_bytes / 1024} KB")
                                Button(onClick = { viewModel.downloadApk(art) }) {
                                    Text("Download APK")
                                }
                            }
                        }
                    }
                } else if (run.conclusion == "failure") {
                    Text("Build Failed", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text("Open the workflow on GitHub to see detailed logs.")
                }
            }
        }
    }
}
