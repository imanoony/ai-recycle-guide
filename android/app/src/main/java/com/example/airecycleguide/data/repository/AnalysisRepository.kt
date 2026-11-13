package com.example.airecycleguide.data.repository

import android.content.Context
import com.example.airecycleguide.data.image.ImagePreparationException
import com.example.airecycleguide.data.image.PhotoUploadPreparer
import com.example.airecycleguide.data.model.AnalysisResponse
import com.example.airecycleguide.data.model.ApiErrorEnvelope
import com.example.airecycleguide.data.remote.AnalysisApi
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

sealed interface AnalysisCallResult {
    data class Success(val response: AnalysisResponse) : AnalysisCallResult
    data class Failure(val error: AnalysisError) : AnalysisCallResult
}

data class AnalysisError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

class AnalysisRepository(
    context: Context,
    private val baseUrl: String,
) {
    private val cacheDirectory = context.applicationContext.cacheDir
    private val photoUploadPreparer = PhotoUploadPreparer(cacheDirectory)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val api: AnalysisApi? by lazy {
        val normalizedUrl = baseUrl.trim()
        if (normalizedUrl.isEmpty()) {
            null
        } else {
            Retrofit.Builder()
                .baseUrl(if (normalizedUrl.endsWith('/')) normalizedUrl else "$normalizedUrl/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AnalysisApi::class.java)
        }
    }

    suspend fun analyze(photo: File, region: String? = null): AnalysisCallResult =
        withContext(Dispatchers.IO) {
            val analysisApi = try {
                api
            } catch (_: IllegalArgumentException) {
                return@withContext failure(
                    code = "invalid_server_url",
                    message = "서버 주소 설정을 확인해 주세요.",
                    retryable = false,
                )
            }
            if (analysisApi == null) {
                return@withContext failure(
                    code = "server_not_configured",
                    message = "분석 서버 주소가 설정되지 않았습니다.",
                    retryable = false,
                )
            }

            var uploadFile: File? = null
            try {
                uploadFile = photoUploadPreparer.prepare(photo)
                val imagePart = MultipartBody.Part.createFormData(
                    "image",
                    uploadFile.name,
                    uploadFile.asRequestBody("image/jpeg".toMediaType()),
                )
                val regionPart = region
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.toRequestBody("text/plain".toMediaType())

                val response = analysisApi.analyze(imagePart, regionPart)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        failure("empty_response", "서버 응답이 비어 있습니다.", false)
                    } else {
                        AnalysisCallResult.Success(body)
                    }
                } else {
                    parseServerFailure(response.code(), response.errorBody()?.string())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ImagePreparationException) {
                failure(error.code, error.message ?: "사진을 전송용으로 준비하지 못했습니다.", false)
            } catch (_: SocketTimeoutException) {
                failure("timeout", "서버 응답 시간이 초과되었습니다. 다시 시도해 주세요.", true)
            } catch (_: IOException) {
                failure("network_error", "서버에 연결할 수 없습니다. 연결을 확인하고 다시 시도해 주세요.", true)
            } catch (_: Exception) {
                failure("invalid_response", "서버 응답을 처리하지 못했습니다.", false)
            } finally {
                uploadFile?.delete()
            }
        }

    private fun parseServerFailure(httpStatus: Int, body: String?): AnalysisCallResult.Failure {
        val apiError = body?.let {
            runCatching { gson.fromJson(it, ApiErrorEnvelope::class.java)?.error }.getOrNull()
        }
        val retryableByStatus = httpStatus == 429 || httpStatus >= 500
        return failure(
            code = apiError?.code ?: "http_$httpStatus",
            message = apiError?.message?.takeIf(String::isNotBlank)
                ?: when (httpStatus) {
                    413 -> "사진 파일이 너무 큽니다."
                    415 -> "지원하지 않는 이미지 형식입니다."
                    503 -> "분석 서버를 일시적으로 사용할 수 없습니다."
                    else -> "사진 분석 요청을 처리하지 못했습니다."
                },
            retryable = apiError?.retryable ?: retryableByStatus,
        )
    }

    private fun failure(code: String, message: String, retryable: Boolean) =
        AnalysisCallResult.Failure(AnalysisError(code, message, retryable))
}
