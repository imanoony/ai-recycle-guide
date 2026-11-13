package com.example.airecycleguide.ui.state

import com.example.airecycleguide.data.model.AnalysisResponse
import java.io.File

sealed interface AppUiState {
    data object Camera : AppUiState

    data class Analyzing(
        val photo: File,
    ) : AppUiState

    data class Result(
        val outcome: AnalysisOutcome,
    ) : AppUiState
}

sealed interface AnalysisOutcome {
    data class Success(val response: AnalysisResponse) : AnalysisOutcome
    data class Error(val failure: AnalysisFailure, val retryPhoto: File) : AnalysisOutcome
}

data class AnalysisFailure(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

enum class CameraPermissionState {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
}
