package com.example.airecycleguide.data.model

import com.google.gson.annotations.SerializedName

data class ApiErrorEnvelope(
    val error: ApiErrorDetail? = null,
)

data class ApiErrorDetail(
    val code: String = "internal_error",
    val message: String? = null,
    @field:SerializedName("request_id") val requestId: String? = null,
    val retryable: Boolean = false,
)
