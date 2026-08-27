package com.apkbuilder.app

import android.app.Application
import com.apkbuilder.app.data.db.AppDatabase
import com.apkbuilder.app.data.github.GitHubManager
import com.apkbuilder.app.data.storage.FileManager
import com.apkbuilder.app.data.storage.SecureStorage

class APKBuilderApp : Application() {
    lateinit var secureStorage: SecureStorage
        private set
    lateinit var gitHubManager: GitHubManager
        private set
    lateinit var fileManager: FileManager
        private set
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        secureStorage = SecureStorage(this)
        gitHubManager = GitHubManager(secureStorage)
        fileManager = FileManager(this)
        database = AppDatabase.getInstance(this)
    }
}
