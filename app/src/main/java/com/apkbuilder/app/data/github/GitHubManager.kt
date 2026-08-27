package com.apkbuilder.app.data.github

import android.util.Base64
import com.apkbuilder.app.data.storage.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GitHubManager(private val secureStorage: SecureStorage) {

    private var api: GitHubApiService? = null

    private fun createApi(token: String): GitHubApiService {
        val logging = HttpLoggingInterceptor().apply {
            // Never log Authorization header
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "APK-Builder-Android")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }

    private fun getApi(): GitHubApiService {
        val token = secureStorage.getToken()
            ?: throw IllegalStateException("GitHub token not found. Please connect GitHub first.")
        if (api == null) {
            api = createApi(token)
        }
        return api!!
    }

    fun invalidateApi() {
        api = null
    }

    suspend fun testConnection(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val response = getApi().getAuthenticatedUser()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRepos(): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        try {
            val response = getApi().getUserRepos()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Failed to load repos"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRepo(name: String, isPrivate: Boolean = true): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            val response = getApi().createRepo(CreateRepoRequest(name = name, private = isPrivate))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Failed to create repository"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        owner: String,
        repo: String,
        path: String,
        contentBytes: ByteArray,
        message: String,
        branch: String = "main"
    ): Result<ContentResponse> = withContext(Dispatchers.IO) {
        try {
            val base64 = Base64.encodeToString(contentBytes, Base64.NO_WRAP)
            // Try to get existing SHA for update
            var sha: String? = null
            try {
                val existing = getApi().getFileContent(owner, repo, path, branch)
                if (existing.isSuccessful) {
                    sha = existing.body()?.sha
                }
            } catch (_: Exception) { /* file does not exist */ }

            val body = ContentRequest(
                message = message,
                content = base64,
                branch = branch,
                sha = sha
            )
            val response = getApi().createOrUpdateFile(owner, repo, path, body)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Upload failed for $path"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createWorkflowFile(
        owner: String,
        repo: String,
        branch: String,
        buildType: String = "debug"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val workflowYml = generateWorkflowYaml(buildType)
        val result = uploadFile(
            owner = owner,
            repo = repo,
            path = ".github/workflows/build-apk.yml",
            contentBytes = workflowYml.toByteArray(Charsets.UTF_8),
            message = "Add/Update APK Builder workflow",
            branch = branch
        )
        result.map { }
    }

    suspend fun triggerBuild(
        owner: String,
        repo: String,
        branch: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = getApi().triggerWorkflow(
                owner = owner,
                repo = repo,
                workflowId = "build-apk.yml",
                body = WorkflowDispatchRequest(ref = branch)
            )
            if (response.isSuccessful || response.code() == 204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Failed to trigger workflow (code ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestRuns(owner: String, repo: String, branch: String? = null): Result<List<WorkflowRun>> =
        withContext(Dispatchers.IO) {
            try {
                val response = getApi().getWorkflowRuns(owner, repo, branch = branch)
                if (response.isSuccessful) {
                    Result.success(response.body()?.workflowRuns ?: emptyList())
                } else {
                    Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Failed to get runs"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getRun(owner: String, repo: String, runId: Long): Result<WorkflowRun> =
        withContext(Dispatchers.IO) {
            try {
                val response = getApi().getWorkflowRun(owner, repo, runId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Failed to get run"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getArtifacts(owner: String, repo: String, runId: Long): Result<List<Artifact>> =
        withContext(Dispatchers.IO) {
            try {
                val response = getApi().getArtifacts(owner, repo, runId)
                if (response.isSuccessful) {
                    Result.success(response.body()?.artifacts ?: emptyList())
                } else {
                    Result.failure(Exception(parseError(response.errorBody()?.string()) ?: "Failed to get artifacts"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadArtifactBytes(downloadUrl: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val response = getApi().downloadArtifact(downloadUrl)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.bytes())
            } else {
                Result.failure(Exception("Download failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            // Simple extraction
            if (body.contains("\"message\"")) {
                val start = body.indexOf("\"message\"") + 11
                val end = body.indexOf("\"", start)
                if (end > start) body.substring(start, end) else body.take(200)
            } else body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
    }

    private fun generateWorkflowYaml(buildType: String): String {
        val task = if (buildType == "release") "assembleRelease" else "assembleDebug"
        return """
name: Build APK

on:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Make gradlew executable
        run: chmod +x gradlew || true

      - name: Build APK
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew $task --stacktrace
          else
            echo "gradlew not found, trying gradle"
            gradle $task --stacktrace || true
          fi

      - name: Find APK
        run: find . -name "*.apk" -type f || true

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: APK
          path: |
            **/build/outputs/apk/**/*.apk
            **/*.apk
          if-no-files-found: warn
          retention-days: 7
""".trimIndent()
    }
}
