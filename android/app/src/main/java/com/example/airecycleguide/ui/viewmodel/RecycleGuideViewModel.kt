package com.example.airecycleguide.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.airecycleguide.data.model.AnalysisResponse
import com.example.airecycleguide.ui.state.AnalysisFailure
import com.example.airecycleguide.ui.state.AppUiState
import com.example.airecycleguide.ui.state.CameraPermissionState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecycleGuideViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Camera)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _cameraPermission = MutableStateFlow(CameraPermissionState.NOT_REQUESTED)
    val cameraPermission: StateFlow<CameraPermissionState> = _cameraPermission.asStateFlow()

    private var analysisJob: Job? = null

    fun updateCameraPermission(state: CameraPermissionState) {
        _cameraPermission.value = state
    }

    fun beginAnalysis(photo: File) {
        if (_uiState.value !is AppUiState.Camera || !photo.isFile) {
            photo.delete()
            return
        }
        cancelAnalysisJob()
        _uiState.value = AppUiState.Analyzing(photo)
    }

    fun attachAnalysisJob(job: Job) {
        cancelAnalysisJob()
        analysisJob = job
    }

    fun reportAnalysisFailure(message: String, retryable: Boolean) {
        val current = _uiState.value as? AppUiState.Analyzing ?: return
        _uiState.value = current.copy(failure = AnalysisFailure(message, retryable))
        analysisJob = null
    }

    /** Clears the visible failure and returns the retained photo for a caller to retry. */
    fun prepareRetry(): File? {
        val current = _uiState.value as? AppUiState.Analyzing ?: return null
        if (current.failure?.retryable != true || !current.photo.isFile) return null
        _uiState.value = current.copy(failure = null)
        return current.photo
    }

    fun showResult(response: AnalysisResponse) {
        val current = _uiState.value as? AppUiState.Analyzing ?: return
        cancelAnalysisJob()
        current.photo.delete()
        _uiState.value = AppUiState.Result(response)
    }

    /** Returns true when the back action was consumed by an in-app state transition. */
    fun handleBack(): Boolean {
        if (_uiState.value is AppUiState.Camera) return false
        returnToCamera()
        return true
    }

    fun returnToCamera() {
        cancelAnalysisJob()
        (_uiState.value as? AppUiState.Analyzing)?.photo?.delete()
        _uiState.value = AppUiState.Camera
    }

    private fun cancelAnalysisJob() {
        analysisJob?.cancel()
        analysisJob = null
    }

    override fun onCleared() {
        cancelAnalysisJob()
        (_uiState.value as? AppUiState.Analyzing)?.photo?.delete()
        super.onCleared()
    }
}
