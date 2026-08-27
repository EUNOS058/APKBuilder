package com.apkbuilder.app.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun saveRepoOwner(owner: String) {
        prefs.edit().putString(KEY_OWNER, owner).apply()
    }

    fun getRepoOwner(): String? = prefs.getString(KEY_OWNER, null) ?: getUsername()

    fun saveRepoName(name: String) {
        prefs.edit().putString(KEY_REPO, name).apply()
    }

    fun getRepoName(): String? = prefs.getString(KEY_REPO, null)

    fun saveBranch(branch: String) {
        prefs.edit().putString(KEY_BRANCH, branch).apply()
    }

    fun getBranch(): String = prefs.getString(KEY_BRANCH, "main") ?: "main"

    fun saveBuildType(type: String) {
        prefs.edit().putString(KEY_BUILD_TYPE, type).apply()
    }

    fun getBuildType(): String = prefs.getString(KEY_BUILD_TYPE, "debug") ?: "debug"

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, enabled).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun hasCredentials(): Boolean = !getToken().isNullOrBlank()

    companion object {
        private const val KEY_TOKEN = "github_token"
        private const val KEY_USERNAME = "github_username"
        private const val KEY_OWNER = "repo_owner"
        private const val KEY_REPO = "repo_name"
        private const val KEY_BRANCH = "branch"
        private const val KEY_BUILD_TYPE = "build_type"
        private const val KEY_DARK = "dark_mode"
    }
}
