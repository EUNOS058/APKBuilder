package com.example.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface BuildApiService {

    @GET("api/health")
    suspend fun getHealth(): Response<HealthResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadZip(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    @POST("api/build")
    suspend fun startBuild(
        @Body request: BuildRequest
    ): Response<BuildResponse>

    @GET("api/build-status/{buildId}")
    suspend fun getBuildStatus(
        @Path("buildId") buildId: String
    ): Response<BuildStatusResponse>

    @GET("api/build-logs/{buildId}")
    suspend fun getBuildLogs(
        @Path("buildId") buildId: String
    ): Response<BuildLogsResponse>
}
