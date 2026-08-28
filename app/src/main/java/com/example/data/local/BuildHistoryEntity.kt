package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_history")
data class BuildHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val buildId: String,
    val projectName: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // QUEUED, BUILDING, SUCCESS, FAILED, SERVER_UNAVAILABLE
    val buildDurationMs: Long = 0L,
    val apkFileName: String? = null,
    val apkSizeBytes: Long? = null,
    val apkDownloadUrl: String? = null,
    val logSummary: String? = null,
    val errorMessage: String? = null
)
