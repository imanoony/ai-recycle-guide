package com.example.airecycleguide.data.remote

import com.example.airecycleguide.data.model.AnalysisResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AnalysisApi {
    @Multipart
    @POST("api/v1/analyze")
    suspend fun analyze(
        @Part image: MultipartBody.Part,
        @Part("region") region: RequestBody?,
    ): Response<AnalysisResponse>
}
