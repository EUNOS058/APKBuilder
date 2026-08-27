package com.apkbuilder.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_history")
data class BuildHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectName: String,
    val repository: String,
    val buildDate: Long,
    val status: String,
    val apkName: String?,
    val workflowRunId: Long?,
    val branch: String = "main"
)
