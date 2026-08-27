package com.apkbuilder.app.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ProjectFile(
    val path: String,
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProjectFile
        return path == other.path && content.contentEquals(other.content)
    }
    override fun hashCode(): Int = 31 * path.hashCode() + content.contentHashCode()
}

data class ZipValidationResult(
    val isValid: Boolean,
    val message: String,
    val files: List<ProjectFile> = emptyList(),
    val fileCount: Int = 0
)

class FileManager(private val context: Context) {

    fun getFileName(uri: Uri): String {
        var name = "project.zip"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    fun getFileSize(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    fun validateAndExtractZip(uri: Uri): ZipValidationResult {
        val files = mutableListOf<ProjectFile>()
        var hasSettings = false
        var hasBuildGradle = false
        var hasGradlew = false
        var hasAppModule = false
        var hasManifest = false

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/')

                        // Security: block path traversal
                        if (name.contains("..") || name.startsWith("/") || name.contains(":")) {
                            return ZipValidationResult(false, "Unsafe path detected: $name")
                        }

                        // Skip directories and ignored folders
                        if (!entry.isDirectory && shouldInclude(name)) {
                            val content = readEntry(zis)
                            // Limit single file size to 5MB to avoid OOM
                            if (content.size > 5 * 1024 * 1024) {
                                // skip very large files
                            } else {
                                files.add(ProjectFile(name, content))
                            }

                            val lower = name.lowercase()
                            if (lower.endsWith("settings.gradle") || lower.endsWith("settings.gradle.kts")) hasSettings = true
                            if (lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts")) hasBuildGradle = true
                            if (lower.endsWith("gradlew") || lower.endsWith("gradlew.bat")) hasGradlew = true
                            if (lower.contains("/app/") || lower.startsWith("app/")) hasAppModule = true
                            if (lower.endsWith("androidmanifest.xml")) hasManifest = true
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return ZipValidationResult(false, "Cannot open ZIP file")
        } catch (e: Exception) {
            return ZipValidationResult(false, "Invalid or corrupted ZIP: ${e.message}")
        }

        if (files.isEmpty()) {
            return ZipValidationResult(false, "ZIP is empty or contains no usable files")
        }

        // Soft validation - at least some Android project indicators
        val score = listOf(hasSettings, hasBuildGradle, hasGradlew, hasAppModule, hasManifest).count { it }
        if (score < 2) {
            return ZipValidationResult(
                false,
                "This does not look like an Android Gradle project.\n" +
                        "Expected files like settings.gradle, build.gradle, gradlew, app/, AndroidManifest.xml"
            )
        }

        return ZipValidationResult(
            isValid = true,
            message = "Valid Android project ($score/5 indicators found). ${files.size} files ready to upload.",
            files = files,
            fileCount = files.size
        )
    }

    private fun shouldInclude(path: String): Boolean {
        val lower = path.lowercase()
        val ignored = listOf(
            "/build/", "/.gradle/", "/.idea/", "/local.properties",
            "/.git/", "/captures/", "/.cxx/", "/kotlin/",
            "keystore", ".jks", ".keystore", "password", "secret",
            "/.DS_Store", "thumbs.db"
        )
        return ignored.none { lower.contains(it) }
    }

    private fun readEntry(zis: ZipInputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(8192)
        var count: Int
        while (zis.read(data).also { count = it } != -1) {
            buffer.write(data, 0, count)
        }
        return buffer.toByteArray()
    }
}
