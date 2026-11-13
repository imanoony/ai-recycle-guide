package com.example.airecycleguide.ui.state

import com.example.airecycleguide.data.model.AnalysisResponse
import java.io.File

sealed interface AppUiState {
    data object Camera : AppUiState

    data class Analyzing(
        val photo: File,
        val failure: AnalysisFailure? = null,
    ) : AppUiState

    data class Result(val response: AnalysisResponse) : AppUiState
}

data class AnalysisFailure(
    val message: String,
    val retryable: Boolean,
)

enum class CameraPermissionState {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
}
