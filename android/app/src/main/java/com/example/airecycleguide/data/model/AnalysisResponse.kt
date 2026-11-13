package com.example.airecycleguide.data.model

import com.google.gson.annotations.SerializedName

/** DTOs matching the v1 response in server/docs/api-contract.md. */
data class AnalysisResponse(
    @field:SerializedName("request_id") val requestId: String = "",
    val status: String = "unidentified",
    val region: String? = null,
    val items: List<WasteItemResult> = emptyList(),
    val message: String? = null,
)

data class WasteItemResult(
    val name: String = "",
    val material: String? = null,
    val confidence: Double? = null,
    @field:SerializedName("sorting_status") val sortingStatus: String = "unknown",
    val steps: List<SortingStep> = emptyList(),
    val cautions: List<String> = emptyList(),
    val sources: List<GuidanceSource> = emptyList(),
)

data class SortingStep(
    val order: Int = 1,
    val title: String = "",
    val instruction: String = "",
)

data class GuidanceSource(
    val title: String = "",
    val url: String = "",
    val region: String? = null,
    @field:SerializedName("checked_at") val checkedAt: String? = null,
)
