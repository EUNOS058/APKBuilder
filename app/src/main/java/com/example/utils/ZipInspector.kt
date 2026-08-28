package com.example.utils

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

data class ZipAnalysisResult(
    val isValidZip: Boolean,
    val isAndroidProject: Boolean,
    val totalFiles: Int,
    val detectedRootFolder: String?,
    val hasSettingsGradle: Boolean,
    val hasBuildGradle: Boolean,
    val hasGradleWrapper: Boolean,
    val hasAppModule: Boolean,
    val errorMessage: String? = null
)

object ZipInspector {

    fun inspectZipUri(context: Context, uri: Uri): ZipAnalysisResult {
        var totalFiles = 0
        var hasSettingsGradle = false
        var hasBuildGradle = false
        var hasGradleWrapper = false
        var hasGradleDir = false
        var hasAppModule = false
        val rootPathsWithGradle = mutableListOf<String>()

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ZipAnalysisResult(
                    isValidZip = false,
                    isAndroidProject = false,
                    totalFiles = 0,
                    detectedRootFolder = null,
                    hasSettingsGradle = false,
                    hasBuildGradle = false,
                    hasGradleWrapper = false,
                    hasAppModule = false,
                    errorMessage = "Could not open selected file."
                )

            ZipInputStream(BufferedInputStream(inputStream)).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (name.isNotEmpty()) {
                        totalFiles++
                    }

                    // Path traversal check
                    if (name.contains("../")) {
                        return ZipAnalysisResult(
                            isValidZip = false,
                            isAndroidProject = false,
                            totalFiles = totalFiles,
                            detectedRootFolder = null,
                            hasSettingsGradle = false,
                            hasBuildGradle = false,
                            hasGradleWrapper = false,
                            hasAppModule = false,
                            errorMessage = "Security Warning: ZIP contains illegal path traversal components."
                        )
                    }

                    val parts = name.split("/").filter { it.isNotEmpty() }
                    val fileName = parts.lastOrNull() ?: ""

                    val isSettings = fileName == "settings.gradle" || fileName == "settings.gradle.kts"
                    val isBuild = fileName == "build.gradle" || fileName == "build.gradle.kts"
                    val isWrapper = fileName == "gradlew" || fileName == "gradlew.bat"
                    val isGradleDir = name.contains("/gradle/") || name.startsWith("gradle/")

                    if (isSettings) hasSettingsGradle = true
                    if (isBuild) hasBuildGradle = true
                    if (isWrapper) hasGradleWrapper = true
                    if (isGradleDir) hasGradleDir = true

                    if (name.contains("/app/") || name.startsWith("app/") || fileName == "AndroidManifest.xml") {
                        hasAppModule = true
                    }

                    if (isSettings || isBuild) {
                        val parentFolder = if (parts.size > 1) {
                            parts.dropLast(1).joinToString("/")
                        } else {
                            ""
                        }
                        rootPathsWithGradle.add(parentFolder)
                    }

                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }

            if (totalFiles == 0) {
                return ZipAnalysisResult(
                    isValidZip = false,
                    isAndroidProject = false,
                    totalFiles = 0,
                    detectedRootFolder = null,
                    hasSettingsGradle = false,
                    hasBuildGradle = false,
                    hasGradleWrapper = false,
                    hasAppModule = false,
                    errorMessage = "The selected ZIP file is empty."
                )
            }

            val isAndroid = hasSettingsGradle || hasBuildGradle || (hasAppModule && (hasGradleWrapper || hasGradleDir))

            val detectedRoot = if (rootPathsWithGradle.isNotEmpty()) {
                val shallowest = rootPathsWithGradle.minByOrNull { it.length } ?: ""
                if (shallowest.isEmpty()) null else shallowest
            } else {
                null
            }

            val errorMsg = if (!isAndroid) {
                "Invalid Android Project: missing build.gradle or settings.gradle file anywhere in the ZIP archive."
            } else null

            return ZipAnalysisResult(
                isValidZip = true,
                isAndroidProject = isAndroid,
                totalFiles = totalFiles,
                detectedRootFolder = detectedRoot,
                hasSettingsGradle = hasSettingsGradle,
                hasBuildGradle = hasBuildGradle,
                hasGradleWrapper = hasGradleWrapper || hasGradleDir,
                hasAppModule = hasAppModule,
                errorMessage = errorMsg
            )

        } catch (e: Exception) {
            return ZipAnalysisResult(
                isValidZip = false,
                isAndroidProject = false,
                totalFiles = 0,
                detectedRootFolder = null,
                hasSettingsGradle = false,
                hasBuildGradle = false,
                hasGradleWrapper = false,
                hasAppModule = false,
                errorMessage = "Failed to parse ZIP file: ${e.localizedMessage}"
            )
        }
    }
}
