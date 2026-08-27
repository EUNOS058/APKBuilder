package com.apkbuilder.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkbuilder.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
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
            ListItem(
                headlineContent = { Text("GitHub Account") },
                supportingContent = { Text(state.githubUsername ?: "Not connected") }
            )
            ListItem(
                headlineContent = { Text("Repository") },
                supportingContent = { Text(state.repoName?.let { "${state.repoOwner}/$it" } ?: "Not set") }
            )
            ListItem(
                headlineContent = { Text("Branch") },
                supportingContent = { Text(state.branch) }
            )

            HorizontalDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dark Mode")
                Switch(
                    checked = darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }

            HorizontalDivider()

            Button(
                onClick = { viewModel.disconnectGitHub() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear GitHub Credentials")
            }

            OutlinedButton(
                onClick = { viewModel.clearHistory() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Build History")
            }

            HorizontalDivider()

            Text("About APK Builder", style = MaterialTheme.typography.titleMedium)
            Text(
                "Version 1.0.0\n" +
                        "Builds Android APKs using GitHub Actions.\n" +
                        "Token is stored encrypted locally and never uploaded to any repository.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
